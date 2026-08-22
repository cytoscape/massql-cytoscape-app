package org.cytoscape.massql.app.command;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.MassqlException;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.massql.app.run.MassqlRunner;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.json.JSONResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The scripted path, against a real network.
 *
 * <p>Its whole point is that it is not a second implementation: the command must reach exactly the
 * table state the dialog would, because both hand the same request to the same runner.
 */
class MassqlCommandTaskIT {

    private static final String QUERY =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5";

    /** micro.mgf numbers its spectra by position; the query matches the second. */
    private static final int MATCHING_SCAN = 2;

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private CyTable nodeTable;
    private CyServiceRegistrar registrar;

    @BeforeEach
    void setUp() {
        network = support.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        nodeTable.createColumn("scan", Integer.class, false);

        registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyEventHelper.class)).thenReturn(new DummyCyEventHelper());
    }

    private CyNode nodeWithScan(int scan) {
        CyNode node = network.addNode();
        network.getRow(node).set("scan", scan);
        return node;
    }

    private MassqlCommandTask command() {
        MassqlCommandTask task = new MassqlCommandTask(registrar);
        task.file = TestFixtures.require("fixtures/micro/micro.mgf").toFile();
        task.query = QUERY;
        task.name = "q";
        task.scanColumn = "scan";
        task.network = network;
        return task;
    }

    private static TaskMonitor monitor() {
        return mock(TaskMonitor.class);
    }

    @Test
    void writesTheSameColumnsAsTheDialogPath() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);
        CyNode missed = nodeWithScan(999);

        MassqlCommandTask task = command();
        task.deriveColumns = "base_peak_i";
        task.run(monitor());

        String viaCommand = network.getRow(matched).get("MASSQL::q", String.class);
        Double derivedViaCommand =
                network.getRow(matched).get("MASSQL::q_base_peak_i", Double.class);
        assertNotNull(viaCommand);
        assertNotNull(derivedViaCommand);
        assertEquals("", network.getRow(missed).get("MASSQL::q", String.class));

        // Same request through the runner the dialog uses, under a different query name.
        new MassqlRunner(registrar)
                .run(
                        new MassqlRunRequest(
                                TestFixtures.require("fixtures/micro/micro.mgf"),
                                QUERY,
                                "viaDialog",
                                "scan",
                                true,
                                List.of(ResultAttribute.BASE_PEAK_I),
                                20.0,
                                network),
                        null,
                        () -> false);

        assertEquals(viaCommand, network.getRow(matched).get("MASSQL::viaDialog", String.class));
        assertEquals(
                derivedViaCommand,
                network.getRow(matched).get("MASSQL::viaDialog_base_peak_i", Double.class));
    }

    @Test
    void reportsTheOutcomeAsJson() {
        nodeWithScan(MATCHING_SCAN);
        nodeWithScan(999);

        MassqlCommandTask task = command();
        task.deriveColumns = "tic";
        task.run(monitor());

        JsonObject result = JsonParser.parseString(task.getResults(String.class)).getAsJsonObject();

        assertEquals(1, result.get("matchedNodes").getAsInt());
        assertEquals(1, result.get("unmatchedNodes").getAsInt());
        assertEquals(1, result.get("resultRows").getAsInt());
        assertEquals(0, result.get("duplicateScans").getAsInt());
        assertEquals("MASSQL::q", result.get("resultColumn").getAsString());
        assertEquals("MASSQL::q_tic", result.getAsJsonArray("derivedColumns").get(0).getAsString());
        assertTrue(!result.get("cancelled").getAsBoolean());
    }

    @Test
    void alsoAnswersAsAJsonResult() {
        nodeWithScan(MATCHING_SCAN);
        MassqlCommandTask task = command();
        task.run(monitor());

        JSONResult json = task.getResults(JSONResult.class);

        assertNotNull(json);
        assertEquals(task.getResults(String.class), json.getJSON());
    }

    @Test
    void hasNoResultBeforeItRuns() {
        assertNull(command().getResults(String.class));
    }

    @Test
    void defaultsToTheCurrentNetworkWhenNoneIsNamed() {
        CyNode matched = nodeWithScan(MATCHING_SCAN);
        CyApplicationManager applications = mock(CyApplicationManager.class);
        when(applications.getCurrentNetwork()).thenReturn(network);
        when(registrar.getService(CyApplicationManager.class)).thenReturn(applications);

        MassqlCommandTask task = command();
        task.network = null;
        task.run(monitor());

        assertNotNull(network.getRow(matched).get("MASSQL::q", String.class));
    }

    @Test
    void saysSoWhenThereIsNoNetworkToWriteTo() {
        CyApplicationManager applications = mock(CyApplicationManager.class);
        when(applications.getCurrentNetwork()).thenReturn(null);
        when(registrar.getService(CyApplicationManager.class)).thenReturn(applications);

        MassqlCommandTask task = command();
        task.network = null;

        MassqlException e = assertThrows(MassqlException.class, () -> task.run(monitor()));
        assertTrue(e.getMessage().contains("network="), e.getMessage());
    }

    @Test
    void aRunWritingNothingIsRefused() {
        nodeWithScan(MATCHING_SCAN);
        MassqlCommandTask task = command();
        task.resultColumn = false;
        task.deriveColumns = "";

        MassqlException e = assertThrows(MassqlException.class, () -> task.run(monitor()));
        assertTrue(e.getMessage().contains("writes nothing"), e.getMessage());
        assertNull(nodeTable.getColumn("MASSQL::q"));
    }
}
