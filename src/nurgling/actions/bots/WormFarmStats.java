package nurgling.actions.bots;

import java.util.Locale;

final class WormFarmStats {
    private final long startMs;
    private int lastWorms;
    private int harvested;
    private double lastStam = -1;
    private double stamSpent;

    WormFarmStats() {
        this(System.currentTimeMillis());
    }

    WormFarmStats(long startMs) {
        this.startMs = startMs;
    }

    void noteWorms(int inventoryCount) {
        if (inventoryCount > lastWorms)
            harvested += inventoryCount - lastWorms;
        lastWorms = Math.max(0, inventoryCount);
    }

    void noteStamina(double fraction) {
        if (fraction < 0)
            return;
        if (lastStam >= 0 && fraction < lastStam)
            stamSpent += lastStam - fraction;
        lastStam = fraction;
    }

    int harvested() {
        return harvested;
    }

    double wormsPerMinute(long nowMs) {
        return LevelerStats.unitsPerMinute(harvested, nowMs - startMs);
    }

    double staminaBarPerMinute(long nowMs) {
        long elapsed = nowMs - startMs;
        if (stamSpent <= 0 || elapsed <= 0)
            return 0;
        return stamSpent * 60000.0 / elapsed;
    }

    long elapsedMs(long nowMs) {
        return Math.max(0, nowMs - startMs);
    }

    static String formatStaminaRate(double barPerMinute) {
        if (barPerMinute <= 0)
            return "-";
        double pct = barPerMinute * 100.0;
        if (pct >= 10)
            return Math.round(pct) + "%";
        return String.format(Locale.US, "%.1f%%", pct);
    }
}
