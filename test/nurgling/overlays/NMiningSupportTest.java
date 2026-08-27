package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiningSupportTest {

    @Test
    void roundSupportsKeepExistingRadiiAndAddMonumentalColumn() {
        assertEquals(92, NMiningSupport.specFor("gfx/terobjs/map/naturalminesupport").circleRadius);
        assertEquals(100, NMiningSupport.specFor("gfx/terobjs/minesupport").circleRadius);
        assertEquals(100, NMiningSupport.specFor("gfx/terobjs/ladder").circleRadius);
        assertEquals(125, NMiningSupport.specFor("gfx/terobjs/column").circleRadius);
        assertEquals(150, NMiningSupport.specFor("gfx/terobjs/minebeam").circleRadius);
        assertEquals(330, NMiningSupport.specFor("gfx/terobjs/monumentalcolumn").circleRadius);
        assertFalse(NMiningSupport.specFor("gfx/terobjs/monumentalcolumn").isRect());
    }

    @Test
    void tunnelsAreForwardRectangles() {
        NMiningSupport.Spec timber = NMiningSupport.specFor("gfx/terobjs/timbertunnel");
        assertTrue(timber.isRect());
        assertEquals(1, timber.widthTiles);
        assertEquals(4, timber.lengthTiles);

        NMiningSupport.Spec reinf = NMiningSupport.specFor("gfx/terobjs/reinforcedtunnel");
        assertTrue(reinf.isRect());
        assertEquals(2, reinf.widthTiles);
        assertEquals(8, reinf.lengthTiles);

        NMiningSupport.Spec arch = NMiningSupport.specFor("gfx/terobjs/stonearchtunnel");
        assertTrue(arch.isRect());
        assertEquals(3, arch.widthTiles);
        assertEquals(15, arch.lengthTiles);
    }

    @Test
    void unknownGobHasNoSupportSpec() {
        assertNull(NMiningSupport.specFor("gfx/terobjs/dframe"));
        assertNull(NMiningSupport.specFor(null));
    }

    @Test
    void timberTunnelLightsOneByFourInFrontOfSupport() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        NMiningSupport.Mask mask = NMiningSupport.computeRect(rc, 0, 1, 4);
        assertEquals(4, count(mask));
        assertFalse(lit(mask, 0, 0));
        assertTrue(lit(mask, 1, 0));
        assertTrue(lit(mask, 2, 0));
        assertTrue(lit(mask, 3, 0));
        assertTrue(lit(mask, 4, 0));
        assertFalse(lit(mask, 5, 0));
        assertFalse(lit(mask, 0, 1));
    }

    @Test
    void timberTunnelNorthAndEastStayOnGobRow() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        NMiningSupport.Mask north = NMiningSupport.computeRect(rc, -Math.PI / 2, 1, 4);
        assertEquals(4, count(north));
        assertTrue(lit(north, 0, -1));
        assertTrue(lit(north, 0, -4));
        assertFalse(lit(north, 1, -1));
        assertFalse(lit(north, 0, 0));
    }

    @Test
    void timberTunnelWestAndSouthShiftOneTileNorth() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        NMiningSupport.Mask west = NMiningSupport.computeRect(rc, Math.PI, 1, 4);
        assertEquals(4, count(west));
        assertTrue(lit(west, -1, -1));
        assertTrue(lit(west, -4, -1));
        assertFalse(lit(west, -1, 0));

        NMiningSupport.Mask south = NMiningSupport.computeRect(rc, Math.PI / 2, 1, 4);
        assertEquals(4, count(south));
        assertTrue(lit(south, 0, 0));
        assertTrue(lit(south, 0, 3));
        assertFalse(lit(south, 0, 4));
        assertFalse(lit(south, 1, 0));
    }

    @Test
    void reinforcedTunnelLightsTwoByEightInFrontOfSupport() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        NMiningSupport.Mask mask = NMiningSupport.computeRect(rc, 0, 2, 8);
        assertEquals(16, count(mask));
        assertFalse(lit(mask, 0, 0));
        assertTrue(lit(mask, 1, 0));
        assertTrue(lit(mask, 1, -1));
        assertTrue(lit(mask, 8, 0));
        assertTrue(lit(mask, 8, -1));
        assertFalse(lit(mask, 0, 1));
        assertFalse(lit(mask, 9, 0));
    }

    private static int count(NMiningSupport.Mask mask) {
        int n = 0;
        for (boolean[] col : mask.data) {
            for (boolean v : col) {
                if (v) n++;
            }
        }
        return n;
    }

    private static boolean lit(NMiningSupport.Mask mask, int tx, int ty) {
        int dx = tx - mask.begin.x;
        int dy = ty - mask.begin.y;
        if (dx < 0 || dy < 0 || dx >= mask.data.length || dy >= mask.data[dx].length) {
            return false;
        }
        return mask.data[dx][dy];
    }
}
