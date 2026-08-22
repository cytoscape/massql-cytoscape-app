package org.cytoscape.massql.app.ui;

import java.awt.Window;

import javax.swing.SwingUtilities;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.run.MassqlRunTask;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

/**
 * Backs the Run MassQL menu items.
 *
 * <p>The dialog opens here rather than inside a task. Cytoscape calls {@code createTaskIterator} on
 * the event thread before handing the iterator to the task manager, so the modal appears with
 * nothing layered over it -- whereas a task that opened its own dialog would surface underneath the
 * progress window the task manager has already shown.
 */
public class RunMassqlTaskFactory implements NetworkTaskFactory {

    private final CyServiceRegistrar registrar;

    public RunMassqlTaskFactory(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public boolean isReady(CyNetwork network) {
        return network != null;
    }

    @Override
    public TaskIterator createTaskIterator(CyNetwork network) {
        MassqlRunRequest request = ask(network);
        return request == null
                ? new TaskIterator(new NothingToDo())
                : new TaskIterator(new MassqlRunTask(request, registrar));
    }

    private MassqlRunRequest ask(CyNetwork network) {
        Window owner = registrar.getService(CySwingApplication.class).getJFrame();
        RunMassqlDialog dialog = new RunMassqlDialog(owner, new RunMassqlForm(network), registrar);

        if (SwingUtilities.isEventDispatchThread()) {
            dialog.setVisible(true);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> dialog.setVisible(true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new IllegalStateException("the Run MassQL dialog failed to open", e);
            }
        }
        return dialog.request();
    }

    /**
     * Returned when the user cancels. The task manager is not documented to accept an empty
     * iterator, and one task that does nothing is cheaper than finding out.
     */
    private static final class NothingToDo extends AbstractTask implements Task {
        @Override
        public void run(TaskMonitor monitor) {
            // Cancelled before anything was chosen.
        }
    }
}
