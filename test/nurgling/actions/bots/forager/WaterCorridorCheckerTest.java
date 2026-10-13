package nurgling.actions.bots.forager;

import haven.Coord;
import haven.Coord2d;
import haven.MCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterCorridorCheckerTest {
    @Test
    void deepWaterBetweenBanksBlocksTheWalk() {
        assertTrue(WaterCorridorChecker.corridorBlocked(center(0, 0), center(6, 0), tile ->
                tile.x == 3 ? "gfx/tiles/deep" : "gfx/tiles/grass"));
    }

    @Test
    void shallowWaterStaysCrossable() {
        assertFalse(WaterCorridorChecker.corridorBlocked(center(0, 0), center(6, 0), tile ->
                tile.x == 3 ? "gfx/tiles/water" : "gfx/tiles/grass"));
    }

    @Test
    void caveFloorNamesAreNotDeepWater() {
        assertFalse(WaterCorridorChecker.isImpassableWater("gfx/tiles/deeptangle"));
        assertFalse(WaterCorridorChecker.isImpassableWater("gfx/tiles/deepcave"));
        assertTrue(WaterCorridorChecker.isImpassableWater("gfx/tiles/odeep"));
    }

    private static Coord2d center(int tx, int ty) {
        return new Coord(tx, ty).mul(MCache.tilesz).add(MCache.tilehsz);
    }
}
