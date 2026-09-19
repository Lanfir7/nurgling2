package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.pf.NHitBoxD;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeContainersInAreaTest {
    @Test
    void pilesAreVisitedNearestToThePlayerFirst() {
        Gob far = new Gob(null, Coord2d.of(30, 0), 30);
        Gob nearest = new Gob(null, Coord2d.of(3, 0), 3);
        Gob middle = new Gob(null, Coord2d.of(12, 0), 12);
        ArrayList<Gob> piles = new ArrayList<>(Arrays.asList(far, nearest, middle));

        FreeContainersInArea.orderPilesNearestFirst(piles, Coord2d.z);

        assertEquals(Arrays.asList(nearest, middle, far), piles);
    }

    @Test
    void failedApproachDoesNotTryToOpenThePile() throws InterruptedException {
        boolean[] opened = {false};

        boolean success = FreeContainersInArea.approachThenOpen(
                () -> false,
                () -> {
                    opened[0] = true;
                    return true;
                });

        assertFalse(success);
        assertFalse(opened[0]);
    }

    @Test
    void ignoredFirstOpenIsRetriedAfterReapproachingTheSamePile() throws InterruptedException {
        int[] approaches = {0};
        int[] opens = {0};

        boolean success = FreeContainersInArea.approachThenOpen(
                () -> {
                    approaches[0]++;
                    return true;
                },
                () -> ++opens[0] == 2);

        assertTrue(success);
        assertEquals(2, approaches[0]);
        assertEquals(2, opens[0]);
    }

    @Test
    void nearestSafeApproachSideWinsOverAUsuallyClearerFarSide() {
        Coord2d player = Coord2d.of(0, 10);
        NHitBoxD playerHitBox = new NHitBoxD(
                Coord2d.of(-1, -1), Coord2d.of(1, 1), player);
        NHitBoxD target = new NHitBoxD(
                Coord2d.of(-2.5, -2.5), Coord2d.of(2.5, 2.5), Coord2d.z);
        List<NHitBoxD> occupied = List.of(
                new NHitBoxD(Coord2d.of(-8, 5.2), Coord2d.of(8, 6)));

        ArrayList<Coord2d> candidates = FreeContainersInArea.safeApproachCandidates(
                player, playerHitBox, target, occupied, 0.5);

        assertEquals(Coord2d.of(0, 4), candidates.get(0));
    }

    @Test
    void stockpileApproachReplansOnlyOnceBeforeTryingAnotherSide() {
        assertTrue(FreeContainersInArea.shouldReplanPileApproach(0));
        assertFalse(FreeContainersInArea.shouldReplanPileApproach(1));
    }

    @Test
    void pickupResumesOnlyAfterInventoryWasActuallyFreed() {
        assertFalse(FreeContainersInArea.canResumePickup(Results.FAIL(), 10));
        assertFalse(FreeContainersInArea.canResumePickup(Results.SUCCESS(), 0));
        assertTrue(FreeContainersInArea.canResumePickup(Results.SUCCESS(), 1));
    }

    @Test
    void emptySourceIsNotDoneWhileTheSelectedZoneIsOutOfView() {
        assertFalse(FreeContainersInArea.treatSourceAsCleared(false, false));
        assertTrue(FreeContainersInArea.treatSourceAsCleared(false, true));
        assertFalse(FreeContainersInArea.treatSourceAsCleared(true, true));
        assertFalse(FreeContainersInArea.treatSourceAsCleared(true, false));
    }

    @Test
    void dumpTripMustWalkBackWhenSelectedZoneLeftVision() {
        Coord2d player = Coord2d.of(5000, 5000);
        Pair<Coord2d, Coord2d> farSource = Pair.of(Coord2d.of(100, 100), Coord2d.of(200, 200));
        Pair<Coord2d, Coord2d> nearby = Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300));

        assertTrue(FreeContainersInArea.shouldReloadSourceGobs(farSource, player));
        assertFalse(FreeContainersInArea.shouldReloadSourceGobs(nearby, player));
    }

    @Test
    void nextBatchReloadsSelectedZoneUntilPilesAreInVision() throws Exception {
        String src = new String(Files.readAllBytes(
                Paths.get("src/nurgling/actions/bots/FreeContainersInArea.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("walkTargetToSeeWholeArea"), src);
        assertTrue(src.contains("navigateToAreaIfNeeded(workAreaId, true)"), src);
        assertTrue(src.contains("treatSourceAsCleared"), src);
    }

    @Test
    void stockpileApproachUsesOnlyTheSideWithRoomForTheCharacter() throws Exception {
        Coord2d player = Coord2d.of(10, 10);
        NHitBoxD playerHitBox = new NHitBoxD(
                Coord2d.of(-1, -1), Coord2d.of(1, 1), player);
        NHitBoxD target = new NHitBoxD(
                Coord2d.of(-2.5, -2.5), Coord2d.of(2.5, 2.5), Coord2d.z);
        List<NHitBoxD> occupiedSides = Arrays.asList(
                new NHitBoxD(Coord2d.of(-7, -2), Coord2d.of(-3, 2)),
                new NHitBoxD(Coord2d.of(3, -2), Coord2d.of(7, 2)),
                new NHitBoxD(Coord2d.of(-2, -7), Coord2d.of(2, -3)));

        ArrayList<Coord2d> candidates = FreeContainersInArea.safeApproachCandidates(
                player, playerHitBox, target, occupiedSides, 0.5);

        assertEquals(List.of(Coord2d.of(0, 4)), candidates);
    }

    @Test
    void safePointsBecomeTargetAwareApproachSidesInTheSameOrder() {
        List<PathFinder.Mode> modes = FreeContainersInArea.approachModes(
                Coord2d.z, List.of(
                        Coord2d.of(0, 4),
                        Coord2d.of(5, 0),
                        Coord2d.of(-6, 0)));

        assertEquals(List.of(
                PathFinder.Mode.Y_MAX,
                PathFinder.Mode.X_MAX,
                PathFinder.Mode.X_MIN), modes);
    }
}
