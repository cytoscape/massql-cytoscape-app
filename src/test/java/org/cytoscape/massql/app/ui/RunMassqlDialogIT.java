package org.cytoscape.massql.app.ui;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
@Timeout(30)
class RunMassqlDialogIT {

    private static final String VALID_QUERY = "QUERY scaninfo(MS2DATA) WHERE MS2PROD=200.5";

    /**
     * Swing is single-threaded: building or driving a component off the event thread is undefined,
     * and under a bare X server it deadlocks rather than merely misbehaving.
     */
    private static void onEdt(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for the event thread", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    private static <T> T fromEdt(Supplier<T> read) {
        AtomicReference<T> value = new AtomicReference<>();
        onEdt(() -> value.set(read.get()));
        return value.get();
    }

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

        onEdt(() -> dialog = new RunMassqlDialog(null, new RunMassqlForm(network), registrar));
    }

    @AfterEach
    void tearDown() {
        if (dialog != null) {
            onEdt(dialog::dispose);
        }
    }

    private void fillIn(String path, String name, String query) {
        onEdt(
                () -> {
                    onEdt(() -> dialog.fileField().setText(path));
                    onEdt(() -> dialog.nameField().setText(name));
                    onEdt(() -> dialog.queryArea().setText(query));
                });
    }

    private void completeForm() {
        fillIn(TestFixtures.require("fixtures/micro/micro.mgf").toString(), "q", VALID_QUERY);
    }

    private void clickApply() {
        onEdt(() -> dialog.applyButton().doClick());
    }

    private static boolean isBlank(String html) {
        return html == null || html.replaceAll("<[^>]*>", "").isBlank();
    }

    @Test
    void applyIsOfferedOnlyOnceEveryFieldHasAValue() {
        assertFalse(
                fromEdt(() -> dialog.applyButton().isEnabled()), "nothing has been entered yet");

        onEdt(() -> dialog.fileField().setText("/tmp/peaks.mgf"));
        assertFalse(fromEdt(() -> dialog.applyButton().isEnabled()));

        onEdt(() -> dialog.nameField().setText("q"));
        assertFalse(fromEdt(() -> dialog.applyButton().isEnabled()));

        onEdt(() -> dialog.queryArea().setText(VALID_QUERY));
        assertTrue(fromEdt(() -> dialog.applyButton().isEnabled()), "every field now has a value");
    }

    /** Typing enables Apply directly; the user does not have to leave the field first. */
    @Test
    void applyTurnsOnWhileTheFieldStillHasFocus() {
        fillIn("/tmp/peaks.mgf", "q", "");

        onEdt(() -> dialog.queryArea().setText(VALID_QUERY));

        assertTrue(fromEdt(() -> dialog.applyButton().isEnabled()));
    }

    @Test
    void theDialogSaysNothingWhileTheFormIsBeingFilledIn() {
        assertTrue(isBlank(fromEdt(dialog::statusText)), fromEdt(dialog::statusText));

        fillIn("/tmp/peaks.mgf", "q", VALID_QUERY);

        assertTrue(
                isBlank(fromEdt(dialog::statusText)), "commentary belongs after Apply, not during");
    }

    @Test
    void aMissingFileIsReportedAgainstTheFileField() {
        fillIn("/no/such/peaks.mgf", "q", VALID_QUERY);

        clickApply();

        assertFalse(isBlank(fromEdt(dialog::statusText)));
        assertSame(dialog.fileField(), fromEdt(dialog::rejectedField));
        assertNull(fromEdt(dialog::request), "nothing was applied");
    }

    @Test
    void anIllegalQueryNameIsReportedAgainstTheNameField() {
        completeForm();
        onEdt(() -> dialog.nameField().setText("has:colon"));

        clickApply();

        assertTrue(fromEdt(dialog::statusText).contains("':'"), fromEdt(dialog::statusText));
        assertSame(dialog.nameField(), fromEdt(dialog::rejectedField));
    }

    @Test
    void anUnparseableQueryIsReportedAgainstTheQueryArea() {
        completeForm();
        onEdt(() -> dialog.queryArea().setText("QUERY scansum(MS2DATA) WHERE MS2PROD=300.0"));

        clickApply();

        assertTrue(fromEdt(dialog::statusText).contains("scansum"), fromEdt(dialog::statusText));
        assertSame(dialog.queryArea(), fromEdt(dialog::rejectedField));
    }

    @Test
    void aUsableFormYieldsARequest() {
        completeForm();

        clickApply();

        assertNotNull(fromEdt(dialog::request));
        assertEquals("q", fromEdt(dialog::request).queryName());
        assertTrue(isBlank(fromEdt(dialog::statusText)), "a clean run reports nothing");
    }

    @Test
    void theRequestNamesTheFileThatWasChosen() {
        File mgf = TestFixtures.require("fixtures/micro/micro.mgf").toFile();
        completeForm();

        clickApply();

        assertEquals(mgf.toPath(), fromEdt(dialog::request).file());
    }
}
