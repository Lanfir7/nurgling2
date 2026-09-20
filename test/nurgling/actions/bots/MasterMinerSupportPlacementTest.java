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
    void choosesTheShortSideNicheWithoutUsingPlayerHeading() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 8));
        open.add(new Coord(10, 7)); // long northbound tunnel
        open.add(new Coord(9, 10)); // short west niche
        assertEquals(new Coord(9, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));

        open.clear();
        open.add(new Coord(10, 9));
        open.add(new Coord(10, 8));
        open.add(new Coord(10, 7)); // long northbound tunnel
        open.add(new Coord(11, 10)); // mirrored east niche
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void usesStableCardinalOrderForEquallyShortNiches() {
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
    void occupiedCorridorTileDoesNotTurnTheCorridorIntoANiche() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(9, 10));
        open.add(new Coord(8, 10));
        open.add(new Coord(11, 10));
        Set<Coord> occupied = new HashSet<>();
        occupied.add(new Coord(8, 10));
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN,
                open::contains, tile -> !occupied.contains(tile)));

        open.remove(new Coord(11, 10));
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN,
                open::contains, tile -> !occupied.contains(tile)));
    }

    @Test
    void rejectsTurningTunnelBranchInFavorOfAnIsolatedNiche() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(9, 10));
        open.add(new Coord(9, 9));
        open.add(new Coord(9, 8)); // west branch turns north into a tunnel
        open.add(new Coord(11, 10)); // isolated east niche
        assertEquals(new Coord(11, 10), MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void returnsNullWhenOnlyATurningCorridorIsOpen() {
        Set<Coord> open = new HashSet<>();
        open.add(new Coord(9, 10));
        open.add(new Coord(9, 9));
        open.add(new Coord(9, 8));
        assertNull(MasterMinerSupportPlacement.chooseAdjacent(ORIGIN, open::contains));
    }

    @Test
    void onlyMinedUndergroundFloorAcceptsConstruction() {
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/deepcave"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/deeptangle/grass"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/cavein"));
        assertTrue(MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/caveout"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/cave"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName("gfx/tiles/rocks/granite"));
        assertTrue(!MasterMinerSupportPlacement.isOpenCaveTileName(null));
    }
}
