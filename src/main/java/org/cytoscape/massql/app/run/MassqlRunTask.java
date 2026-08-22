package org.cytoscape.massql.app.run;

import org.cytoscape.massql.MassqlParseException;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.MassqlRunSummary;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

/** Runs one request off the event thread, reporting progress and honouring Cancel. */
public class MassqlRunTask extends AbstractTask {

    private final MassqlRunRequest request;
    private final MassqlRunner runner;
    private volatile MassqlRunSummary summary;

    public MassqlRunTask(MassqlRunRequest request, CyServiceRegistrar registrar) {
        this.request = request;
        this.runner = new MassqlRunner(registrar);
    }

    /** The result of the run, or null if it failed or has not finished. */
    public MassqlRunSummary summary() {
        return summary;
    }

    @Override
    public void run(TaskMonitor monitor) {
        monitor.setTitle("Run MassQL");
        try {
            summary = runner.run(request, monitor, () -> cancelled);
        } catch (MassqlParseException e) {
            // Position and construct are what make a parse failure actionable; the bare message
            // says only that something is wrong.
            throw new IllegalArgumentException(describe(e), e);
        }

        if (summary.cancelled()) {
            monitor.setStatusMessage("Cancelled -- the node table was not changed");
            return;
        }
        report(monitor);
    }

    private void report(TaskMonitor monitor) {
        monitor.setProgress(1.0);
        monitor.setStatusMessage(
                summary.matchedNodes()
                        + " of "
                        + (summary.matchedNodes() + summary.unmatchedNodes())
                        + " nodes matched "
                        + summary.resultRows()
                        + " result rows");

        for (String diagnostic : summary.diagnostics()) {
            monitor.showMessage(TaskMonitor.Level.WARN, diagnostic);
        }
        if (summary.duplicateScans() > 0) {
            monitor.showMessage(
                    TaskMonitor.Level.WARN,
                    summary.duplicateScans()
                            + " result rows shared a scan number with an earlier row and were"
                            + " discarded; each node carries the last row for its scan.");
        }
    }

    private static String describe(MassqlParseException e) {
        String where = e.position() > 0 ? " at position " + e.position() : "";
        return "The query could not be parsed" + where + ": " + e.getMessage();
    }
}
