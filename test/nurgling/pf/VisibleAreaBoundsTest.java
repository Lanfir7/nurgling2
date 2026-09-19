package nurgling.pf;

import haven.Coord2d;
import haven.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleAreaBoundsTest {
    /** Player sitting on a 100-wu cell origin so bounds are exact: [4600, 5500). */
    private static final Coord2d PLAYER = Coord2d.of(5000, 5000);

    @Test
    void smallZoneNextToPlayerIsFullyVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300));
        assertTrue(Utils.areaFullyInVisibleArea(zone, PLAYER));
    }

    @Test
    void zoneStickingPastVisionEdgeIsNotFullyVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(5400, 5400), Coord2d.of(5600, 5600));
        assertFalse(Utils.areaFullyInVisibleArea(zone, PLAYER));
    }

    @Test
    void zoneBiggerThanVisionIsNotFullyVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(4000, 4000), Coord2d.of(6000, 6000));
        assertFalse(Utils.areaFullyInVisibleArea(zone, PLAYER));
    }

    @Test
    void unknownAreaIsNotFullyVisible() {
        assertFalse(Utils.areaFullyInVisibleArea(null, PLAYER));
        assertFalse(Utils.areaFullyInVisibleArea(Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300)), null));
    }

    @Test
    void zoneTouchingExclusiveVisionEdgeStillCountsAsVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(4600, 4600), Coord2d.of(5500, 5500));
        assertTrue(Utils.areaFullyInVisibleArea(zone, PLAYER));
    }

    @Test
    void noWalkWhenZoneAlreadyFullyVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300));
        assertEquals(null, Utils.walkTargetToSeeWholeArea(zone, PLAYER));
    }

    @Test
    void walkShiftsVisionUntilEveryZoneCornerIsVisible() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(5400, 5400), Coord2d.of(5600, 5600));
        Coord2d target = Utils.walkTargetToSeeWholeArea(zone, PLAYER);
        assertNotNull(target);
        assertTrue(Utils.areaFullyInVisibleArea(zone, target), target.toString());
    }

    @Test
    void oversizedZoneWalksToNearestCorner() {
        Pair<Coord2d, Coord2d> zone = Pair.of(Coord2d.of(4000, 4000), Coord2d.of(6000, 6000));
        Coord2d target = Utils.walkTargetToSeeWholeArea(zone, PLAYER);
        assertEquals(Coord2d.of(4000, 4000), target);
    }

    @Test
    void unknownAreaHasNoWalkTarget() {
        assertEquals(null, Utils.walkTargetToSeeWholeArea(null, PLAYER));
        assertEquals(null, Utils.walkTargetToSeeWholeArea(
                Pair.of(Coord2d.of(5400, 5400), Coord2d.of(5600, 5600)), null));
    }
}
