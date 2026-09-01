package nurgling.widgets.compass;

import nurgling.NConfig;

public final class NCompassSettings {
    private NCompassSettings() {
    }

    public static boolean showBar() {
        return Boolean.TRUE.equals(NConfig.get(NConfig.Key.showCompassBar));
    }

    public static boolean showLegacyPointers() {
        return Boolean.TRUE.equals(NConfig.get(NConfig.Key.showLegacyCompassPointers));
    }

    public static boolean showQuests() {
        return enabled(NConfig.Key.showCompassQuests);
    }

    public static boolean showParty() {
        return enabled(NConfig.Key.showCompassParty);
    }

    public static boolean showDatabasePeers() {
        return enabled(NConfig.Key.showCompassDatabasePeers);
    }

    public static boolean showNearbyPlayers() {
        return enabled(NConfig.Key.showCompassNearbyPlayers);
    }

    public static boolean showCombatTargets() {
        return enabled(NConfig.Key.showCompassCombatTargets);
    }

    public static int backgroundOpacity() {
        Object value = NConfig.get(NConfig.Key.compassBackgroundOpacity);
        int opacity = value instanceof Number ? ((Number) value).intValue() : 0;
        return Math.max(0, Math.min(100, opacity));
    }

    public static int backgroundAlpha() {
        return Math.round(backgroundOpacity() * 255.0f / 100.0f);
    }

    private static boolean enabled(NConfig.Key key) {
        return Boolean.TRUE.equals(NConfig.get(key));
    }
}
