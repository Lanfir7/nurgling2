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
}
