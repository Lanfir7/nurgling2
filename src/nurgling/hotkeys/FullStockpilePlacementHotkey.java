package nurgling.hotkeys;

import haven.ClickData;
import haven.Gob;

/** Shift+RMB with a held item on empty ground starts Full Stockpile Maker. */
public final class FullStockpilePlacementHotkey {
    private FullStockpilePlacementHotkey() {
    }

    public static boolean shouldLaunch(boolean holdingItem, boolean groundTarget, int button, int mods,
                                       InputGesture gesture) {
        return holdingItem && groundTarget && matches(gesture, button, mods);
    }

    public static boolean matches(InputGesture gesture, int button, int mods) {
        return gesture != null && gesture.matchesMouse(button, mods);
    }

    public static boolean isGroundTarget(ClickData inf) {
        return inf == null || Gob.from(inf.ci) == null;
    }

    public static boolean passThroughHeldMouse(boolean areaSelect, boolean pendingLaunch) {
        return areaSelect || pendingLaunch;
    }

    public static String resolveItemName(String hint, String held, String firstSlot) {
        if (!blank(hint))
            return hint;
        if (!blank(held))
            return held;
        return firstSlot;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
