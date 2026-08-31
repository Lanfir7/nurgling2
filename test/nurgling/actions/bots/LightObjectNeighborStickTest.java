package nurgling.actions.bots;

import haven.Coord2d;
import haven.MCache;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightObjectNeighborStickTest {
    private static final Coord2d PLAYER = Coord2d.of(0, 0);
    private static final double RADIUS = 5 * MCache.tilesz.x;
    private static final String KILN = "gfx/terobjs/kiln";

    @Test
    void radiusIsFiveTiles() {
        assertEquals(5 * MCache.tilesz.x, LightObject.NEIGHBOR_STICK_RADIUS, 1e-9);
        assertEquals(55.0, LightObject.NEIGHBOR_STICK_RADIUS, 1e-9);
    }

    @Test
    void picksLitKilnInsideFiveTiles() {
        LightObject.FireSourceProbe inside = kiln(2, 4 * MCache.tilesz.x, 1);
        LightObject.FireSourceProbe picked = LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Collections.singletonList(inside));
        assertNotNull(picked);
        assertEquals(2L, picked.id);
    }

    @Test
    void ignoresLitKilnOutsideFiveTiles() {
        LightObject.FireSourceProbe outside = kiln(2, 5.1 * MCache.tilesz.x, 1);
        assertNull(LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Collections.singletonList(outside)));
    }

    @Test
    void includesKilnExactlyAtFiveTiles() {
        LightObject.FireSourceProbe edge = kiln(2, RADIUS, 1);
        LightObject.FireSourceProbe picked = LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Collections.singletonList(edge));
        assertNotNull(picked);
        assertEquals(2L, picked.id);
    }

    @Test
    void excludesTheTargetGobEvenWhenLitAndClose() {
        LightObject.FireSourceProbe target = kiln(7, MCache.tilesz.x, 1);
        assertNull(LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 7L, Collections.singletonList(target)));
    }

    @Test
    void requiresFireBit() {
        LightObject.FireSourceProbe cold = kiln(2, MCache.tilesz.x, 0);
        assertNull(LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Collections.singletonList(cold)));
    }

    @Test
    void kilnFireBitIsOne() {
        assertEquals(1, LightObject.fireFlag(KILN));
        LightObject.FireSourceProbe wrongBit = kiln(2, MCache.tilesz.x, 2);
        assertNull(LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Collections.singletonList(wrongBit)));
    }

    @Test
    void prefersClosestLitSourceInRadius() {
        LightObject.FireSourceProbe far = kiln(3, 4 * MCache.tilesz.x, 1);
        LightObject.FireSourceProbe near = kiln(4, 2 * MCache.tilesz.x, 1);
        LightObject.FireSourceProbe picked = LightObject.pickClosestLitFireSource(
                PLAYER, RADIUS, 1L, Arrays.asList(far, near));
        assertNotNull(picked);
        assertEquals(4L, picked.id);
    }

    @Test
    void ctrlAltItemactIsModCtrlPlusMeta() {
        assertEquals(6, UI.MOD_CTRL | UI.MOD_META);
        assertEquals(UI.MOD_CTRL | UI.MOD_META, LightObject.NEIGHBOR_STICK_MODFLAGS);
    }

    @Test
    void neighborStickWaitsUntilProgressHasStartedAndCleared() {
        assertTrue(LightObject.neighborStickReady(true, false, true));
        assertFalse(LightObject.neighborStickReady(true, true, true));
        assertFalse(LightObject.neighborStickReady(true, false, false));
        assertFalse(LightObject.neighborStickReady(false, false, true));
    }

    @Test
    void retriesNeighborStickWhileUnlitSourceAndBranchRemain() {
        assertTrue(LightObject.shouldRetryNeighborStick(true, true, false));
    }

    @Test
    void doesNotFallThroughToFirebrandWhileNeighborSourceAndBranchRemain() {
        // Neighbor-stick keeps the gob until retry is false (no source/branch or already lit)
        // or the no-progress bound trips while a source and branch still exist.
        assertTrue(LightObject.shouldRetryNeighborStick(true, true, false));
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(0));
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(1));
        assertTrue(LightObject.shouldGiveUpNeighborStickNoProgress(
                LightObject.NEIGHBOR_STICK_MAX_NO_PROGRESS));
    }

    @Test
    void stopsNeighborStickWhenTargetIsLit() {
        assertFalse(LightObject.shouldRetryNeighborStick(true, true, true));
    }

    @Test
    void stopsNeighborStickWhenSourceIsGone() {
        assertFalse(LightObject.shouldRetryNeighborStick(false, true, false));
        assertFalse(LightObject.shouldRetryNeighborStick(false, false, false));
    }

    @Test
    void stopsNeighborStickWhenNoBranch() {
        assertFalse(LightObject.shouldRetryNeighborStick(true, false, false));
    }

    @Test
    void firstFailedAttemptStillRetriesIfSourceAndBranchRemain() {
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(0));
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(1));
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(
                LightObject.NEIGHBOR_STICK_MAX_NO_PROGRESS - 1));
        assertTrue(LightObject.shouldRetryNeighborStick(true, true, false));
    }

    @Test
    void givesUpNeighborStickAfterBoundedNoProgress() {
        assertTrue(LightObject.shouldGiveUpNeighborStickNoProgress(
                LightObject.NEIGHBOR_STICK_MAX_NO_PROGRESS));
    }

    @Test
    void nextNoProgressIncrementsOnlyWhileSourceRemains() {
        assertEquals(1, LightObject.nextNeighborStickNoProgress(0, false, true));
        assertEquals(2, LightObject.nextNeighborStickNoProgress(1, false, true));
        assertEquals(1, LightObject.nextNeighborStickNoProgress(1, false, false));
        assertEquals(0, LightObject.nextNeighborStickNoProgress(0, false, false));
    }

    @Test
    void nextNoProgressResetsWhenStickLights() {
        assertEquals(0, LightObject.nextNeighborStickNoProgress(2, true, true));
        assertEquals(0, LightObject.nextNeighborStickNoProgress(2, true, false));
    }

    @Test
    void neighborSearchUsesTargetOriginSoOppositeNeighborIsStillFound() {
        Coord2d target = Coord2d.of(0, 0);
        Coord2d playerAtDeadSource = Coord2d.of(RADIUS, 0);
        LightObject.FireSourceProbe opposite = kiln(3, -4 * MCache.tilesz.x, 1);
        assertNotNull(LightObject.pickClosestLitFireSource(
                target, RADIUS, 1L, Collections.singletonList(opposite)));
        assertNull(LightObject.pickClosestLitFireSource(
                playerAtDeadSource, RADIUS, 1L, Collections.singletonList(opposite)));
    }

    @Test
    void readyStickThatDoesNotLightTargetStillRetriesWhileSourceAndBranchRemain() {
        assertTrue(LightObject.shouldRetryNeighborStick(true, true, false));
        assertFalse(LightObject.shouldGiveUpNeighborStickNoProgress(0));
        assertFalse(LightObject.shouldExitNeighborStickAfterAttempt(false, true, true, 0));
    }

    @Test
    void exitNeighborStickAfterAttemptWhenLitOrNoSourceOrNoBranchOrGiveUp() {
        assertTrue(LightObject.shouldExitNeighborStickAfterAttempt(true, true, true, 0));
        assertTrue(LightObject.shouldExitNeighborStickAfterAttempt(false, false, true, 0));
        assertTrue(LightObject.shouldExitNeighborStickAfterAttempt(false, true, false, 0));
        assertTrue(LightObject.shouldExitNeighborStickAfterAttempt(
                false, true, true, LightObject.NEIGHBOR_STICK_MAX_NO_PROGRESS));
    }

    private static LightObject.FireSourceProbe kiln(long id, double x, int attr) {
        return new LightObject.FireSourceProbe(id, KILN, attr, Coord2d.of(x, 0));
    }
}
