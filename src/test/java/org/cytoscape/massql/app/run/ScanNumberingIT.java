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
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A peak list numbers its spectra one of two ways, and <b>both are legitimate</b>: an explicit
 * {@code SCANS=} header, or -- when the file carries none -- the spectrum's 1-based position in the
 * file. GNPS and mzmine routinely emit the latter, so ordinal numbering is the normal case for this
 * app's users, not a fallback to be tolerated.
 *
 * <p>The app takes whichever number the reader reports and joins on it, with no special-casing.
 * These tests pin that, using two fixtures holding the <em>same three spectra</em> and differing
 * only in whether they declare {@code SCANS=}. The same query matches the second spectrum in both,
 * so the only thing that changes is the number it is filed under: 2 by position, 202 by header.
 */
class ScanNumberingIT {

    private static final String MATCHES_SECOND_SPECTRUM =
            "QUERY scaninfo(MS2DATA) WHERE MS2PROD=300.0:TOLERANCEMZ=0.5";

    /** No {@code SCANS=} headers, so the reader numbers by position: 1, 2, 3. */
    private static final Path ORDINAL = TestFixtures.require("fixtures/micro/micro.mgf");

    /** The same spectra, declaring SCANS=101, 202, 303. */
    private static final Path EXPLICIT = TestFixtures.require("fixtures/scans/explicit_scans.mgf");

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private MassqlRunner runner;

    @BeforeEach
    void setUp() {
        network = support.getNetwork();
        network.getDefaultNodeTable().createColumn("scan", Integer.class, false);

        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyEventHelper.class)).thenReturn(new DummyCyEventHelper());
        runner = new MassqlRunner(registrar);
    }

    private CyNode nodeWithScan(int scan) {
        CyNode node = network.addNode();
        network.getRow(node).set("scan", scan);
        return node;
    }

    private MassqlRunSummary runAgainst(Path peakList) {
        return runner.run(
                new MassqlRunRequest(
                        peakList,
                        MATCHES_SECOND_SPECTRUM,
                        "q",
                        "scan",
                        true,
                        List.of(),
                        20.0,
                        network),
                null,
                () -> false);
    }

    private String cell(CyNode node) {
        return network.getRow(node).get("MASSQL::q", String.class);
    }

    @Test
    void positionDerivedScanNumbersJoinLikeAnyOther() {
        CyNode atPosition = nodeWithScan(2);

        MassqlRunSummary summary = runAgainst(ORDINAL);

        assertEquals(1, summary.matchedNodes());
        assertNotNull(cell(atPosition));
        assertEquals(
                "2",
                cell(atPosition).replaceAll(".*\"scan\":(\\d+).*", "$1"),
                "the result is filed under the spectrum's position in the file");
    }

    @Test
    void explicitScanHeadersJoinOnTheDeclaredNumber() {
        CyNode declared = nodeWithScan(202);

        MassqlRunSummary summary = runAgainst(EXPLICIT);

        assertEquals(1, summary.matchedNodes());
        assertNotNull(cell(declared));
        assertEquals(
                "202",
                cell(declared).replaceAll(".*\"scan\":(\\d+).*", "$1"),
                "the declared header wins over the position");
    }

    /**
     * The two schemes are not interchangeable, and nothing in the app pretends otherwise: a network
     * numbered for one file will not match the other. This is the failure the app cannot detect for
     * a user -- it looks exactly like a query that matched nothing -- so it is worth stating.
     */
    @Test
    void aNetworkNumberedForOneSchemeDoesNotMatchTheOther() {
        CyNode byPosition = nodeWithScan(2);

        assertEquals(0, runAgainst(EXPLICIT).matchedNodes());
        assertEquals("", cell(byPosition));

        CyNode byHeader = nodeWithScan(202);

        assertEquals(1, runAgainst(EXPLICIT).matchedNodes());
        assertNotNull(cell(byHeader));
        assertEquals("", cell(byPosition));
    }

    /**
     * A title is free text. {@code micro.mgf}'s spectra are titled {@code micro.scan1}, {@code
     * micro.scan3}, {@code micro.scan5} while their scan numbers are 1, 2, 3 -- so a network built
     * by reading titles would join against nothing.
     */
    @Test
    void spectrumTitlesAreNotScanNumbers() {
        CyNode fromTitle = nodeWithScan(3);

        MassqlRunSummary summary = runAgainst(ORDINAL);

        assertEquals(
                0, summary.matchedNodes(), "the second spectrum is scan 2, titled micro.scan3");
        assertEquals("", cell(fromTitle));
    }
}
