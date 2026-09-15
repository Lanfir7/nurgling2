package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

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
        assertFalse(WormFarmLogic.shouldStopDig(-1));
        assertTrue(WormFarmLogic.isMetalShovel("Metal Shovel"));
        assertTrue(WormFarmLogic.isMetalShovel("gfx/invobjs/smallshovel-m"));
        assertFalse(WormFarmLogic.isMetalShovel("Wooden Shovel"));
        assertEquals(3, WormFarmLogic.minFreeSlots("Metal Shovel"));
        assertEquals(1, WormFarmLogic.minFreeSlots("Wooden Shovel"));
        assertTrue(WormFarmLogic.shouldStopDig(2, 3));
        assertFalse(WormFarmLogic.shouldStopDig(3, 3));
        assertTrue(WormFarmLogic.waitDigTickDone(false, 0, false, 2, 3));
        assertFalse(WormFarmLogic.waitDigTickDone(false, 0, false, 3, 3));
    }

    @Test
    void digWaitTickNeverNeedsNestedInventoryTasks() {
        assertFalse(WormFarmLogic.waitDigTickDone(false, 0, false, 8));
        assertTrue(WormFarmLogic.waitDigTickDone(false, 0, false, 0));
        assertFalse(WormFarmLogic.waitDigTickDone(false, 0, false, -1));
        assertTrue(WormFarmLogic.waitDigTickDone(false, 0, true, 8));
        assertTrue(WormFarmLogic.waitDigTickDone(true, 20, false, 0));
        assertFalse(WormFarmLogic.waitDigTickDone(true, 19, false, 0));
        assertTrue(WormFarmLogic.waitDigTickDone(false, 360, false, 8));
    }

    @Test
    void fillStopsWhenSoilGoneAndDoesNotAskForTake() {
        assertTrue(WormFarmLogic.shouldSkipFill(0));
        assertFalse(WormFarmLogic.shouldSkipFill(3));
        assertTrue(WormFarmLogic.shouldStopFill(true, 0, 40));
        assertTrue(WormFarmLogic.shouldStopFill(true, 5, 0));
        assertFalse(WormFarmLogic.shouldStopFill(true, 5, 12));
        assertFalse(WormFarmLogic.shouldStopFill(false, 5, 0));
        assertTrue(WormFarmLogic.isFillMode("Units of soil required: 40"));
        assertFalse(WormFarmLogic.isFillMode("Units of soil left over: 2"));
        assertTrue(WormFarmLogic.shouldClickDig(false, false));
        assertTrue(WormFarmLogic.shouldClickDig(true, true));
        assertTrue(WormFarmLogic.shouldClickDig(true, false));
        assertTrue(WormFarmLogic.fillDidNotUseSoil(false, 5, 5));
        assertTrue(WormFarmLogic.fillDidNotUseSoil(false, 5, 8));
        assertFalse(WormFarmLogic.fillDidNotUseSoil(false, 5, 3));
        assertFalse(WormFarmLogic.fillDidNotUseSoil(true, 5, 5));
    }

    @Test
    void remembersGroundPlaneTargetFromLabel() {
        assertEquals(77, WormFarmLogic.parseTargetLevel("Целевой уровень: 77"));
        assertEquals(77, WormFarmLogic.parseTargetLevel("Target level: 77"));
        assertEquals(70, WormFarmLogic.parseTargetLevel("Target level: 70-80"));
        assertEquals(-9923, WormFarmLogic.parseTargetLevel("Целевой уровень: -9923"));
        assertEquals(-9923, WormFarmLogic.parseTargetLevel("Целевой уровень: -9\u00a0923"));
        assertEquals(-9923, WormFarmLogic.parseTargetLevel("Target level: -9,923"));
        java.text.MessageFormat ru = new java.text.MessageFormat("Целевой уровень: {0}", new java.util.Locale("ru", "RU"));
        assertEquals(-9923, WormFarmLogic.parseTargetLevel(ru.format(new Object[]{-9923})));
        assertEquals(0, WormFarmLogic.parseTargetLevel("..."));
        assertTrue(WormFarmLogic.canReadTarget("Целевой уровень: 77"));
        assertFalse(WormFarmLogic.canReadTarget("..."));
        assertTrue(WormFarmLogic.isRangeTarget("Target level: 70-80"));
        assertFalse(WormFarmLogic.isRangeTarget("Целевой уровень: 77"));
        assertFalse(WormFarmLogic.isRangeTarget("Целевой уровень: -9923"));
        assertTrue(WormFarmLogic.isUsablePlaneTarget(77));
        assertFalse(WormFarmLogic.isUsablePlaneTarget(-9923));
        assertTrue(WormFarmLogic.planeShowsLowered(77, WormFarmLogic.deepHeight(77)));
        assertFalse(WormFarmLogic.planeShowsLowered(77, 77));
        assertTrue(WormFarmLogic.planeShowsRestored(77, 77));
        assertFalse(WormFarmLogic.planeShowsRestored(77, WormFarmLogic.deepHeight(77)));
    }

    @Test
    void restoreUsesRememberedPlaneNotCurrentGround() {
        float[] wz = {1, 1, 1};
        int[] dz = {1, 1, 1};
        int plane = 77;
        WormFarmLogic.applyUniform(wz, dz, WormFarmLogic.deepHeight(plane));
        assertEquals(plane - WormFarmLogic.DEEP_OFFSET, dz[0]);
        WormFarmLogic.applyUniform(wz, dz, plane);
        assertEquals(77, dz[0]);
        assertEquals(77, dz[2]);
        assertEquals(77f, wz[1]);
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
