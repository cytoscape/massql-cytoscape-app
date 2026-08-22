package org.cytoscape.massql.app.run;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.cytoscape.massql.io.ScanView;
import org.cytoscape.massql.io.SpectraStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressSpectraStreamTest {

    /** Counts scans handed out and records whether it was closed; the contents do not matter. */
    private static final class FakeStream implements SpectraStream {
        private final int total;
        private int served;
        private boolean closed;

        FakeStream(int total) {
            this.total = total;
        }

        @Override
        public boolean hasNext() {
            return served < total;
        }

        @Override
        public ScanView next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            served++;
            return null;
        }

        @Override
        public List<String> diagnostics() {
            return List.of("from the delegate");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static int drain(SpectraStream stream) {
        int n = 0;
        while (stream.hasNext()) {
            stream.next();
            n++;
        }
        return n;
    }

    @Test
    void passesEveryScanThroughWhenNotCancelled() {
        FakeStream delegate = new FakeStream(7);
        ProgressSpectraStream stream = new ProgressSpectraStream(delegate, null, () -> false);

        assertEquals(7, drain(stream));
        assertEquals(7, stream.scansRead());
    }

    @Test
    void cancellingEndsTheLoopWithoutThrowing() {
        FakeStream delegate = new FakeStream(1000);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ProgressSpectraStream stream = new ProgressSpectraStream(delegate, null, cancelled::get);

        int read = 0;
        while (stream.hasNext()) {
            stream.next();
            if (++read == 3) {
                cancelled.set(true);
            }
        }

        // The engine's loop is `while (hasNext())`, so reporting exhaustion stops it cleanly --
        // one scan after the flag flips, and with the reader still in a closeable state.
        assertEquals(3, read);
        assertTrue(delegate.hasNext(), "the delegate was abandoned, not drained");
    }

    @Test
    void closingReachesTheDelegate() {
        FakeStream delegate = new FakeStream(1);
        new ProgressSpectraStream(delegate, null, () -> false).close();
        assertTrue(delegate.closed);
    }

    @Test
    void diagnosticsComeFromTheDelegate() {
        ProgressSpectraStream stream =
                new ProgressSpectraStream(new FakeStream(1), null, () -> false);
        assertEquals(List.of("from the delegate"), stream.diagnostics());
    }

    @Test
    void aNullCancelSupplierMeansNeverCancelled() {
        ProgressSpectraStream stream = new ProgressSpectraStream(new FakeStream(4), null, null);
        assertEquals(4, drain(stream));
    }

    @Test
    void statusIsReportedOnlyEveryFiveHundredScans() {
        List<String> messages = new ArrayList<>();
        ProgressSpectraStream stream =
                new ProgressSpectraStream(
                        new FakeStream(1200), new RecordingMonitor(messages), () -> false);

        drain(stream);

        assertEquals(List.of("500 spectra scanned", "1000 spectra scanned"), messages);
        assertFalse(messages.isEmpty());
    }
}
