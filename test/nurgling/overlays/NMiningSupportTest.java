package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
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
        assertEquals(5, timber.lengthTiles);

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
    void timberTunnelMatchesVanillaInEveryDirection() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        assertMask(NMiningSupport.computeRect(rc, 0, 1, 5), 5,
                new Coord(0, 0), new Coord(4, 0));
        assertMask(NMiningSupport.computeRect(rc, Math.PI / 2, 1, 5), 5,
                new Coord(0, 0), new Coord(0, 4));
        assertMask(NMiningSupport.computeRect(rc, Math.PI, 1, 5), 5,
                new Coord(-5, 0), new Coord(-1, 0));
        assertMask(NMiningSupport.computeRect(rc, -Math.PI / 2, 1, 5), 5,
                new Coord(0, -5), new Coord(0, -1));
    }

    @Test
    void reinforcedTunnelMatchesVanillaInEveryDirection() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        assertMask(NMiningSupport.computeRect(rc, 0, 2, 8), 16,
                new Coord(0, -1), new Coord(7, 0));
        assertMask(NMiningSupport.computeRect(rc, Math.PI / 2, 2, 8), 16,
                new Coord(-1, 0), new Coord(0, 7));
        assertMask(NMiningSupport.computeRect(rc, Math.PI, 2, 8), 16,
                new Coord(-8, -1), new Coord(-1, 0));
        assertMask(NMiningSupport.computeRect(rc, -Math.PI / 2, 2, 8), 16,
                new Coord(-1, -8), new Coord(0, -1));
    }

    @Test
    void stoneArchTunnelMatchesVanillaInEveryDirection() {
        Coord2d rc = Coord2d.of(5.5, 5.5);
        assertMask(NMiningSupport.computeRect(rc, 0, 3, 15), 45,
                new Coord(0, -1), new Coord(14, 1));
        assertMask(NMiningSupport.computeRect(rc, Math.PI / 2, 3, 15), 45,
                new Coord(-1, 0), new Coord(1, 14));
        assertMask(NMiningSupport.computeRect(rc, Math.PI, 3, 15), 45,
                new Coord(-15, -1), new Coord(-1, 1));
        assertMask(NMiningSupport.computeRect(rc, -Math.PI / 2, 3, 15), 45,
                new Coord(-1, -15), new Coord(1, -1));
    }

    @Test
    void constructedTunnelStartsOneTileAheadOfPlacementGhostInEveryDirection() {
        assertOverlay(newTunnelOverlay(-1, 0), new Coord(0, 0), new Coord(5, 1));
        assertOverlay(newTunnelOverlay(-1, Math.PI / 2), new Coord(0, 0), new Coord(1, 5));
        assertOverlay(newTunnelOverlay(-1, Math.PI), new Coord(-5, 0), new Coord(0, 1));
        assertOverlay(newTunnelOverlay(-1, -Math.PI / 2), new Coord(0, -5), new Coord(1, 0));

        assertOverlay(newTunnelOverlay(1, 0), new Coord(1, 0), new Coord(6, 1));
        assertOverlay(newTunnelOverlay(1, Math.PI / 2), new Coord(0, 1), new Coord(1, 6));
        assertOverlay(newTunnelOverlay(1, Math.PI), new Coord(-6, 0), new Coord(-1, 1));
        assertOverlay(newTunnelOverlay(1, -Math.PI / 2), new Coord(0, -6), new Coord(1, -1));
    }

    private static NMiningSupport newTunnelOverlay(long gobId, double angle) {
        Gob gob = new Gob(null, Coord2d.of(5.5, 5.5), gobId);
        gob.a = angle;
        return new NMiningSupport(gob, 1, 5);
    }

    private static void assertOverlay(NMiningSupport overlay, Coord expectedBegin, Coord expectedEnd) {
        assertEquals(expectedBegin, overlay.begin);
        assertEquals(expectedEnd, overlay.end);
    }

    private static void assertMask(NMiningSupport.Mask mask, int expectedCount,
                                   Coord expectedBegin, Coord expectedEnd) {
        assertEquals(expectedCount, count(mask));
        assertEquals(expectedBegin, mask.begin);
        assertEquals(expectedEnd, mask.end);
        assertTrue(lit(mask, expectedBegin.x, expectedBegin.y));
        assertTrue(lit(mask, expectedEnd.x, expectedEnd.y));
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
