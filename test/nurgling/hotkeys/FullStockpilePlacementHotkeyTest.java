package nurgling.hotkeys;

import haven.KeyMatch;
import haven.UI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FullStockpilePlacementHotkeyTest {
    private static final InputGesture SHIFT_RMB = InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S);

    @Test void heldShiftRmbOnGroundLaunches() {
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(true, true, 3, UI.MOD_SHIFT, SHIFT_RMB));
    }

    @Test void plainRmbOnGroundKeepsStockpileGhost() {
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(true, true, 3, 0, SHIFT_RMB));
    }

    @Test void shiftRmbOnGobKeepsInteractOne() {
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(true, false, 3, UI.MOD_SHIFT, SHIFT_RMB));
    }

    @Test void emptyHandsDoNotLaunch() {
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(false, true, 3, UI.MOD_SHIFT, SHIFT_RMB));
    }

    @Test void ctrlShiftRmbDoesNotLaunch() {
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(
                true, true, 3, UI.MOD_CTRL | UI.MOD_SHIFT, SHIFT_RMB));
    }

    @Test void reboundHeldClickUsesTheNewBinding() {
        InputGesture rebound = InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.M);
        assertTrue(FullStockpilePlacementHotkey.shouldLaunch(true, true, 3, UI.MOD_META, rebound));
        assertFalse(FullStockpilePlacementHotkey.shouldLaunch(true, true, 3, UI.MOD_SHIFT, rebound));
    }

    @Test void nullClickDataIsGround() {
        assertTrue(FullStockpilePlacementHotkey.isGroundTarget(null));
    }

    @Test void heldMousePassesThroughDuringAreaSelect() {
        assertTrue(FullStockpilePlacementHotkey.passThroughHeldMouse(true, false));
        assertTrue(FullStockpilePlacementHotkey.passThroughHeldMouse(false, true));
        assertFalse(FullStockpilePlacementHotkey.passThroughHeldMouse(false, false));
    }

    @Test void itemHintBeatsHeldAndFirstSlot() {
        assertEquals("Ore", FullStockpilePlacementHotkey.resolveItemName("Ore", "Block", "Board"));
        assertEquals("Block", FullStockpilePlacementHotkey.resolveItemName(null, "Block", "Board"));
        assertEquals("Board", FullStockpilePlacementHotkey.resolveItemName(null, null, "Board"));
        assertEquals("Board", FullStockpilePlacementHotkey.resolveItemName("  ", "", "Board"));
    }
}
