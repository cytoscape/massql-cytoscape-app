package org.cytoscape.massql.app.run;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.event.DummyCyEventHelper;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guard on the assumption the whole app rests on: that the scan numbers in a peak list and the
 * scan numbers in the network built from it refer to the same spectra.
 *
 * <p>Nothing the app does at run time can check this. A peak list numbered on a different basis
 * from its network produces a run that completes, writes a column, and matches nothing — which
 * looks exactly like a selective query. So it is pinned here, against a real matched pair from
 * cytoscape/cytoscape#26 rather than anything this repo generated. See {@code PROVENANCE.md} beside
 * the fixtures.
 *
 * <p>The node table deliberately comes from the GNPS network, not from the peak list. Building it
 * from the {@code .mgf} would make the join tautological and prove nothing.
 */
class GnpsScanJoinIT {

    private static final String TRIHYDROXY_BILE_ACID =
            "QUERY scaninfo(MS2DATA)"
                    + " WHERE MS2PROD=337.25:TOLERANCEMZ=0.01:INTENSITYPERCENT=5"
                    + " AND MS2PROD=319.24:TOLERANCEMZ=0.01:INTENSITYPERCENT=5";

    /** In this network the scan number is the node's name. There is no column called "scan". */
    private static final String SCAN_COLUMN = "name";

    private static final int NODES = 600;
    private static final int NODES_IN_THE_SLICE = 400;

    /** Precomputed from the fixture pair; a range would pass under partial misalignment. */
    private static final List<Integer> EXPECTED_MATCHES =
            List.of(
                    18842, 20718, 20799, 21310, 23108, 25213, 25250, 25533, 25787, 26037, 26277,
                    26457, 26672, 27265, 27658, 27919, 28122, 28218, 28750, 28765, 29047, 29685,
                    30044, 30545, 30565, 32165, 32660, 32730, 33139, 33401, 33413);

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private CyTable nodeTable;
    private MassqlRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        network = support.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        // "name" already exists on every node table, as a String -- which is the point: the join
        // column here is one Cytoscape created, not one the researcher added.
        nodeTable.createColumn("mz", Double.class, false);

        loadGnpsNodeTable();

        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyEventHelper.class)).thenReturn(new DummyCyEventHelper());
        runner = new MassqlRunner(registrar);
    }

    /** Builds the network from the committed GNPS node table, one node per row. */
    private void loadGnpsNodeTable() throws IOException {
        List<String> lines =
                Files.readAllLines(TestFixtures.require("fixtures/gnps/gnps_node_table.csv"));
        assertEquals("name,mz", lines.get(0), "fixture header changed");

        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            CyRow row = network.getRow(network.addNode());
            row.set(SCAN_COLUMN, cells[0]);
            if (!cells[1].isEmpty()) {
                row.set("mz", Double.valueOf(cells[1]));
            }
        }
    }

    private MassqlRunSummary run() {
        return runner.run(
                new MassqlRunRequest(
                        TestFixtures.require("fixtures/gnps/plusrize_slice.mgf"),
                        TRIHYDROXY_BILE_ACID,
                        "bile_acid",
                        SCAN_COLUMN,
                        true,
                        List.of(ResultAttribute.BASE_PEAK_I),
                        20.0,
                        network),
                null,
                () -> false);
    }

    private List<Integer> scansWithAResult() {
        List<Integer> matched = new ArrayList<>();
        for (CyNode node : network.getNodeList()) {
            CyRow row = network.getRow(node);
            String json = row.get("MASSQL::bile_acid", String.class);
            if (json != null && !json.isEmpty()) {
                matched.add(Integer.valueOf(row.get(SCAN_COLUMN, String.class)));
            }
        }
        return matched.stream().sorted().toList();
    }

    /**
     * Guards the fixtures themselves. Were the peak list ever regenerated in scan order, the join
     * assertions below would still pass while testing a case that no longer occurs in the wild.
     */
    @Test
    void thePeakListIsStillDeclaredOutOfScanOrder() throws IOException {
        List<Integer> declared = new ArrayList<>();
        Matcher m =
                Pattern.compile("^SCANS=(\\d+)", Pattern.MULTILINE)
                        .matcher(
                                Files.readString(
                                        TestFixtures.require("fixtures/gnps/plusrize_slice.mgf")));
        while (m.find()) {
            declared.add(Integer.valueOf(m.group(1)));
        }

        assertEquals(NODES_IN_THE_SLICE, declared.size());
        assertFalse(declared.equals(declared.stream().sorted().toList()), "the slice got sorted");
    }

    @Test
    void aRealGnpsNetworkAndItsPeakListJoinExactly() {
        MassqlRunSummary summary = run();

        assertEquals(EXPECTED_MATCHES.size(), summary.resultRows(), "scans the query matched");
        assertEquals(EXPECTED_MATCHES.size(), summary.matchedNodes());
        assertEquals(NODES - EXPECTED_MATCHES.size(), summary.unmatchedNodes());
        assertEquals(0, summary.duplicateScans());
    }

    /** The counts could be right while the wrong nodes carried the results. */
    @Test
    void theResultsLandOnTheNodesThatProducedThem() {
        run();

        assertEquals(EXPECTED_MATCHES, scansWithAResult());
    }

    @Test
    void aMatchedNodeCarriesItsOwnScanInTheResult() {
        run();

        for (CyNode node : network.getNodeList()) {
            String json = network.getRow(node).get("MASSQL::bile_acid", String.class);
            if (json == null || json.isEmpty()) {
                continue;
            }
            String scan = network.getRow(node).get(SCAN_COLUMN, String.class);
            assertTrue(
                    json.contains("\"scan\":" + scan),
                    "node " + scan + " carries a result for another scan: " + json);
        }
    }

    @Test
    void nodesOutsideThePeakListAreLeftEmptyRatherThanWrong() {
        run();

        long blank =
                network.getNodeList().stream()
                        .map(n -> network.getRow(n).get("MASSQL::bile_acid", String.class))
                        .filter(""::equals)
                        .count();

        assertEquals(NODES - EXPECTED_MATCHES.size(), blank);
    }

    @Test
    void theDerivedColumnFollowsTheSameJoin() {
        run();

        for (CyNode node : network.getNodeList()) {
            CyRow row = network.getRow(node);
            Double derived = row.get("MASSQL::bile_acid_base_peak_i", Double.class);
            boolean matched =
                    EXPECTED_MATCHES.contains(Integer.valueOf(row.get(SCAN_COLUMN, String.class)));

            if (matched) {
                assertNotNull(derived, "matched node has no derived value");
                assertTrue(derived > 0, "a matched spectrum reported no base peak");
            } else {
                assertNull(derived);
            }
        }
    }
}
