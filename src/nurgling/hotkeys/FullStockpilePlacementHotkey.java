package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import nurgling.db.StockpileStoragePolicy;

import java.awt.event.KeyEvent;

/** Ctrl+Shift while a stockpile hologram is up starts Full Stockpile Maker. */
public final class FullStockpilePlacementHotkey {
    private FullStockpilePlacementHotkey() {
    }

    public static boolean shouldLaunch(String placingRes, int keyCode, int mods, InputGesture gesture) {
        return StockpileStoragePolicy.isStockpileRes(placingRes) && matches(gesture, keyCode, mods);
    }

    public static boolean matches(InputGesture gesture, int keyCode, int mods) {
        if (gesture == null || gesture.type() != InputGesture.Type.KEY)
            return false;
        KeyMatch key = gesture.key();
        if (key == null)
            return false;
        if (isCtrlShiftBinding(key))
            return isCtrlShiftModifierPress(keyCode, mods);
        return keyCode == key.code && ((mods & key.modmask) == (key.modmatch & key.modmask));
    }

    public static String resolveItemName(String hint, String held, String firstSlot) {
        if (!blank(hint))
            return hint;
        if (!blank(held))
            return held;
        return firstSlot;
    }

    static boolean isCtrlShiftBinding(KeyMatch key) {
        return key.code == KeyEvent.VK_SHIFT
                && (key.modmask & KeyMatch.C) != 0
                && (key.modmatch & KeyMatch.C) != 0;
    }

    static boolean isCtrlShiftModifierPress(int keyCode, int mods) {
        boolean modifierKey = keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL;
        if (!modifierKey)
            return false;
        boolean ctrl = (mods & UI.MOD_CTRL) != 0 || keyCode == KeyEvent.VK_CONTROL;
        boolean shift = (mods & UI.MOD_SHIFT) != 0 || keyCode == KeyEvent.VK_SHIFT;
        boolean extra = (mods & UI.MOD_META) != 0;
        return ctrl && shift && !extra;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
