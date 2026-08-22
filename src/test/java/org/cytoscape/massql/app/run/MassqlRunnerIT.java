package org.cytoscape.massql.app.run;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the runner against a real {@link CyNetwork} and a real peak list -- no OSGi container, no
 * desktop.
 *
 * <p><b>The fixture's scans are numbered 1, 2, 3 -- not 1, 3, 5 as its {@code TITLE} lines
 * suggest.</b> {@code micro.mgf} carries no {@code SCANS=} headers, so the reader falls back to
 * each spectrum's 1-based position in the file and the titles are just text. The query below
 * matches the second spectrum, whose title reads {@code micro.scan3} but whose scan number is 2.
 * This is the join hazard the app cannot detect for a user, so it is worth seeing it here.
 */
class MassqlRunnerIT {

    private static final String MATCHING_QUERY =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5";

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

    /** The scan number of the only spectrum {@link #MATCHING_QUERY} matches. */
    private static final int MATCHING_SCAN = 2;

    private CyNode nodeWithScan(Integer scan) {
        CyNode node = network.addNode();
        network.getRow(node).set("scan", scan);
        return node;
    }

    private MassqlRunRequest request(boolean resultColumn, List<ResultAttribute> attrs) {
        Path mgf = TestFixtures.require("fixtures/micro/micro.mgf");
        return new MassqlRunRequest(
                mgf, MATCHING_QUERY, "q", "scan", resultColumn, attrs, 20.0, network);
    }

    @Test
    void writesJsonForAMatchAndAnEmptyStringForEverythingElse() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);
        CyNode missed = nodeWithScan(999);

        MassqlRunSummary summary = runner.run(request(true, List.of()), null, () -> false);

        assertEquals(1, summary.matchedNodes());
        assertEquals(1, summary.unmatchedNodes());
        assertEquals("MASSQL::q", summary.resultColumn());

        String json = network.getRow(matched).get("MASSQL::q", String.class);
        assertNotNull(json);
        assertTrue(json.contains("\"scan\":" + MATCHING_SCAN), json);

        // Not null: a null cell makes the equation engine give up before MASSQL_PARSE runs and
        // paint the cell as an error, which is not what "this node had no result" should look like.
        assertEquals("", network.getRow(missed).get("MASSQL::q", String.class));
    }

    @Test
    void derivedColumnHoldsAValueForAMatchAndNothingForAMiss() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);
        CyNode missed = nodeWithScan(999);

        MassqlRunSummary summary =
                runner.run(request(false, List.of(ResultAttribute.BASE_PEAK_I)), null, () -> false);

        assertEquals(List.of("MASSQL::q_base_peak_i"), summary.derivedColumns());
        assertNull(summary.resultColumn(), "the JSON column was not requested");
        assertNull(nodeTable.getColumn("MASSQL::q"), "and must not have been created");

        assertNotNull(network.getRow(matched).get("MASSQL::q_base_peak_i", Double.class));
        assertNull(network.getRow(missed).get("MASSQL::q_base_peak_i", Double.class));
    }

    @Test
    void derivedValuesDoNotDependOnTheJsonColumn() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);

        runner.run(request(false, List.of(ResultAttribute.TIC)), null, () -> false);
        Double withoutJson = network.getRow(matched).get("MASSQL::q_tic", Double.class);

        runner.run(request(true, List.of(ResultAttribute.TIC)), null, () -> false);
        Double withJson = network.getRow(matched).get("MASSQL::q_tic", Double.class);

        assertEquals(withoutJson, withJson);
        assertNotNull(withJson);
    }

    @Test
    void derivedCellsHoldValuesRatherThanEquations() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);

        runner.run(request(true, List.of(ResultAttribute.TIC)), null, () -> false);

        // A stored formula would be re-evaluated on every table repaint, style pass and filter.
        assertTrue(
                network.getRow(matched).getRaw("MASSQL::q_tic") instanceof Double,
                "the cell must hold a number, not an Equation");
        assertEquals(
                "",
                nodeTable.getLastInternalError() == null ? "" : nodeTable.getLastInternalError());
    }

    @Test
    void aStringScanColumnMatchesJustAsWell() {
        nodeTable.deleteColumn("scan");
        nodeTable.createColumn("scan", String.class, false);
        CyNode matched = network.addNode();
        network.getRow(matched).set("scan", " " + MATCHING_SCAN + " ");
        CyNode nonsense = network.addNode();
        network.getRow(nonsense).set("scan", "not a number");

        MassqlRunSummary summary = runner.run(request(true, List.of()), null, () -> false);

        assertEquals(1, summary.matchedNodes());
        assertEquals(1, summary.unmatchedNodes());
    }

    @Test
    void rerunningTheSameQueryRefreshesEveryCell() {
        CyNode node = nodeWithScan(MATCHING_SCAN);

        runner.run(request(true, List.of()), null, () -> false);
        Object columnBefore = nodeTable.getColumn("MASSQL::q");

        // A query matching nothing must clear the previous run's value, not leave it behind.
        MassqlRunRequest empty =
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        "QUERY scaninfo(MS2DATA) WHERE MS2PROD=1234.5:TOLERANCEMZ=0.001",
                        "q",
                        "scan",
                        true,
                        List.of(),
                        20.0,
                        network);
        runner.run(empty, null, () -> false);

        assertEquals("", network.getRow(node).get("MASSQL::q", String.class));
        assertTrue(columnBefore == nodeTable.getColumn("MASSQL::q"), "the column was recreated");
    }

    @Test
    void cancellingLeavesTheTableUntouched() {
        nodeWithScan(MATCHING_SCAN);

        MassqlRunSummary summary = runner.run(request(true, List.of()), null, () -> true);

        assertTrue(summary.cancelled());
        assertNull(nodeTable.getColumn("MASSQL::q"), "a cancelled run must write nothing");
    }
}
