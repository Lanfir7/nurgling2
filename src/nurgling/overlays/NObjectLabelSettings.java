package nurgling.overlays;

import nurgling.NConfig;

import java.util.concurrent.atomic.AtomicLong;

/** Immutable, validated settings shared by icon-sign and parchment labels. */
public final class NObjectLabelSettings {
    private static final AtomicLong REVISION = new AtomicLong();
    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 24;
    public static final int MIN_HEIGHT = 0;
    public static final int MAX_HEIGHT = 20;
    public static final int MIN_OPACITY = 0;
    public static final int MAX_OPACITY = 100;

    public final boolean enabled;
    public final boolean iconSigns;
    public final boolean parchments;
    public final int fontSize;
    public final int height;
    public final int backgroundOpacity;

    private NObjectLabelSettings(boolean enabled, boolean iconSigns, boolean parchments,
                                 int fontSize, int height, int backgroundOpacity) {
        this.enabled = enabled;
        this.iconSigns = iconSigns;
        this.parchments = parchments;
        this.fontSize = clamp(fontSize, MIN_FONT_SIZE, MAX_FONT_SIZE);
        this.height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        this.backgroundOpacity = clamp(backgroundOpacity, MIN_OPACITY, MAX_OPACITY);
    }

    public static NObjectLabelSettings current() {
        return new NObjectLabelSettings(
                bool(NConfig.Key.objectLabelsEnabled, true),
                bool(NConfig.Key.objectLabelIconSigns, true),
                bool(NConfig.Key.objectLabelParchments, true),
                integer(NConfig.Key.objectLabelFontSize, 12),
                integer(NConfig.Key.objectLabelHeight, 5),
                integer(NConfig.Key.objectLabelBackgroundOpacity, 50));
    }

    public static long revision() {
        return REVISION.get();
    }

    public static void changed() {
        REVISION.incrementAndGet();
    }

    private static boolean bool(NConfig.Key key, boolean fallback) {
        Object value = NConfig.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int integer(NConfig.Key key, int fallback) {
        Object value = NConfig.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
