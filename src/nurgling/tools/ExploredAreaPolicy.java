package nurgling.tools;

/**
 * Pure helpers for explored-area display vs recording and config migration.
 */
public final class ExploredAreaPolicy {
    private ExploredAreaPolicy() {}

    public static boolean shouldRecord(Object recordFlag) {
        return Boolean.TRUE.equals(recordFlag);
    }

    public static boolean shouldDraw(Object displayFlag) {
        return Boolean.TRUE.equals(displayFlag);
    }

    /**
     * Existing configs that only had {@code exploredAreaEnable} inherit recording
     * from that flag. Explicit {@code exploredAreaRecord} wins even if display differs.
     */
    public static boolean migrateRecord(boolean recordKeyPresent, Object recordValue, Object displayEnable) {
        if (recordKeyPresent) {
            return Boolean.TRUE.equals(recordValue);
        }
        return Boolean.TRUE.equals(displayEnable);
    }
}
