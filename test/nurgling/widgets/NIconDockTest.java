package nurgling.widgets;

import haven.Coord;
import haven.PUtils;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NIconDockTest {

    @Test
    void hiddenUntilTheCursorComesNear() {
        NIconDock dock = new NIconDock();
        Coord sz = new Coord(200, 200);

        for(int i = 0; i < 100; i++)
            dock.track(new Coord(1000, 1000), sz, 0.05);
        assertTrue(dock.hidden(), "icons must stay hidden while the cursor is elsewhere");

        for(int i = 0; i < 100; i++)
            dock.track(new Coord(100, 100), sz, 0.05);
        assertFalse(dock.hidden(), "icons must appear once the cursor is over the panel");
    }

    @Test
    void revealRampsOverTheFadeDistance() {
        assertEquals(1.0, NIconDock.reveal(0));
        assertEquals(0.0, NIconDock.reveal(NIconDock.FADE));
        assertEquals(0.0, NIconDock.reveal(NIconDock.FADE * 4));
        assertTrue(NIconDock.reveal(NIconDock.FADE / 2) > 0);
        assertTrue(NIconDock.reveal(NIconDock.FADE / 4) > NIconDock.reveal(NIconDock.FADE / 2));
    }

    @Test
    void nearerIconsGrowMore() {
        assertEquals(1.0 + NIconDock.GROW, NIconDock.scale(0));
        assertEquals(1.0, NIconDock.scale(NIconDock.REACH));
        assertEquals(1.0, NIconDock.scale(NIconDock.REACH * 2));
        double near = NIconDock.scale(NIconDock.REACH * 0.25);
        double mid = NIconDock.scale(NIconDock.REACH * 0.5);
        double far = NIconDock.scale(NIconDock.REACH * 0.75);
        assertTrue(near > mid && mid > far && far > 1.0, "the size must fall off in steps");
    }

    @Test
    void grownIconIsTheBrightest() {
        NIconDock dock = new NIconDock();
        Coord sz = new Coord(200, 200);
        for(int i = 0; i < 100; i++)
            dock.track(new Coord(100, 100), sz, 0.05);
        assertTrue(dock.alpha(1.0 + NIconDock.GROW) > dock.alpha(1.0));
        assertTrue(dock.alpha(1.0) > 0, "a revealed icon is dim, not invisible");
        assertTrue(dock.alpha(1.0 + NIconDock.GROW) <= 255);
    }

    @Test
    void insideThePanelCountsAsZeroDistance() {
        Coord sz = new Coord(200, 100);
        assertEquals(0.0, NIconDock.rectdist(new Coord(10, 10), sz));
        assertEquals(0.0, NIconDock.rectdist(new Coord(200, 100), sz));
        assertEquals(10.0, NIconDock.rectdist(new Coord(210, 50), sz));
        assertEquals(10.0, NIconDock.rectdist(new Coord(50, -10), sz));
    }

    @Test
    void overlayIconsGrowUpAndRightFromTheirLeftEdge() {
        Coord mid = new Coord(50, 100);
        Coord orig = new Coord(20, 20);
        Coord grown = new Coord(38, 38);
        Coord ul = NIconDock.grownUl(mid, orig, grown, false, false);
        int origLeft = mid.x - (orig.x / 2);
        assertEquals(origLeft, ul.x);
        assertEquals(mid.y + (orig.y / 2) - grown.y, ul.y);
        assertTrue(ul.x + grown.x > origLeft + orig.x, "extra width must go right, not left");
    }

    @Test
    void grownClickHitsPixelsOutsideTheLayoutRect() {
        Coord mid = new Coord(50, 100);
        Coord orig = new Coord(20, 20);
        Coord above = new Coord(50, 80);
        assertFalse(NIconDock.hitGrown(above, mid, orig, 1.0, false, false),
            "the layout rect must not cover the grown pixels");
        assertTrue(NIconDock.hitGrown(above, mid, orig, NIconDock.maxScale(), false, false),
            "a click on the grown overlay icon must count");
        assertFalse(NIconDock.hitGrown(new Coord(50, 60), mid, orig, NIconDock.maxScale(), false, false),
            "pixels past the maximum grown rect must miss");
    }

    @Test
    void mapClickHitsPixelsOutsideTheLayoutRect() {
        Coord orig = new Coord(20, 20);
        Coord mid = new Coord(190, 10);
        Coord downLeft = new Coord(170, 25);
        assertFalse(NIconDock.hitGrown(downLeft, mid, orig, 1.0, true, true));
        assertTrue(NIconDock.hitGrown(downLeft, mid, orig, NIconDock.maxScale(), true, true),
            "a click on the grown map icon must count");
    }

    @Test
    void activeGrownIconTakesTheClickAtMaxSize() {
        Coord orig = new Coord(20, 20);
        Coord[] mids = {new Coord(50, 100), new Coord(73, 100)};
        Coord[] origs = {orig, orig};
        double[] scales = {NIconDock.maxScale(), 1.2};
        boolean[] inward = {false, false};
        Coord onGrown = new Coord(70, 80);
        assertEquals(0, NIconDock.hitActive(onGrown, mids, origs, scales, inward),
            "the large nearest icon must receive the click, not its neighbour");
        assertEquals(-1, NIconDock.hitActive(new Coord(10, 10), mids, origs, scales, inward),
            "a click far from every icon must fall through to the map");
    }

    @Test
    void ungrownIconKeepsItsLayoutHitbox() {
        Coord orig = new Coord(20, 20);
        Coord mid = new Coord(50, 100);
        Coord[] mids = {mid};
        Coord[] origs = {orig};
        double[] scales = {1.0};
        boolean[] inward = {false};
        assertEquals(0, NIconDock.hitActive(new Coord(50, 100), mids, origs, scales, inward));
        assertEquals(-1, NIconDock.hitActive(new Coord(50, 80), mids, origs, scales, inward),
            "max-size click must not steal map clicks from a still-small icon");
    }

    @Test
    void mapIconGrowsDownAndLeftInsideTheFrame() {
        Coord orig = new Coord(20, 20);
        Coord panel = new Coord(200, 200);
        Coord mid = new Coord(panel.x - (orig.x / 2), orig.y / 2);
        Coord grown = new Coord(38, 38);
        Coord ul = NIconDock.grownUl(mid, orig, grown, true, true);
        int origRight = mid.x + (orig.x / 2);
        int origTop = mid.y - (orig.y / 2);
        assertEquals(origRight - grown.x, ul.x);
        assertEquals(origTop, ul.y);
        assertTrue(ul.x >= 0, "grown map icon must stay inside the left edge");
        assertTrue(ul.y >= 0, "grown map icon must stay inside the top edge");
        assertTrue(ul.x + grown.x <= panel.x, "grown map icon must stay inside the right edge");
        assertTrue(ul.y + grown.y <= panel.y, "grown map icon must stay inside the bottom edge");
    }

    @Test
    void smallIconUpscalesFourTimesForHover() {
        BufferedImage src = rgba(16, 16);
        BufferedImage hi = NIconDock.hiRes(src, new Coord(16, 16));
        assertEquals(64, hi.getWidth());
        assertEquals(64, hi.getHeight());
        assertEquals(16, src.getWidth(), "the layout copy must stay small");
    }

    @Test
    void alreadyLargeSourceIsKept() {
        BufferedImage src = rgba(64, 64);
        assertSame(src, NIconDock.hiRes(src, new Coord(16, 16)));
    }

    @Test
    void twoTimesSourceStillGrowsToFourTimesLogical() {
        BufferedImage src = rgba(32, 32);
        BufferedImage hi = NIconDock.hiRes(src, new Coord(16, 16));
        assertEquals(64, hi.getWidth());
        assertEquals(64, hi.getHeight());
    }

    private static BufferedImage rgba(int w, int h) {
        WritableRaster buf = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, 4, null);
        return new BufferedImage(PUtils.cm_rgba, buf, false, null);
    }
}
