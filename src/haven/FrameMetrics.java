package haven;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Locale;

/** Opt-in, bounded timings of the UI loop; no per-frame allocations or disk I/O. */
public final class FrameMetrics {
    private static final int COLUMNS = 10;
    private final int capacity;
    private double[][] rows;
    private volatile boolean enabled;
    private int next, count;
    private long previousEnd;
    private boolean hasPrevious;
    private boolean previousBackground, previousRendering;
    private long generation;

    public FrameMetrics(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void start() {
        if (rows == null) rows = new double[capacity][COLUMNS];
        next = count = 0;
        hasPrevious = false;
        generation++;
        enabled = true;
    }

    public synchronized void stop() { enabled = false; generation++; }
    public boolean enabled() { return enabled; }
    public synchronized long token() { return enabled ? generation : -1; }

    public synchronized void record(long token, long endNanos, double totalMs,
                                    double tickMs, double drawMs, double submitMs,
                                    double syncMs, double limitMs, boolean background,
                                    boolean rendering, long endEpochMillis) {
        if (!enabled || token != generation) return;
        // The first frame establishes the clock; it has no preceding interval.
        if (hasPrevious) {
            double interval = (endNanos - previousEnd) / 1e6;
            if (interval > 0) {
                double[] row = rows[next];
                row[0] = interval; row[1] = totalMs; row[2] = tickMs;
                row[3] = drawMs; row[4] = submitMs; row[5] = syncMs;
                // A transition interval may still contain the background FPS limit.
                row[6] = limitMs; row[7] = (background || previousBackground) ? 1 : 0;
                row[8] = (rendering && previousRendering) ? 1 : 0;
                // Wall time only locates the frame in JFR; durations use the monotonic clock.
                row[9] = endEpochMillis;
                next = (next + 1) % rows.length;
                count = Math.min(count + 1, rows.length);
            }
        }
        previousEnd = endNanos;
        previousBackground = background;
        previousRendering = rendering;
        hasPrevious = true;
    }

    public synchronized Snapshot snapshot() {
        double[][] copy = new double[count][];
        int first = (next - count + capacity) % capacity;
        for (int i = 0; i < count; i++) copy[i] = rows[(first + i) % rows.length].clone();
        return new Snapshot(copy);
    }

    public static final class Snapshot {
        private final double[][] rows;
        Snapshot(double[][] rows) { this.rows = rows; }
        public int size() { return rows.length; }

        /** Foreground rendered frames only. These are UI-loop intervals, not GPU timings. */
        public String summary() {
            double[] times = new double[rows.length];
            int n = 0, over50 = 0, over100 = 0;
            double total = 0;
            for (double[] row : rows) {
                if (row[7] != 0 || row[8] == 0) continue;
                times[n++] = row[0]; total += row[0];
                if (row[0] > 50) over50++;
                if (row[0] > 100) over100++;
            }
            if (n == 0) return "No foreground rendered frame intervals (retained: " + rows.length + ").";
            Arrays.sort(times, 0, n);
            return String.format(Locale.ROOT,
                    "UI frames: %d/%d; FPS %.1f; p50 %.2f ms; p95 %.2f ms; p99 %.2f ms; max %.2f ms; >50ms %d; >100ms %d",
                    n, rows.length, 1000 * n / total, percentile(times, n, .50),
                    percentile(times, n, .95), percentile(times, n, .99), times[n - 1], over50, over100);
        }

        private static double percentile(double[] times, int n, double fraction) {
            return times[Math.max(0, (int)Math.ceil(n * fraction) - 1)];
        }

        public void writeCsv(Writer out) throws IOException {
            out.write("interval_ms,total_ms,tick_ms,draw_ms,submit_ms,sync_wait_ms,limit_wait_ms,background,rendering,end_epoch_ms\n");
            for (double[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) out.write(',');
                    out.write(Double.toString(row[i]));
                }
                out.write('\n');
            }
        }
    }
}
