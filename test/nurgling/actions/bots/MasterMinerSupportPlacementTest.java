package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerSupportPlacementTest {
    private static final Coord ORIGIN = new Coord(10, 10);

    @Test
    void choosesSideBranchInsteadOfEitherCorridorDirectionRegardlessOfButtonTile() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(9, 10));
        open.add(new Coord(11, 10));
        open.add(new Coord(10, 9)); // dead-end side branch
        assertEquals(new Coord(10, 9), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));

        Coord elsewhere = new Coord(20, 20);
        Set<Coord> shiftedOpen = new HashSet<>();
        shiftedOpen.add(elsewhere);
        shiftedOpen.add(new Coord(19, 20));
        shiftedOpen.add(new Coord(21, 20));
        shiftedOpen.add(new Coord(20, 19));
        assertEquals(new Coord(20, 19), MasterMinerSupportPlacement.chooseAdjacent(
                elsewhere, elsewhere, shiftedOpen::contains));
    }

    @Test
    void usesStableCardinalOrderForOpenTiles() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        open.add(new Coord(9, 10));
        assertEquals(new Coord(9, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void returnsNullWithoutAnOpenRay() {
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, tile -> false));
    }

    @Test
    void skipsOccupiedAndUnminedAdjacentTiles() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        open.add(new Coord(11, 10));
        // The west tile is deliberately absent: it remains unmined.
        Set<Coord> occupied = new HashSet<>();
        occupied.add(new Coord(10, 9));
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN,
                open::contains, tile -> !occupied.contains(tile)));

        open.remove(new Coord(11, 10));
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN,
                open::contains, tile -> !occupied.contains(tile)));
    }

    @Test
    void doesNotTreatTheArrivalTileOrMainCorridorAsASideBranch() {
        Coord storedOriginTile = new Coord(10, 10);
        Coord returnedMinerTile = new Coord(11, 10);
        Set<Coord> open = new HashSet<>();
        open.add(storedOriginTile);
        open.add(returnedMinerTile);
        open.add(new Coord(11, 9));
        open.add(new Coord(11, 11));

        assertNull(MasterMinerSupportPlacement.chooseAdjacent(
                storedOriginTile, returnedMinerTile, open::contains));
        assertEquals(new Coord(12, 10), MasterMinerSupportPlacement.chooseAdjacent(
                storedOriginTile, returnedMinerTile, open::contains,
                tile -> tile.equals(new Coord(12, 10)), tile -> true));
    }

    @Test
    void acceptsAnOpenSideTileEvenWhenItsBranchContinues() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        open.add(new Coord(9, 10));
        open.add(new Coord(9, 9)); // the side branch continues beside the main corridor
        assertEquals(new Coord(9, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void choosesMineableSideWallWhenNoOpenDeadEndExists() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(9, 10));
        mineable.add(new Coord(11, 10));

        assertEquals(new Coord(9, 10), MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, mineable::contains, tile -> true));
        open.add(new Coord(11, 10));
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, mineable::contains, tile -> true));
    }

    @Test
    void choosesPerpendicularWallAtEndOfStraightCorridor() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(10, 11)); // forward wall remains in the main corridor
        mineable.add(new Coord(9, 10));

        assertEquals(new Coord(9, 10), MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, mineable::contains, tile -> true));
    }

    @Test
    void choosesAdjacentEastFloorAtCorridorEndDespiteContinuingSideBranchAndUnknownForwardTile() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        for (int y = 5; y < 10; y++) open.add(new Coord(10, y));
        open.add(new Coord(11, 10));
        open.add(new Coord(12, 10));
        open.add(new Coord(11, 11));
        Set<Coord> known = new HashSet<>();
        for (int x = 8; x <= 13; x++) {
            for (int y = 5; y <= 12; y++) known.add(new Coord(x, y));
        }
        known.remove(new Coord(10, 11)); // map edge ahead of the miner
        Coord side = new Coord(11, 10);

        assertEquals(side, MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, new Coord(10, 14), open::contains, known::contains,
                tile -> false, tile -> true));
        assertTrue(MasterMinerSupportPlacement.isSideBranch(ORIGIN, side,
                open::contains, known::contains, false));
        assertTrue(!MasterMinerSupportPlacement.isSideBranch(ORIGIN, side,
                open::contains, known::contains, true));

        known.remove(new Coord(12, 10)); // unknown neighbor of the placement tile
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, known::contains,
                tile -> false, tile -> true));
    }

    @Test
    void rejectsEqualLengthPassagesAtAnAmbiguousBend() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        for (int step = 1; step <= 5; step++) {
            open.add(new Coord(10, 10 - step));
            open.add(new Coord(10 + step, 10));
        }
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void neverMinesAConnectedBranchOrChoosesAnAmbiguousIntersection() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        open.add(new Coord(9, 9));
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(9, 10));
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, mineable::contains, tile -> true));

        open.add(new Coord(9, 10));
        open.add(new Coord(11, 10));
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, ORIGIN, open::contains, mineable::contains, tile -> true));
    }

    @Test
    void usesButtonCorridorWhenMinerReturnsInAnExistingSideBranch() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        open.add(new Coord(11, 10));
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(9, 10));

        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, new Coord(11, 10), open::contains, mineable::contains, tile -> true));
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(
                ORIGIN, new Coord(20, 20), open::contains, mineable::contains, tile -> true));
    }

    @Test
    void rechecksThatMinedTileIsStillADeadEndBeforePlacement() {
        Set<Coord> open = new HashSet<>();
        open.add(ORIGIN);
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 11));
        Coord side = new Coord(9, 10);
        open.add(side);
        assertTrue(MasterMinerSupportPlacement.isSideBranch(ORIGIN, side, open::contains));
        open.add(new Coord(9, 9));
        assertTrue(!MasterMinerSupportPlacement.isSideBranch(ORIGIN, side, open::contains));
        assertTrue(!MasterMinerSupportPlacement.isSideBranch(ORIGIN,
                new Coord(10, 9), open::contains));
    }

    @Test
    void identifiesOnlyMineableWallResourcesForExcavation() {
        assertTrue(TunnelingBot.isMineableTileName("gfx/tiles/cave"));
        assertTrue(TunnelingBot.isMineableTileName("gfx/tiles/rocks/granite"));
        assertTrue(!TunnelingBot.isMineableTileName("gfx/tiles/mine"));
        assertTrue(!TunnelingBot.isMineableTileName(null));
    }

    @Test
    void onlyMinedUndergroundFloorAcceptsConstruction() {
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/mine"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/deepcave"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/deeptangle/grass"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/cavein"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/caveout"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/cave"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/rocks/granite"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName(null));
    }

}
