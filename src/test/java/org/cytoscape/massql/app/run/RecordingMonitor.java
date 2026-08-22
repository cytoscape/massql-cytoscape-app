package org.cytoscape.massql.app.run;

import java.util.List;

import org.cytoscape.work.TaskMonitor;

/** A {@link TaskMonitor} that records status messages so a test can assert on them. */
final class RecordingMonitor implements TaskMonitor {

    private final List<String> statusMessages;

    RecordingMonitor(List<String> statusMessages) {
        this.statusMessages = statusMessages;
    }

    @Override
    public void setTitle(String title) {}

    @Override
    public void setProgress(double progress) {}

    @Override
    public void setStatusMessage(String statusMessage) {
        statusMessages.add(statusMessage);
    }

    @Override
    public void showMessage(Level level, String message) {}
}
