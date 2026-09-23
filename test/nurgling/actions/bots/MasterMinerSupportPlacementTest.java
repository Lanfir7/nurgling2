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
    void choosesAnAdjacentMinedTileInAConnectedCorridor() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 8));
        open.add(new Coord(9, 9)); // connected corridor branch
        assertEquals(new Coord(10, 9), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void usesStableCardinalOrderForOpenTiles() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(10, 9));
        open.add(new Coord(9, 10));
        assertEquals(new Coord(10, 9), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void returnsNullWithoutAnOpenRay() {
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, tile -> false));
    }

    @Test
    void skipsOccupiedAndUnminedAdjacentTiles() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(10, 9));
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
    void choosesBesideTheMinerAfterReturningNearTheStoredOrigin() {
        Coord storedOriginTile = new Coord(10, 10);
        Coord returnedMinerTile = new Coord(11, 10);
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(11, 9));

        assertEquals(new Coord(11, 9), MasterMinerSupportPlacement.chooseAdjacent(
                storedOriginTile, returnedMinerTile, open::contains));
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
