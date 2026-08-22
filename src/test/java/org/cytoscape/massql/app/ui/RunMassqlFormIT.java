package org.cytoscape.massql.app.ui;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.massql.MassqlOptions;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dialog's rules, exercised without a display. Everything here decides what the user is allowed
 * to do before a single spectrum is read.
 */
class RunMassqlFormIT {

    private static final File MGF = TestFixtures.require("fixtures/micro/micro.mgf").toFile();
    private static final File MZML = TestFixtures.require("fixtures/micro/micro.mzML").toFile();

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private CyTable nodeTable;

    @BeforeEach
    void setUp() {
        network = support.getNetwork();
        nodeTable = network.getDefaultNodeTable();
    }

    private RunMassqlForm validForm() {
        if (nodeTable.getColumn("scan") == null) {
            nodeTable.createColumn("scan", Integer.class, false);
        }
        RunMassqlForm form = new RunMassqlForm(network);
        form.setFile(MGF);
        form.setQueryName("hexose_loss");
        form.setQueryText("QUERY scaninfo(MS2DATA) WHERE MS2PROD=200.5");
        return form;
    }

    private static List<String> names(List<CyColumn> columns) {
        return columns.stream().map(CyColumn::getName).toList();
    }

    @Test
    void offersOnlyColumnsThatCouldHoldAScanNumber() {
        nodeTable.createColumn("scan", Integer.class, false);
        nodeTable.createColumn("cluster index", Long.class, false);
        nodeTable.createColumn("feature id", String.class, false);
        nodeTable.createColumn("mz", Double.class, false);
        nodeTable.createColumn("keep", Boolean.class, false);

        List<String> offered = names(RunMassqlForm.scanColumnCandidates(network));

        assertTrue(offered.containsAll(List.of("scan", "cluster index", "feature id")));
        assertFalse(offered.contains("mz"), "a decimal m/z is not a scan number");
        assertFalse(offered.contains("keep"));
    }

    @Test
    void doesNotOfferTheSuidPrimaryKey() {
        nodeTable.createColumn("scan", Integer.class, false);

        List<String> offered = names(RunMassqlForm.scanColumnCandidates(network));

        assertFalse(
                offered.contains(CyNetwork.SUID),
                "SUID is assigned by Cytoscape and means nothing to an instrument");
    }

    @Test
    void preselectsAColumnNamedScan() {
        nodeTable.createColumn("other", Integer.class, false);
        nodeTable.createColumn("scan", Integer.class, false);

        assertEquals("scan", new RunMassqlForm(network).scanColumn());
    }

    /**
     * Every node table already carries {@code name} and {@code shared name}, so preselecting "the
     * first candidate" would quietly join on one of those -- a run that completes, writes a column,
     * and matches nothing, with no clue as to why.
     */
    @Test
    void selectsNothingWhenNoColumnIsNamedScan() {
        nodeTable.createColumn("cluster index", Integer.class, false);
        RunMassqlForm form = new RunMassqlForm(network);
        form.setFile(MGF);
        form.setQueryName("q");
        form.setQueryText("QUERY scaninfo(MS2DATA)");

        assertNull(form.scanColumn());
        assertFalse(form.isReady());
        assertTrue(form.whyNotReady().contains("Choose the node column"), form.whyNotReady());

        form.setScanColumn("cluster index");
        assertTrue(form.isReady(), "an explicit choice is all that was missing");
    }

    @Test
    void aNetworkWithNoUsableColumnSaysSo() {
        nodeTable.createColumn("mz", Double.class, false);
        RunMassqlForm form = new RunMassqlForm(network);
        form.setFile(MGF);
        form.setQueryName("q");
        form.setQueryText("QUERY scaninfo(MS2DATA)");

        assertNull(form.scanColumn());
        assertFalse(form.isReady());
        assertTrue(form.whyNotReady().contains("scan number"), form.whyNotReady());
    }

    @Test
    void aCompleteFormIsReady() {
        RunMassqlForm form = validForm();

        assertNull(form.whyNotReady());
        assertTrue(form.isReady());
    }

    @Test
    void everyMissingFieldExplainsItself() {
        RunMassqlForm blank = new RunMassqlForm(network);
        assertTrue(blank.whyNotReady().contains("peak list"), blank.whyNotReady());

        RunMassqlForm noName = validForm();
        noName.setQueryName("  ");
        assertTrue(noName.whyNotReady().contains("Name the query"), noName.whyNotReady());

        RunMassqlForm noQuery = validForm();
        noQuery.setQueryText("");
        assertTrue(noQuery.whyNotReady().contains("MassQL query"), noQuery.whyNotReady());
    }

    @Test
    void aMissingFileIsRefusedBeforeTheRunStarts() {
        RunMassqlForm form = validForm();
        form.setFile(new File("no-such-peaks.mgf"));

        assertFalse(form.isReady());
        assertTrue(form.whyNotReady().contains("does not exist"), form.whyNotReady());
    }

    @Test
    void aQueryNameCarryingTheNamespaceSeparatorIsRefused() {
        RunMassqlForm form = validForm();
        form.setQueryName("MASSQL::x");

        assertTrue(form.whyNotReady().contains("':'"), form.whyNotReady());
    }

    @Test
    void unselectingEverythingBlocksApply() {
        RunMassqlForm form = validForm();
        form.setCreateResultColumn(false);

        assertFalse(form.isReady());
        assertTrue(form.whyNotReady().contains("at least one column"), form.whyNotReady());

        form.setDerived(ResultAttribute.BASE_PEAK_I, true);
        assertTrue(form.isReady(), "one attribute is enough on its own");
    }

    @Test
    void theToleranceIsInertForAnMgfAndLiveForAnMzml() {
        RunMassqlForm form = validForm();

        form.setFile(MGF);
        assertFalse(form.precursorToleranceApplies(), "an MGF carries no MS1 scans");

        form.setFile(MZML);
        assertTrue(form.precursorToleranceApplies());
    }

    @Test
    void aToleranceTypedForAnMgfDoesNotSilentlyTravelWithTheRequest() {
        RunMassqlForm form = validForm();
        form.setFile(MGF);
        form.setPrecursorTolPpm(5.0);

        // The field is disabled for an MGF, so a value left over from an earlier mzML selection
        // must not reach the engine as though the user had chosen it for this file.
        assertEquals(MassqlOptions.DEFAULT_PRECURSOR_TOL_PPM, form.toRequest().precursorTolPpm());
    }

    @Test
    void theRequestCarriesWhatWasEntered() {
        RunMassqlForm form = validForm();
        form.setFile(MZML);
        form.setPrecursorTolPpm(7.5);
        form.setDerived(ResultAttribute.TIC, true);
        form.setDerived(ResultAttribute.BASE_PEAK_I, true);
        form.setDerived(ResultAttribute.TIC, false);

        MassqlRunRequest request = form.toRequest();

        assertEquals("hexose_loss", request.queryName());
        assertEquals("scan", request.scanColumn());
        assertEquals(7.5, request.precursorTolPpm());
        assertTrue(request.createResultColumn());
        assertEquals(List.of(ResultAttribute.BASE_PEAK_I), request.deriveAttributes());
        assertEquals(network, request.network());
    }

    @Test
    void aQueryNameIsTrimmedBeforeItNamesAColumn() {
        RunMassqlForm form = validForm();
        form.setQueryName("  spaced  ");

        assertEquals("spaced", form.toRequest().queryName());
    }
}
