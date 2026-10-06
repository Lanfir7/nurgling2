package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarryartzTopoTest {
    private static final QuarryartzTopo.Ground OPEN = (x, y) -> 1;

    @Test
    void sampleCellKeepsItsMeasurementAndFarCellsStayEmpty() {
        QuarryartzTopo.Result field = QuarryartzTopo.build(
                new int[] {0}, new int[] {0}, new double[] {210}, OPEN, 20, 120);
        assertEquals(210, qualityAt(field, 0, 0), 0.01);
        assertTrue(Double.isNaN(qualityAt(field, 20, 0)));
        assertTrue((argbAt(field, 0, 0) >>> 24) > 0);
        assertEquals(0, argbAt(field, 20, 0));
    }

    @Test
    void midpointSitsBetweenTwoReadings() {
        QuarryartzTopo.Result field = QuarryartzTopo.build(
                new int[] {0, 8}, new int[] {0, 0}, new double[] {100, 200}, OPEN, 20, 100);
        double mid = qualityAt(field, 4, 0);
        assertTrue(mid > 130 && mid < 170, "mid=" + mid);
    }

    @Test
    void undugTilesStayTransparent() {
        QuarryartzTopo.Result field = QuarryartzTopo.build(
                new int[] {0}, new int[] {0}, new double[] {220},
                (x, y) -> (x == 0 && y == 0) ? 1 : 0, 20, 140);
        assertTrue((argbAt(field, 0, 0) >>> 24) > 0);
        assertEquals(0, argbAt(field, 3, 0));
    }

    @Test
    void highQualityIsCyanAndLowQualityIsRed() {
        int low = QuarryartzTopo.color(150, 255);
        int high = QuarryartzTopo.color(300, 255);
        assertTrue(((low >> 16) & 255) > ((high >> 16) & 255));
        assertTrue((high & 255) > (low & 255));
    }

    @Test
    void closePeaksCollapseToTheBestRealReading() {
        QuarryartzTopo.Result field = QuarryartzTopo.build(
                new int[] {0, 3, 6, 10, 13, 16},
                new int[] {0, 0, 0, 0, 0, 0},
                new double[] {100, 140, 180, 100, 130, 160},
                OPEN, 20, 80);
        assertEquals(1, field.peakQ.length);
        assertEquals(180, field.peakQ[0]);
        assertEquals(6, field.peakX[0]);
    }

    @Test
    void caveFloorNameIsExcavatedAndRockIsNot() {
        assertTrue(QuarryartzTopo.excavatedName("gfx/tiles/mine"));
        assertTrue(QuarryartzTopo.excavatedName("gfx/tiles/deepcave"));
        assertTrue(QuarryartzTopo.excavatedName("gfx/tiles/deeptangle"));
        assertTrue(QuarryartzTopo.excavatedName("gfx/tiles/cave"));
        assertTrue(QuarryartzTopo.excavatedName("gfx/tiles/rocks/granite"));
        assertTrue(!QuarryartzTopo.excavatedName("gfx/tiles/nil"));
    }

    private static double qualityAt(QuarryartzTopo.Result field, int x, int y) {
        int gx = (x - field.x0) / field.stride;
        int gy = (y - field.y0) / field.stride;
        if (gx < 0 || gy < 0 || gx >= field.width || gy >= field.height)
            return Double.NaN;
        return field.quality[gx + gy * field.width];
    }

    private static int argbAt(QuarryartzTopo.Result field, int x, int y) {
        int gx = (x - field.x0) / field.stride;
        int gy = (y - field.y0) / field.stride;
        if (gx < 0 || gy < 0 || gx >= field.width || gy >= field.height)
            return 0;
        return field.argb[gx + gy * field.width];
    }
}
