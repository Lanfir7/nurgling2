package nurgling.actions.bots;

import java.util.Locale;

final class LevelerStats {
    private final long startMs;
    private long processed;
    private long lastRemaining = -1;

    LevelerStats() {
        this(System.currentTimeMillis());
    }

    LevelerStats(long startMs) {
        this.startMs = startMs;
    }

    static int remainingWork(int soilRequired, int soilToDig) {
        return soilRequired > 0 ? soilRequired : Math.max(0, soilToDig);
    }

    void noteRemaining(long remaining) {
        if (lastRemaining >= 0 && remaining >= 0 && remaining < lastRemaining)
            processed += lastRemaining - remaining;
        if (remaining >= 0)
            lastRemaining = remaining;
    }

    long processed() {
        return processed;
    }

    long lastRemaining() {
        return lastRemaining;
    }

    double unitsPerMinute(long nowMs) {
        return unitsPerMinute(processed, nowMs - startMs);
    }

    Long etaMs(long nowMs) {
        long remaining = lastRemaining < 0 ? 0 : lastRemaining;
        return etaMs(remaining, unitsPerMinute(nowMs));
    }

    static double unitsPerMinute(long processed, long elapsedMs) {
        if (processed <= 0 || elapsedMs <= 0)
            return 0;
        return processed * 60000.0 / elapsedMs;
    }

    static Long etaMs(long remaining, double unitsPerMinute) {
        if (remaining <= 0)
            return 0L;
        if (unitsPerMinute <= 0)
            return null;
        return Math.round(remaining / unitsPerMinute * 60000.0);
    }

    static String formatRate(double unitsPerMinute) {
        if (unitsPerMinute <= 0)
            return "-";
        if (unitsPerMinute >= 100)
            return String.valueOf(Math.round(unitsPerMinute));
        return String.format(Locale.US, "%.1f", unitsPerMinute);
    }

    static String formatDuration(Long ms) {
        if (ms == null)
            return "-";
        if (ms <= 0)
            return "0s";
        long s = Math.max(1, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0)
            return h + "h " + m + "m";
        if (m > 0)
            return m + "m " + sec + "s";
        return sec + "s";
    }
}
