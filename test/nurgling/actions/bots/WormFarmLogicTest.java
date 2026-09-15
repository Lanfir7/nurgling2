package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WormFarmLogicTest {
    @Test
    void keepsOnlySoilWormsAndTubers() {
        assertTrue(WormFarmLogic.isKeepItem("Soil"));
        assertTrue(WormFarmLogic.isKeepItem("Earthworm"));
        assertTrue(WormFarmLogic.isKeepItem("Odd Tuber"));
        assertFalse(WormFarmLogic.isKeepItem("Stone"));
        assertFalse(WormFarmLogic.isKeepItem("Mulch"));
        assertFalse(WormFarmLogic.isKeepItem(null));
        assertTrue(WormFarmLogic.shouldDropJunk("Stone"));
        assertFalse(WormFarmLogic.shouldDropJunk("Soil"));
    }

    @Test
    void digStopsOnNoFreeSoilSlotNotOnDeepTarget() {
        assertTrue(WormFarmLogic.shouldStopDig(0));
        assertFalse(WormFarmLogic.shouldStopDig(1));
        assertFalse(WormFarmLogic.shouldStopDig(8));
    }

    @Test
    void fillStopsWhenSoilGoneAndDoesNotAskForTake() {
        assertTrue(WormFarmLogic.shouldSkipFill(0));
        assertFalse(WormFarmLogic.shouldSkipFill(3));
        assertTrue(WormFarmLogic.shouldStopFill(0, 40));
        assertTrue(WormFarmLogic.shouldStopFill(5, 0));
        assertFalse(WormFarmLogic.shouldStopFill(5, 12));
    }

    @Test
    void restoreUsesStartSnapshotNotCurrentGround() {
        int[] original = {100, 110, 90};
        float[] wz = {1, 1, 1};
        int[] dz = {1, 1, 1};
        int[] snapshot = WormFarmLogic.copyHeights(original);
        WormFarmLogic.applyUniform(wz, dz, WormFarmLogic.deepHeight(90));
        assertEquals(-9910, dz[0]);
        assertEquals(-9910, dz[2]);
        WormFarmLogic.restoreHeights(wz, dz, snapshot);
        assertArrayEquals(original, dz);
        assertEquals(100f, wz[0]);
        assertEquals(110f, wz[1]);
        assertEquals(90f, wz[2]);
        assertArrayEquals(original, snapshot);
    }

    @Test
    void putErrorOnlyForWormsAndTubers() {
        assertNull(WormFarmLogic.putError(0, 0));
        assertEquals("Worm Farm: no earthworm PUT area available", WormFarmLogic.putError(2, 0));
        assertEquals("Worm Farm: no Odd Tuber PUT area available", WormFarmLogic.putError(0, 1));
    }

    @Test
    void restoreNeedsMatchLevelerThresholds() {
        assertTrue(WormFarmLogic.shouldRestoreNeeds(0.24, 0.9));
        assertTrue(WormFarmLogic.shouldRestoreNeeds(0.9, 0.29));
        assertFalse(WormFarmLogic.shouldRestoreNeeds(0.25, 0.3));
    }
}
