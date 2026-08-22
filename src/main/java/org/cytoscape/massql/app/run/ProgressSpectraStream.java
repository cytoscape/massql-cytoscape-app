package org.cytoscape.massql.app.run;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.cytoscape.massql.io.ScanView;
import org.cytoscape.massql.io.SpectraStream;
import org.cytoscape.work.TaskMonitor;

/**
 * Adds progress reporting and cancellation to a spectra stream, neither of which the SDK offers.
 *
 * <p>Reporting {@code hasNext() == false} ends the engine's loop cleanly, so cancelling costs at
 * most one scan and never leaves the reader half-consumed. Progress is a scan count rather than a
 * percentage because nothing in the SDK exposes a total or a byte offset to divide by.
 */
public final class ProgressSpectraStream implements SpectraStream {

    private static final int REPORT_EVERY = 500;

    private final SpectraStream delegate;
    private final TaskMonitor monitor;
    private final BooleanSupplier cancelled;
    private long seen;

    public ProgressSpectraStream(
            SpectraStream delegate, TaskMonitor monitor, BooleanSupplier cancelled) {
        this.delegate = delegate;
        this.monitor = monitor;
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }

    /** Scans handed to the engine so far, whether or not they matched. */
    public long scansRead() {
        return seen;
    }

    @Override
    public boolean hasNext() {
        return !cancelled.getAsBoolean() && delegate.hasNext();
    }

    @Override
    public ScanView next() {
        ScanView view = delegate.next();
        if (++seen % REPORT_EVERY == 0 && monitor != null) {
            monitor.setStatusMessage(seen + " spectra scanned");
        }
        return view;
    }

    @Override
    public List<String> diagnostics() {
        return delegate.diagnostics();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
