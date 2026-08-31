package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import nurgling.actions.PathFinder;
import nurgling.pf.NHitBoxD;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
