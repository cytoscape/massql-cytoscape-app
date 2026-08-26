package org.cytoscape.massql.app.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The numeric columns a run derives from its results.
 *
 * <p>{@code micro.mgf} holds three spectra, numbered 1, 2 and 3 by position. The first declares no
 * {@code RTINSECONDS}, so its retention time is a genuine {@code 0.0} -- which is what makes it the
 * right fixture for proving that a measured zero is not confused with a missing value.
 */
class DerivedColumnsIT {

    private static final String MATCHES_EVERY_SCAN = "QUERY scaninfo(MS2DATA)";

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private CyTable nodeTable;
    private MassqlRunner runner;

    @BeforeEach
    void setUp() {
        network = support.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        nodeTable.createColumn("scan", Integer.class, false);

        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyEventHelper.class)).thenReturn(new DummyCyEventHelper());
        runner = new MassqlRunner(registrar);
    }

    private CyNode nodeWithScan(int scan) {
        CyNode node = network.addNode();
        network.getRow(node).set("scan", scan);
        return node;
    }

    private MassqlRunSummary derive(List<ResultAttribute> attributes) {
        return derive(attributes, false);
    }

    private MassqlRunSummary derive(List<ResultAttribute> attributes, boolean withJson) {
        return runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        MATCHES_EVERY_SCAN,
                        "q",
                        "scan",
                        withJson,
                        attributes,
                        20.0,
                        network),
                null,
                () -> false);
    }

    private Double valueOf(CyNode node, String column) {
        return network.getRow(node).get(column, Double.class);
    }

    @Test
    void writesOneColumnPerCheckedAttribute() {
        CyNode node = nodeWithScan(2);

        MassqlRunSummary summary =
                derive(
                        List.of(
                                ResultAttribute.TIC,
                                ResultAttribute.BASE_PEAK_I,
                                ResultAttribute.BASE_PEAK_MZ));

        assertEquals(
                List.of("MASSQL::q_tic", "MASSQL::q_base_peak_i", "MASSQL::q_base_peak_mz"),
                summary.derivedColumns(),
                "columns are written in the order they were requested");

        for (String column : summary.derivedColumns()) {
            assertEquals(Double.class, nodeTable.getColumn(column).getType());
            assertNotNull(valueOf(node, column), column + " has no value");
        }
        assertEquals(2600.0, valueOf(node, "MASSQL::q_tic"));
        assertEquals(1500.0, valueOf(node, "MASSQL::q_base_peak_i"));
    }

    /**
     * The distinction the whole design turns on. Scan 1 declares no retention time, so massql
     * reports 0.0 -- a real measurement. Scan 999 matched nothing. Both would look identical if
     * absence were written as zero.
     */
    @Test
    void aMeasuredZeroIsWrittenAsZeroAndAbsenceIsLeftBlank() {
        CyNode zeroRt = nodeWithScan(1);
        CyNode later = nodeWithScan(2);
        CyNode unmatched = nodeWithScan(999);

        derive(List.of(ResultAttribute.RT));

        assertEquals(0.0, valueOf(zeroRt, "MASSQL::q_rt"), "0.0 is a retention time, not a gap");
        assertEquals(1.0, valueOf(later, "MASSQL::q_rt"));
        assertNull(valueOf(unmatched, "MASSQL::q_rt"));
    }

    /** An MGF carries no MS1 survey scans, so the precursor fields are absent for every node. */
    @Test
    void anAttributeTheFormatCannotSupplyIsBlankEverywhere() {
        CyNode matched = nodeWithScan(2);

        derive(List.of(ResultAttribute.MS1_I));

        assertNull(valueOf(matched, "MASSQL::q_ms1_i"));
        assertTrue(isBlank(nodeTable.getLastInternalError()));
    }

    @Test
    void reusesTheSameColumnOnARerun() {
        CyNode node = nodeWithScan(2);

        derive(List.of(ResultAttribute.TIC));
        CyColumn first = nodeTable.getColumn("MASSQL::q_tic");

        derive(List.of(ResultAttribute.TIC));

        assertSame(
                first,
                nodeTable.getColumn("MASSQL::q_tic"),
                "recreating the column would drop any style mapping bound to it");
        assertEquals(2600.0, valueOf(node, "MASSQL::q_tic"));
    }

    @Test
    void aRerunClearsAValueThatNoLongerMatches() {
        CyNode node = nodeWithScan(2);
        derive(List.of(ResultAttribute.TIC));
        assertNotNull(valueOf(node, "MASSQL::q_tic"));

        runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        "QUERY scaninfo(MS2DATA) WHERE MS2PROD=1234.5:TOLERANCEMZ=0.001",
                        "q",
                        "scan",
                        false,
                        List.of(ResultAttribute.TIC),
                        20.0,
                        network),
                null,
                () -> false);

        assertNull(valueOf(node, "MASSQL::q_tic"), "a stale value is worse than an empty cell");
    }

    /**
     * The case a whole-column check would miss: the second query still matches something, just not
     * this node. Its cell has to be emptied rather than left holding the first query's answer under
     * a column named for the second.
     */
    @Test
    void aRowThatMatchedTheFirstQueryIsClearedWhenTheSecondMissesIt() {
        CyNode dropsOut = nodeWithScan(1);
        CyNode staysMatched = nodeWithScan(2);

        // Matches all three spectra, so both nodes get a value.
        derive(List.of(ResultAttribute.TIC), true);
        assertEquals(1750.0, valueOf(dropsOut, "MASSQL::q_tic"));
        assertEquals(2600.0, valueOf(staysMatched, "MASSQL::q_tic"));

        // Same query name, but now only scan 2 matches.
        runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5",
                        "q",
                        "scan",
                        true,
                        List.of(ResultAttribute.TIC),
                        20.0,
                        network),
                null,
                () -> false);

        assertNull(valueOf(dropsOut, "MASSQL::q_tic"), "scan 1 no longer matches");
        assertEquals("", network.getRow(dropsOut).get("MASSQL::q", String.class));

        assertEquals(2600.0, valueOf(staysMatched, "MASSQL::q_tic"), "scan 2 still does");
        assertNotNull(network.getRow(staysMatched).get("MASSQL::q", String.class));
    }

    /**
     * A re-run under the same query name leaves nothing describing the previous query. A column
     * named for this query holding the last one's numbers is worse than no column: it reads as
     * current.
     */
    @Test
    void anAttributeDroppedOnARerunHasItsColumnRemoved() {
        nodeWithScan(2);

        derive(List.of(ResultAttribute.TIC, ResultAttribute.BASE_PEAK_I));
        assertNotNull(nodeTable.getColumn("MASSQL::q_base_peak_i"));

        derive(List.of(ResultAttribute.TIC));

        assertNull(nodeTable.getColumn("MASSQL::q_base_peak_i"), "the dropped column must go");
        assertNotNull(nodeTable.getColumn("MASSQL::q_tic"), "the kept one stays");
    }

    @Test
    void turningOffTheJsonColumnOnARerunRemovesIt() {
        nodeWithScan(2);

        derive(List.of(ResultAttribute.TIC), true);
        assertNotNull(nodeTable.getColumn("MASSQL::q"));

        derive(List.of(ResultAttribute.TIC), false);

        assertNull(nodeTable.getColumn("MASSQL::q"));
    }

    /**
     * The columns a run does write are reused rather than replaced, so a visual mapping bound to
     * one survives the re-run. They carry nothing stale because every row is rewritten.
     */
    @Test
    void aColumnThisRunStillWritesKeepsItsIdentity() {
        nodeWithScan(2);

        derive(List.of(ResultAttribute.TIC, ResultAttribute.BASE_PEAK_I));
        CyColumn tic = nodeTable.getColumn("MASSQL::q_tic");

        derive(List.of(ResultAttribute.TIC));

        assertSame(tic, nodeTable.getColumn("MASSQL::q_tic"));
    }

    /** Ownership is matched exactly, so a query whose name merely starts the same is untouched. */
    @Test
    void aRerunLeavesAnotherQuerysColumnsAlone() {
        nodeWithScan(2);

        runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        MATCHES_EVERY_SCAN,
                        "qq",
                        "scan",
                        true,
                        List.of(ResultAttribute.BASE_PEAK_I),
                        20.0,
                        network),
                null,
                () -> false);
        derive(List.of(ResultAttribute.BASE_PEAK_I));

        derive(List.of(ResultAttribute.TIC));

        assertNull(nodeTable.getColumn("MASSQL::q_base_peak_i"), "this query's column went");
        assertNotNull(nodeTable.getColumn("MASSQL::qq"), "the other query's did not");
        assertNotNull(nodeTable.getColumn("MASSQL::qq_base_peak_i"));
    }

    @Test
    void replacesAColumnLeftAtTheWrongType() {
        CyNode node = nodeWithScan(2);
        nodeTable.createColumn("MASSQL::q_tic", String.class, false);

        derive(List.of(ResultAttribute.TIC));

        assertEquals(Double.class, nodeTable.getColumn("MASSQL::q_tic").getType());
        assertEquals(2600.0, valueOf(node, "MASSQL::q_tic"));
    }

    @Test
    void refusesToReplaceAnImmutableColumn() {
        nodeWithScan(2);
        nodeTable.createColumn("MASSQL::q_tic", String.class, true);

        MassqlException e =
                assertThrows(MassqlException.class, () -> derive(List.of(ResultAttribute.TIC)));

        assertTrue(e.getMessage().contains("immutable"), e.getMessage());
        assertTrue(e.getMessage().contains("query name"), "the message should say what to do");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
