package nurgling;

public final class LoginPreferences {
    private LoginPreferences() {}

    public static boolean shouldOpenInventory(Object setting) {
        return setting instanceof Boolean && (Boolean) setting;
    }

    /**
     * Speed to send to the server, or null if nothing should be sent yet.
     * On login max is often 0 (crawl only); wait until max allows the preferred speed,
     * but still step up as far as max currently allows.
     */
    public static Integer speedToApply(int cur, int max, Integer preferred) {
        if (preferred == null || preferred < 0 || preferred > 3) {
            return null;
        }
        if (max < 0) {
            return null;
        }
        int target = Math.min(preferred, max);
        if (target == cur) {
            return null;
        }
        return target;
    }

    public static Integer preferredSpeedFromConfig(Object speedPref) {
        if (!(speedPref instanceof Number)) {
            return null;
        }
        return ((Number) speedPref).intValue();
    }
}
