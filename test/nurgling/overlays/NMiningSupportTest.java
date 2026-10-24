package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.res.lib.tree.TreeScale;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    void resizedFullyGrownTowercapKeepsFullSupportRadius() {
        Gob gob = new Gob(null, Coord2d.of(5.5, 5.5), 1);
        gob.setattr(new TreeScale(gob, 0.2f, 1.0f));
        NMiningSupport overlay = new NMiningSupport(gob, 100);

        overlay.getData();

        assertEquals(100, overlay.r);
    }

    @Test
    void simultaneousTreeMaskReadsAlwaysReturnCompleteCoverage() throws Exception {
        Gob gob = new Gob(null, Coord2d.of(5.5, 5.5), 1);
        gob.setattr(new TreeScale(gob, 1.0f));
        NMiningSupport support = new NMiningSupport(gob, 100);
        // A radius-100 circle on the 11-unit tile grid has 261 lit centers.
        assertEquals(261, count(support.getData()));
        AtomicInteger incomplete = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            Future<?>[] jobs = new Future<?>[8];
            for (int i = 0; i < jobs.length; i++) {
                jobs[i] = workers.submit(() -> {
                    start.await();
                    for (int n = 0; n < 500; n++) {
                        if (count(support.getData()) != 261)
                            incomplete.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<?> job : jobs)
                job.get(30, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertEquals(0, incomplete.get(), "a cut received a partly populated tree mask");
    }

    @Test
    void treeMaskSnapshotKeepsItsBoundsAndPixelsWhenGrowthChanges() {
        Gob gob = new Gob(null, Coord2d.of(5.5, 5.5), 1);
        TreeScale growth = new TreeScale(gob, 1.0f);
        gob.setattr(growth);
        NMiningSupport support = new NMiningSupport(gob, 100);

        growth.originalScale = 0.55f;
        NMiningSupport.SupportMask small = support.getMask();
        assertEquals(new Coord(-4, -4), small.begin);
        assertEquals(new Coord(5, 5), small.end);
        assertEquals(69, count(small.data));

        growth.originalScale = 1.0f;
        NMiningSupport.SupportMask large = support.getMask();
        assertEquals(new Coord(-9, -9), large.begin);
        assertEquals(new Coord(10, 10), large.end);
        assertEquals(261, count(large.data));
        assertEquals(new Coord(-4, -4), small.begin);
        assertEquals(new Coord(5, 5), small.end);
        assertEquals(69, count(small.data));
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
    void builtTunnelPropsTheSameTilesAsItsPreviewAtNegativeCoordinates() {
        int[][] sizes = {{1, 5}, {2, 8}, {3, 15}};
        double[] angles = {0, Math.PI / 2, Math.PI, -Math.PI / 2};
        Coord[] forwards = {new Coord(1, 0), new Coord(0, 1),
                new Coord(-1, 0), new Coord(0, -1)};
        for (int[] size : sizes) {
            int width = size[0], length = size[1];
            for (int direction = 0; direction < angles.length; direction++) {
                Coord fwd = forwards[direction];
                Coord lateral = new Coord(Math.abs(fwd.y), Math.abs(fwd.x));
                Coord2d line = Coord2d.of(-22, -22);
                if (width % 2 == 1) {
                    line = line.add(Coord2d.of(lateral).mul(5.5));
                }
                Coord2d builtPosition = line.sub(Coord2d.of(fwd).mul(0.1));
                String label = width + "x" + length + " facing " + fwd;

                NMiningSupport.Mask preview = NMiningSupport.computeRect(
                        line, angles[direction], width, length);
                NMiningSupport.Mask built = NMiningSupport.computeRect(
                        builtPosition, angles[direction], width, length, true);
                assertEquals(width * length, count(preview), label);
                assertEquals(preview.begin, built.begin, label + " first tile");
                assertEquals(preview.end, built.end, label + " last tile");
                assertTrue(Arrays.deepEquals(preview.data, built.data), label + " pixels");

                NMiningSupport.SupportMask previewOverlay = newTunnelOverlay(
                        -1, line, angles[direction], width, length).getMask();
                NMiningSupport.SupportMask builtOverlay = newTunnelOverlay(
                        1, builtPosition, angles[direction], width, length).getMask();
                assertEquals(preview.begin, builtOverlay.begin, label + " overlay begin");
                assertEquals(preview.end.add(1, 1), builtOverlay.end, label + " exclusive end");
                assertEquals(previewOverlay.begin, builtOverlay.begin, label + " overlay first tile");
                assertEquals(previewOverlay.end, builtOverlay.end, label + " overlay last tile");
                assertTrue(Arrays.deepEquals(previewOverlay.data, builtOverlay.data), label + " overlay pixels");
            }
        }
    }

    private static NMiningSupport newTunnelOverlay(long gobId, Coord2d position, double angle,
                                                   int width, int length) {
        Gob gob = new Gob(null, position, gobId);
        gob.a = angle;
        return new NMiningSupport(gob, width, length);
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
        return count(mask.data);
    }

    private static int count(boolean[][] data) {
        int n = 0;
        for (boolean[] col : data) {
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
