package nurgling.overlays;

import nurgling.NConfig;

/**
 * Live Combat HUD knobs for the world-space creature HP bar.
 * Offset is world Z (negative sits below the animal); width is unscaled pixels.
 */
public final class NCombatHpBarStyle {
    public static final float DEF_OFFSET = 13.2f;
    public static final float OFFSET_MIN = -20f;
    public static final float OFFSET_MAX = 40f;
    public static final int DEF_WIDTH = 78;
    public static final int WIDTH_MIN = 40;
    public static final int WIDTH_MAX = 160;
    public static final int BAR_H = 22;

    private NCombatHpBarStyle() {}

    public static float clampOffset(Object raw) {
        float v = DEF_OFFSET;
        if(raw instanceof Number)
            v = ((Number) raw).floatValue();
        return Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, v));
    }

    public static int clampWidth(Object raw) {
        int v = DEF_WIDTH;
        if(raw instanceof Number)
            v = ((Number) raw).intValue();
        return Math.max(WIDTH_MIN, Math.min(WIDTH_MAX, v));
    }

    public static float offsetZ() {
        return clampOffset(NConfig.get(NConfig.Key.combatCreatureHpBarOffset));
    }

    public static int unscaledWidth() {
        return clampWidth(NConfig.get(NConfig.Key.combatCreatureHpBarWidth));
    }
}
