package org.cytoscape.massql.app.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.MassqlParseException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two failure modes a run has to survive without corrupting the table: a peak list that files two
 * spectra under one scan number, and a query that does not parse.
 */
class DuplicateScansIT {

    /**
     * Declares SCANS=5 twice, then SCANS=9. Equal ids are accepted by the engine; only a descending
     * sequence is rejected.
     */
    private static final String DUPLICATES = "fixtures/scans/duplicate_scans.mgf";

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

    private MassqlRunSummary run(String fixture, String query) {
        return runner.run(
                new MassqlRunRequest(
                        TestFixtures.require(fixture),
                        query,
                        "q",
                        "scan",
                        true,
                        List.of(ResultAttribute.TIC),
                        20.0,
                        network),
                null,
                () -> false);
    }

    @Test
    void aScanClaimedTwiceIsReportedRatherThanHidden() {
        nodeWithScan(5);
        nodeWithScan(9);

        MassqlRunSummary summary = run(DUPLICATES, "QUERY scaninfo(MS2DATA)");

        assertEquals(3, summary.resultRows(), "the engine returns both rows for scan 5");
        assertEquals(1, summary.duplicateScans(), "and the run says one was discarded");
        assertEquals(2, summary.matchedNodes());
    }

    /**
     * A node can hold one result, so one of the two has to win. The later row does, and the count
     * above is what tells the user a choice was made on their behalf.
     */
    @Test
    void theLastRowForAScanIsTheOneWritten() {
        CyNode node = nodeWithScan(5);

        run(DUPLICATES, "QUERY scaninfo(MS2DATA)");

        String json = network.getRow(node).get("MASSQL::q", String.class);
        assertNotNull(json);
        assertTrue(json.contains("\"precmz\":500.0"), "expected the second spectrum, got: " + json);
        assertEquals(1750.0, network.getRow(node).get("MASSQL::q_tic", Double.class));
    }

    @Test
    void aRunWithNoDuplicatesReportsNone() {
        nodeWithScan(2);

        MassqlRunSummary summary = run("fixtures/micro/micro.mgf", "QUERY scaninfo(MS2DATA)");

        assertEquals(0, summary.duplicateScans());
    }

    /** A query that does not parse must fail before the peak list is opened or a column made. */
    @Test
    void aMalformedQueryLeavesTheTableUntouched() {
        nodeWithScan(5);

        assertThrows(
                MassqlParseException.class,
                () -> run(DUPLICATES, "QUERY scansum(MS2DATA) WHERE MS2PROD=300.0"));

        assertNull(nodeTable.getColumn("MASSQL::q"));
        assertNull(nodeTable.getColumn("MASSQL::q_tic"));
    }

    @Test
    void aParseFailureSaysWhatItObjectedTo() {
        nodeWithScan(5);

        MassqlParseException e =
                assertThrows(
                        MassqlParseException.class,
                        () -> run(DUPLICATES, "QUERY scansum(MS2DATA) WHERE MS2PROD=300.0"));

        assertEquals("scansum", e.construct(), "the offending construct is named");
    }
}
