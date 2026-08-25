package org.cytoscape.massql.app.ui;

import java.awt.GraphicsEnvironment;
import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.application.swing.CyColumnPresentationManager;
import org.cytoscape.massql.app.TestFixtures;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.service.util.CyServiceRegistrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dialog's own behaviour: when Apply is offered, when it says something, and what it points at.
 *
 * <p>Requires a display, since constructing a {@link javax.swing.JDialog} does. The CI workflow
 * supplies one through Xvfb; anywhere headless these are skipped rather than failed.
 *
 * <p>Focus is asserted as the component the dialog <em>chose</em>. The AWT focus manager moves
 * focus only once a window is showing, and showing a modal dialog in a test would block.
 */
class RunMassqlDialogIT {

    private static final String VALID_QUERY = "QUERY scaninfo(MS2DATA) WHERE MS2PROD=200.5";

    private final NetworkTestSupport support = new NetworkTestSupport();
    private CyNetwork network;
    private RunMassqlDialog dialog;

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(
                GraphicsEnvironment.isHeadless(), "a dialog cannot be built without a display");

        network = support.getNetwork();
        network.getDefaultNodeTable().createColumn("scan", Integer.class, false);

        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        when(registrar.getService(CyColumnPresentationManager.class))
                .thenReturn(mock(CyColumnPresentationManager.class));

        dialog = new RunMassqlDialog(null, new RunMassqlForm(network), registrar);
    }

    @AfterEach
    void tearDown() {
        if (dialog != null) {
            dialog.dispose();
        }
    }

    private void fillIn(String path, String name, String query) {
        dialog.fileField().setText(path);
        dialog.nameField().setText(name);
        dialog.queryArea().setText(query);
    }

    private void completeForm() {
        fillIn(TestFixtures.require("fixtures/micro/micro.mgf").toString(), "q", VALID_QUERY);
    }

    private void clickApply() {
        dialog.applyButton().doClick();
    }

    private static boolean isBlank(String html) {
        return html == null || html.replaceAll("<[^>]*>", "").isBlank();
    }

    @Test
    void applyIsOfferedOnlyOnceEveryFieldHasAValue() {
        assertFalse(dialog.applyButton().isEnabled(), "nothing has been entered yet");

        dialog.fileField().setText("/tmp/peaks.mgf");
        assertFalse(dialog.applyButton().isEnabled());

        dialog.nameField().setText("q");
        assertFalse(dialog.applyButton().isEnabled());

        dialog.queryArea().setText(VALID_QUERY);
        assertTrue(dialog.applyButton().isEnabled(), "every field now has a value");
    }

    /** Typing enables Apply directly; the user does not have to leave the field first. */
    @Test
    void applyTurnsOnWhileTheFieldStillHasFocus() {
        fillIn("/tmp/peaks.mgf", "q", "");

        dialog.queryArea().setText(VALID_QUERY);

        assertTrue(dialog.applyButton().isEnabled());
    }

    @Test
    void theDialogSaysNothingWhileTheFormIsBeingFilledIn() {
        assertTrue(isBlank(dialog.statusText()), dialog.statusText());

        fillIn("/tmp/peaks.mgf", "q", VALID_QUERY);

        assertTrue(isBlank(dialog.statusText()), "commentary belongs after Apply, not during");
    }

    @Test
    void aMissingFileIsReportedAgainstTheFileField() {
        fillIn("/no/such/peaks.mgf", "q", VALID_QUERY);

        clickApply();

        assertFalse(isBlank(dialog.statusText()));
        assertSame(dialog.fileField(), dialog.rejectedField());
        assertNull(dialog.request(), "nothing was applied");
    }

    @Test
    void anIllegalQueryNameIsReportedAgainstTheNameField() {
        completeForm();
        dialog.nameField().setText("has:colon");

        clickApply();

        assertTrue(dialog.statusText().contains("':'"), dialog.statusText());
        assertSame(dialog.nameField(), dialog.rejectedField());
    }

    @Test
    void anUnparseableQueryIsReportedAgainstTheQueryArea() {
        completeForm();
        dialog.queryArea().setText("QUERY scansum(MS2DATA) WHERE MS2PROD=300.0");

        clickApply();

        assertTrue(dialog.statusText().contains("scansum"), dialog.statusText());
        assertSame(dialog.queryArea(), dialog.rejectedField());
    }

    @Test
    void aUsableFormYieldsARequest() {
        completeForm();

        clickApply();

        assertNotNull(dialog.request());
        assertEquals("q", dialog.request().queryName());
        assertTrue(isBlank(dialog.statusText()), "a clean run reports nothing");
    }

    @Test
    void theRequestNamesTheFileThatWasChosen() {
        File mgf = TestFixtures.require("fixtures/micro/micro.mgf").toFile();
        completeForm();

        clickApply();

        assertEquals(mgf.toPath(), dialog.request().file());
    }
}
