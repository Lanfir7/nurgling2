package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

class FullStockpilePlacementHotkeyTest {
    private static final InputGesture DEFAULT = InputGesture.key(
            KeyMatch.forcode(KeyEvent.VK_SHIFT, KeyMatch.C, KeyMatch.C));

    @Test void ctrlThenShiftOnStockpileGhostLaunches() {
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_SHIFT, UI.MOD_CTRL, DEFAULT));
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-board", KeyEvent.VK_SHIFT, UI.MOD_CTRL | UI.MOD_SHIFT, DEFAULT));
    }

    @Test void shiftThenCtrlOnStockpileGhostLaunches() {
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_CONTROL, UI.MOD_SHIFT, DEFAULT));
    }

    @Test void shiftAloneOrHouseGhostDoesNotLaunch() {
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_SHIFT, 0, DEFAULT));
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/cupboard", KeyEvent.VK_SHIFT, UI.MOD_CTRL, DEFAULT));
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                null, KeyEvent.VK_SHIFT, UI.MOD_CTRL, DEFAULT));
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_SHIFT, UI.MOD_CTRL | UI.MOD_META, DEFAULT));
    }

    @Test void reboundKeyUsesTheNewBindingInsteadOfCtrlShift() {
        InputGesture rebound = InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, 0));
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_Q, 0, rebound));
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                "gfx/terobjs/stockpile-ore", KeyEvent.VK_SHIFT, UI.MOD_CTRL, rebound));
    }

    @Test void itemHintBeatsHeldAndFirstSlot() {
        assertEquals("Ore", FullStockpilePlacementHotkey.resolveItemName("Ore", "Block", "Board"));
        assertEquals("Block", FullStockpilePlacementHotkey.resolveItemName(null, "Block", "Board"));
        assertEquals("Board", FullStockpilePlacementHotkey.resolveItemName(null, null, "Board"));
        assertEquals("Board", FullStockpilePlacementHotkey.resolveItemName("  ", "", "Board"));
    }
}
