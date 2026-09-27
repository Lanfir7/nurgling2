package nurgling;

import haven.Coord;

/** Percentage-based placement with a compass-following default and full-card bounds. */
public final class LootNoticeLayout {
    public static final int DEFAULT_X = 50;
    public static final int DEFAULT_Y = 12;
    public static final int DEFAULT_BACKGROUND_OPACITY = 90;

    public final boolean belowCompass;
    public final int xPercent;
    public final int yPercent;
    public final int backgroundOpacity;

    public LootNoticeLayout(boolean belowCompass, int xPercent, int yPercent, int backgroundOpacity) {
        this.belowCompass = belowCompass;
        this.xPercent = clampPercent(xPercent);
        this.yPercent = clampPercent(yPercent);
        this.backgroundOpacity = clampPercent(backgroundOpacity);
    }

    public static int percent(Object value, int fallback) {
        if (!(value instanceof Number))
            return clampPercent(fallback);
        double number = ((Number)value).doubleValue();
        return Double.isFinite(number) ? clampPercent((int)Math.round(number)) : clampPercent(fallback);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static int visibleRows(int screenHeight, int rowHeight, int gap, int availableRows) {
        int step = Math.max(1, rowHeight + gap);
        int fit = Math.max(0, (Math.max(0, screenHeight) + gap) / step);
        return Math.max(0, Math.min(availableRows, fit));
    }

    public Coord origin(Coord screen, Coord card, int gap, int rows, Integer compassBottom,
                        int compassGap, int fallbackTop) {
        int width = Math.max(0, card.x);
        int height = Math.max(0, card.y);
        int stackHeight = rows <= 0 ? 0 : rows * height + Math.max(0, rows - 1) * Math.max(0, gap);
        int maxX = Math.max(0, screen.x - width);
        int maxY = Math.max(0, screen.y - stackHeight);
        int x = belowCompass ? maxX / 2 : (int)Math.round(maxX * xPercent / 100.0);
        int y;
        if (belowCompass)
            y = (compassBottom == null ? fallbackTop : compassBottom + compassGap);
        else
            y = (int)Math.round(maxY * yPercent / 100.0);
        return Coord.of(Math.max(0, Math.min(maxX, x)), Math.max(0, Math.min(maxY, y)));
    }
}
