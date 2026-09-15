package nurgling.actions.bots.forager;

/**
 * Decides whether Forager may use its synchronous drink action at a checkpoint.
 * Kept independent of game UI so the run-local cooldown and notification policy stay testable.
 */
public final class ForagerDrinkPolicy {
    public static final double DEFAULT_THRESHOLD = 0.51;
    public static final long DEFAULT_RETRY_COOLDOWN_MS = 5_000L;
    private static final double MIN_THRESHOLD = 0.10;
    private static final double MAX_THRESHOLD = 0.90;
    private static final double MIN_TIMEOUT_SECONDS = 1.0;
    private static final double MAX_TIMEOUT_SECONDS = 15.0;

    private ForagerDrinkPolicy() {
    }

    public enum Action {
        SKIP,
        DRINK,
        NOTIFY_NO_DRINK
    }

    public static final class Settings {
        private final double target;
        private final long retryCooldownMs;

        private Settings(double target, long retryCooldownMs) {
            this.target = target;
            this.retryCooldownMs = retryCooldownMs;
        }

        public double target() {
            return target;
        }

        public long retryCooldownMs() {
            return retryCooldownMs;
        }
    }

    public static Settings settings(Object thresholdValue, Object timeoutValue) {
        double threshold = numberOrDefault(thresholdValue, DEFAULT_THRESHOLD);
        if (!Double.isFinite(threshold) || threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD) {
            threshold = DEFAULT_THRESHOLD;
        }

        double timeoutSeconds = numberOrDefault(timeoutValue, DEFAULT_RETRY_COOLDOWN_MS / 1000.0);
        if (!Double.isFinite(timeoutSeconds) || timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            timeoutSeconds = DEFAULT_RETRY_COOLDOWN_MS / 1000.0;
        }
        return new Settings(threshold, (long) (timeoutSeconds * 1000.0));
    }

    private static double numberOrDefault(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    public static boolean mayHaveDrink(Number totalDrinkable) {
        return totalDrinkable == null || totalDrinkable.doubleValue() > 0;
    }

    public static final class RunState {
        private boolean reportedNoDrink;
        private boolean failedDrink;
        private long lastFailedDrinkMs;

        public Action atCheckpoint(boolean enabled, double stamina, boolean hasDrink, long nowMs, Settings settings) {
            if (!enabled || settings == null || !Double.isFinite(stamina) || stamina < 0 || stamina >= settings.target()) {
                return Action.SKIP;
            }
            if (!hasDrink) {
                if (reportedNoDrink) {
                    return Action.SKIP;
                }
                reportedNoDrink = true;
                return Action.NOTIFY_NO_DRINK;
            }
            if (failedDrink && nowMs - lastFailedDrinkMs < settings.retryCooldownMs()) {
                return Action.SKIP;
            }
            return Action.DRINK;
        }

        public void recordFailedDrink(long nowMs) {
            failedDrink = true;
            lastFailedDrinkMs = nowMs;
        }

        public void recordDrinkAttempt(boolean actionSucceeded, double staminaAfter, long nowMs, Settings settings) {
            if (!actionSucceeded || settings == null || !Double.isFinite(staminaAfter) || staminaAfter < settings.target()) {
                recordFailedDrink(nowMs);
            } else {
                failedDrink = false;
            }
        }
    }
}
