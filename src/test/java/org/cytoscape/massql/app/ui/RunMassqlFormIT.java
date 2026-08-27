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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     * The combo selects its first entry regardless, so the form has to hold the same thing. Leaving
     * it unset showed the user a chosen column beside a message asking them to choose one.
     */
    @Test
    void fallsBackToTheFirstCandidateWhenNoneIsNamedScan() {
        nodeTable.createColumn("cluster index", Integer.class, false);
        RunMassqlForm form = new RunMassqlForm(network);

        assertEquals(
                RunMassqlForm.scanColumnCandidates(network).get(0).getName(), form.scanColumn());
    }

    @Test
    void aColumnNamedScanIsPreferred() {
        nodeTable.createColumn("scan", Integer.class, false);
        RunMassqlForm form = new RunMassqlForm(network);

        assertEquals("scan", form.scanColumn());
    }

    /** The bug this replaced: every field filled, yet Apply stayed disabled. */
    @Test
    void aScanColumnNotNamedScanStillCompletesTheForm() {
        nodeTable.createColumn("cluster index", Integer.class, false);
        RunMassqlForm form = new RunMassqlForm(network);
        form.setFile(MGF);
        form.setQueryName("q");
        form.setQueryText("QUERY scaninfo(MS2DATA)");

        assertTrue(form.isComplete());
        assertNull(form.validate());
    }

    /**
     * Cytoscape gives every node table {@code name} and {@code shared name} as text, so there is
     * always something to offer even when the researcher added no identifier of their own -- and
     * for a GNPS network those are exactly where the scan number lives.
     */
    @Test
    void theBuiltInTextColumnsAreAlwaysAvailable() {
        nodeTable.createColumn("mz", Double.class, false);
        RunMassqlForm form = new RunMassqlForm(network);
        form.setFile(MGF);
        form.setQueryName("q");
        form.setQueryText("QUERY scaninfo(MS2DATA)");

        List<String> offered = names(RunMassqlForm.scanColumnCandidates(network));
        assertTrue(offered.contains("shared name"), offered.toString());
        assertFalse(offered.contains("mz"));

        assertNotNull(form.scanColumn());
        assertTrue(form.isComplete());
    }

    @Test
    void aFormWithEveryFieldSetIsComplete() {
        RunMassqlForm form = validForm();

        assertTrue(form.isComplete());
        assertNull(form.validate());
    }

    @Test
    void everyMissingFieldExplainsItself() {
        RunMassqlForm blank = new RunMassqlForm(network);
        assertEquals(RunMassqlForm.Field.FILE, blank.validate().field());

        RunMassqlForm noName = validForm();
        noName.setQueryName("  ");
        assertEquals(RunMassqlForm.Field.QUERY_NAME, noName.validate().field());

        RunMassqlForm noQuery = validForm();
        noQuery.setQueryText("");
        assertEquals(RunMassqlForm.Field.QUERY_TEXT, noQuery.validate().field());
    }

    /**
     * Apply turns on the moment every field has a value, without waiting for focus to leave one.
     * Judging the values happens later, when the user says they are finished.
     */
    @Test
    void completenessAndValidityAreSeparateQuestions() {
        RunMassqlForm form = validForm();
        form.setFile(new File("no-such-peaks.mgf"));

        assertTrue(form.isComplete(), "every field has a value");
        assertEquals(RunMassqlForm.Field.FILE, form.validate().field(), "but one is unusable");
    }

    @Test
    void anIncompleteFormOffersNoApply() {
        RunMassqlForm form = validForm();

        form.setQueryText("");
        assertFalse(form.isComplete());

        form.setQueryText("QUERY scaninfo(MS2DATA)");
        assertTrue(form.isComplete());
    }

    @Test
    void aMissingFileIsRefusedBeforeTheRunStarts() {
        RunMassqlForm form = validForm();
        form.setFile(new File("no-such-peaks.mgf"));

        assertTrue(form.isComplete(), "a path was entered, so Apply is offered");
        assertEquals(RunMassqlForm.Field.FILE, form.validate().field());
    }

    @Test
    void aQueryNameCarryingTheNamespaceSeparatorIsRefused() {
        RunMassqlForm form = validForm();
        form.setQueryName("MASSQL::x");

        RunMassqlForm.Problem problem = form.validate();
        assertEquals(RunMassqlForm.Field.QUERY_NAME, problem.field());
        assertTrue(problem.message().contains("':'"), problem.message());
    }

    @Test
    void theReservedQueryNameIsRefused() {
        RunMassqlForm form = validForm();
        form.setQueryName("QUERIES");

        RunMassqlForm.Problem problem = form.validate();
        assertEquals(RunMassqlForm.Field.QUERY_NAME, problem.field());
        assertTrue(problem.message().contains("reserved"), problem.message());
    }

    @Test
    void unselectingEveryColumnLeavesTheFormIncomplete() {
        RunMassqlForm form = validForm();
        form.setCreateResultColumn(false);

        assertFalse(form.isComplete());
        assertEquals(RunMassqlForm.Field.COLUMNS, form.validate().field());

        form.setDerived(ResultAttribute.BASE_PEAK_I, true);
        assertTrue(form.isComplete(), "one attribute is enough on its own");
    }

    @Test
    void onlyMzmlAndMzxmlCarryMs1() {
        RunMassqlForm form = validForm();

        form.setFile(MGF);
        assertFalse(form.fileCarriesMs1());

        form.setFile(MZML);
        assertTrue(form.fileCarriesMs1());
    }

    @Test
    void theMs1AttributesAreOfferedOnlyForAFileThatMeasuresThem() {
        RunMassqlForm form = validForm();

        form.setFile(MZML);
        assertTrue(form.applies(ResultAttribute.MS1_I));
        assertTrue(form.applies(ResultAttribute.BASE_PEAK_I));

        form.setFile(MGF);
        assertFalse(form.applies(ResultAttribute.MS1_I), "an MGF measures no precursor intensity");
        assertFalse(form.applies(ResultAttribute.MS1_PRECMZ));
        assertFalse(form.applies(ResultAttribute.MS1_BASE_PEAK_I));
        assertTrue(form.applies(ResultAttribute.BASE_PEAK_I), "fragment values still apply");
    }

    /** Otherwise the run would write a column of blanks under a name promising measurements. */
    @Test
    void anMs1AttributeTickedForAnMzmlDoesNotFollowTheUserToAnMgf() {
        RunMassqlForm form = validForm();
        form.setFile(MZML);
        form.setDerived(ResultAttribute.MS1_I, true);
        form.setDerived(ResultAttribute.TIC, true);

        form.setFile(MGF);

        assertEquals(List.of(ResultAttribute.TIC), form.toRequest().deriveAttributes());
    }

    @Test
    void anMgfWithOnlyMs1AttributesTickedCannotBeApplied() {
        RunMassqlForm form = validForm();
        form.setFile(MGF);
        form.setCreateResultColumn(false);
        form.setDerived(ResultAttribute.MS1_I, true);

        assertFalse(form.isComplete());
        assertEquals(RunMassqlForm.Field.COLUMNS, form.validate().field());
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
