package org.cytoscape.massql.app.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code MASSQL::QUERIES}, the column naming the queries each node currently matches.
 *
 * <p>{@code micro.mgf} holds three spectra numbered 1, 2 and 3 by position. One query below matches
 * all of them and another matches only scan 2, which is what lets a node gain a name and later lose
 * it.
 */
class QueriesMarkerIT {

    private static final String MATCHES_EVERY_SCAN = "QUERY scaninfo(MS2DATA)";
    private static final String MATCHES_SCAN_2 =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5";
    private static final String MATCHES_NOTHING =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=1234.5:TOLERANCEMZ=0.001";

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

    private void run(String queryName, String query) {
        run(queryName, query, () -> false);
    }

    private void run(String queryName, String query, java.util.function.BooleanSupplier cancelled) {
        runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        query,
                        queryName,
                        "scan",
                        true,
                        List.of(),
                        20.0,
                        network),
                null,
                cancelled);
    }

    /** An unwritten cell reads back null, which means the same as holding no names. */
    private List<String> queriesOn(CyNode node) {
        List<String> names = network.getRow(node).getList(NodeColumns.QUERIES_COLUMN, String.class);
        return names == null ? List.of() : names;
    }

    @Test
    void aMatchedNodeIsMarkedAndAnUnmatchedOneIsNot() {
        CyNode matched = nodeWithScan(2);
        CyNode missed = nodeWithScan(999);

        run("bile_acid", MATCHES_SCAN_2);

        assertEquals(List.of("bile_acid"), queriesOn(matched));
        assertEquals(List.of(), queriesOn(missed));
    }

    /** The reason the column exists: a marker, not a tally. */
    @Test
    void runningTheSameQueryTwiceLeavesOneEntry() {
        CyNode matched = nodeWithScan(2);

        run("bile_acid", MATCHES_SCAN_2);
        run("bile_acid", MATCHES_SCAN_2);
        run("bile_acid", MATCHES_SCAN_2);

        assertEquals(List.of("bile_acid"), queriesOn(matched));
    }

    @Test
    void asecondQueryAppendsInTheOrderTheyWereRun() {
        CyNode matched = nodeWithScan(2);

        run("first", MATCHES_EVERY_SCAN);
        run("second", MATCHES_SCAN_2);

        assertEquals(List.of("first", "second"), queriesOn(matched));
    }

    /**
     * A node that matched a query and no longer does must lose the name, or the column claims a
     * match that the current query does not make.
     */
    @Test
    void aNarrowedRerunTakesTheNameOffNodesItNoLongerMatches() {
        CyNode dropsOut = nodeWithScan(1);
        CyNode staysMatched = nodeWithScan(2);

        run("other", MATCHES_EVERY_SCAN);
        run("bile_acid", MATCHES_EVERY_SCAN);
        assertEquals(List.of("other", "bile_acid"), queriesOn(dropsOut));

        run("bile_acid", MATCHES_SCAN_2);

        assertEquals(List.of("other"), queriesOn(dropsOut), "only this query's name comes off");
        assertEquals(List.of("other", "bile_acid"), queriesOn(staysMatched));
    }

    @Test
    void theColumnIsCreatedEvenWhenNothingMatches() {
        nodeWithScan(2);

        run("bile_acid", MATCHES_NOTHING);

        assertNotNull(nodeTable.getColumn(NodeColumns.QUERIES_COLUMN));
        assertEquals(List.class, nodeTable.getColumn(NodeColumns.QUERIES_COLUMN).getType());
    }

    @Test
    void aCancelledRunLeavesNoColumn() {
        nodeWithScan(2);

        run("bile_acid", MATCHES_SCAN_2, () -> true);

        assertNull(nodeTable.getColumn(NodeColumns.QUERIES_COLUMN));
    }

    /** The marker outlives the columns of a query whose attributes were unticked. */
    @Test
    void theMarkerSurvivesTheRerunCleanupOfOtherColumns() {
        CyNode matched = nodeWithScan(2);

        runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/micro/micro.mgf"),
                        MATCHES_SCAN_2,
                        "bile_acid",
                        "scan",
                        true,
                        List.of(ResultAttribute.TIC),
                        20.0,
                        network),
                null,
                () -> false);
        assertNotNull(nodeTable.getColumn("MASSQL::bile_acid_tic"));

        run("bile_acid", MATCHES_SCAN_2);

        assertNull(nodeTable.getColumn("MASSQL::bile_acid_tic"), "the unticked column went");
        assertEquals(List.of("bile_acid"), queriesOn(matched), "the marker did not");
    }
}
