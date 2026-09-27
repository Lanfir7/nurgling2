package haven;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameMetricsTest {
    private static void frame(FrameMetrics metrics, long token, long ms, boolean bg, boolean render) {
        metrics.record(token, ms * 1000000, 7, 1, 2, 1, 1, 2, bg, render, 1700000000000L + ms);
    }

    @Test void boundedCaptureUsesActualIntervalsAndRetainsChronologicalOrder() throws Exception {
        FrameMetrics metrics = new FrameMetrics(2);
        metrics.start();
        long token = metrics.token();
        for (long ms : new long[]{0, 10, 30, 90}) frame(metrics, token, ms, false, true);
        FrameMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.summary().contains("FPS 25.0"));
        assertTrue(snapshot.summary().contains("p99 60.00 ms"));
        assertTrue(snapshot.summary().contains(">50ms 1"));
        StringWriter csv = new StringWriter();
        snapshot.writeCsv(csv);
        assertTrue(csv.toString().split("\n")[1].startsWith("20.0,"));
        assertTrue(csv.toString().split("\n")[2].startsWith("60.0,"));
        assertEquals(1700000000090d,
                Double.parseDouble(csv.toString().split("\n")[2].split(",")[9]));
    }

    @Test void wallClockAdjustmentDoesNotChangeMeasuredInterval() throws Exception {
        FrameMetrics metrics = new FrameMetrics(2);
        metrics.start();
        metrics.record(metrics.token(), 1000000, 7, 1, 2, 1, 1, 2, false, true, 1000);
        metrics.record(metrics.token(), 11000000, 7, 1, 2, 1, 1, 2, false, true, 500);
        StringWriter csv = new StringWriter();
        metrics.snapshot().writeCsv(csv);
        String[] row = csv.toString().split("\n")[1].split(",");
        assertEquals(10.0, Double.parseDouble(row[0]));
        assertEquals(500.0, Double.parseDouble(row[9]));
    }

    @Test void stopAndRestartRejectFramesFromOldCaptureAndResetClock() {
        FrameMetrics metrics = new FrameMetrics(4);
        assertEquals(-1, metrics.token());
        metrics.start();
        long old = metrics.token();
        frame(metrics, old, 0, false, true);
        frame(metrics, old, 10, false, true);
        FrameMetrics.Snapshot saved = metrics.snapshot();
        metrics.stop();
        frame(metrics, old, 20, false, true);
        assertEquals(1, metrics.snapshot().size());
        metrics.start();
        frame(metrics, old, 30, false, true);
        frame(metrics, metrics.token(), 1000, false, true);
        assertEquals(0, metrics.snapshot().size());
        frame(metrics, metrics.token(), 1010, false, true);
        assertEquals(1, saved.size());
        assertTrue(metrics.snapshot().summary().contains("FPS 100.0"));
    }

    @Test void backgroundAndDisabledRenderingDoNotContaminateForegroundSummary() {
        FrameMetrics metrics = new FrameMetrics(5);
        metrics.start();
        long token = metrics.token();
        frame(metrics, token, 0, false, true);
        frame(metrics, token, 1000, true, true);
        frame(metrics, token, 2000, false, false);
        frame(metrics, token, 2010, false, true);
        frame(metrics, token, 2020, false, true);
        assertTrue(metrics.snapshot().summary().contains("UI frames: 1/4; FPS 100.0"));
    }
}
