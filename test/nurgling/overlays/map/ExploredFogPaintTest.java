package nurgling.overlays.map;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredFogPaintTest {
    @Test
    void zoomedOutCellStartsAtItsOwnBaseGrids() {
        Coord near = Coord.z;
        Coord next = Coord.of(1, 0);

        assertEquals(50, MinimapExploredAreaRenderer.coveragePixel(1, near, 100, 0));
        assertEquals(0, MinimapExploredAreaRenderer.coveragePixel(1, next, 200, 0));
        assertEquals(-1, MinimapExploredAreaRenderer.coveragePixel(1, next, 100, 0));
        assertEquals(-1, MinimapExploredAreaRenderer.coveragePixel(1, near, -1, 0));
    }

    @Test
    void closeZoomPaintsTheExploredTile() {
        boolean[] mask = new boolean[100 * 100];
        mask[4 + 7 * 100] = true;
        boolean[] dest = new boolean[100 * 100];

        assertTrue(MinimapExploredAreaRenderer.paintMask(dest, 100, 0, Coord.of(2, 3), 2, 3, mask));
        assertTrue(dest[4 + 7 * 100]);
        assertFalse(dest[0]);
    }

    @Test
    void farZoomUsesTheBlockCenter() {
        boolean[] mask = new boolean[100 * 100];
        mask[4 + 4 * 100] = true;
        boolean[] dest = new boolean[100 * 100];

        assertTrue(MinimapExploredAreaRenderer.paintMask(dest, 100, 3, Coord.z, 0, 0, mask));
        assertTrue(dest[0]);

        boolean[] missed = new boolean[100 * 100];
        missed[0] = true;
        boolean[] empty = new boolean[100 * 100];
        assertFalse(MinimapExploredAreaRenderer.paintMask(empty, 100, 3, Coord.z, 0, 0, missed));
    }

    @Test
    void neighbouringCellsShareAnEdge() {
        Coord camera = Coord.z;
        Coord hsz = Coord.of(400, 300);
        float scaleFactor = 2f;
        Coord[] left = MinimapExploredAreaRenderer.cellBounds(Coord.z, scaleFactor, camera, hsz);
        Coord[] right = MinimapExploredAreaRenderer.cellBounds(Coord.of(1, 0), scaleFactor, camera, hsz);

        assertTrue(right[0].x - left[1].x <= 0);
        assertTrue(left[1].x - right[0].x <= 1);
    }
}
