package haven;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PUtilsBlurmaskTest {
    @Test
    void blurmask2AcceptsTranslatedRaster() {
        WritableRaster parent = PUtils.imgraster(Coord.of(16, 16));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                parent.setSample(x, y, 0, 255);
                parent.setSample(x, y, 3, 255);
            }
        }
        Raster raster = parent.createChild(4, 4, 8, 8, 4, 4, null);
        assertTrue(raster.getMinX() != 0 || raster.getMinY() != 0);

        WritableRaster out = assertDoesNotThrow(
                () -> PUtils.blurmask2(raster, 1, 1, Color.BLACK));
        assertEquals(12, out.getWidth());
        assertEquals(12, out.getHeight());
    }

    @Test
    void blurmask2AcceptsRgbWithoutAlpha() {
        BufferedImage src = opaque(BufferedImage.TYPE_3BYTE_BGR, 8);

        WritableRaster out = assertDoesNotThrow(
                () -> PUtils.blurmask2(src.getRaster(), 1, 1, Color.BLACK));
        assertEquals(12, out.getWidth());
        assertEquals(12, out.getHeight());
    }

    @Test
    void blurmask2KeepsOriginRgba() {
        BufferedImage src = opaque(BufferedImage.TYPE_INT_ARGB, 6);
        WritableRaster out = PUtils.blurmask2(src.getRaster(), 1, 1, Color.BLACK);
        assertEquals(10, out.getWidth());
        assertEquals(10, out.getHeight());
    }

    @Test
    void alphadrawAcceptsTranslatedAlpha() {
        WritableRaster parent = PUtils.alpharaster(Coord.of(16, 16));
        parent.setSample(6, 6, 0, 255);
        Raster alpha = parent.createChild(4, 4, 8, 8, 4, 4, null);
        assertTrue(alpha.getMinX() != 0 || alpha.getMinY() != 0);
        WritableRaster dst = PUtils.imgraster(Coord.of(8, 8));
        assertDoesNotThrow(() -> PUtils.alphadraw(dst, alpha, Coord.z, Color.BLACK));
    }

    @Test
    void mpartKeepsTileCoordWhenLoopCoordMoves() {
        Coord loop = Coord.of(3, 4);
        Coord gc = Coord.of(100, 200);
        Tiler.MPart part = new Tiler.MPart(loop, gc, new Surface.Vertex[0], new float[0], new float[0], new int[0]);
        loop.x = 25;
        loop.y = 25;
        gc.x = 0;
        assertEquals(3, part.lc.x);
        assertEquals(4, part.lc.y);
        assertEquals(100, part.gc.x);
        assertEquals(200, part.gc.y);
    }

    @Test
    void waterBottomSplitSurvivesPollutedCoordZ() {
        int ox = Coord.z.x, oy = Coord.z.y;
        Coord.z.x = 26;
        Coord.z.y = 25;
        try {
            MapMesh.Scan ts = new MapMesh.Scan(Coord.z, Coord.of(25, 25));
            MapMesh.Scan vs = new MapMesh.Scan(Coord.of(-1, -1), Coord.of(28, 28));
            assertEquals(0, ts.ul.x);
            assertEquals(0, ts.ul.y);
            assertEquals(25, ts.br.x);
            for (int y = ts.ul.y; y < ts.br.y; y++) {
                for (int x = ts.ul.x; x < ts.br.x; x++) {
                    int i = vs.o(x + 1, y + 1);
                    assertTrue(i >= 0 && i < vs.l, () -> "vs.o out of range: " + i);
                }
            }
            MapMesh.Scan es = new MapMesh.Scan(Coord.z, Coord.of(25, 25));
            assertEquals(21, es.o(21, 0));
        } finally {
            Coord.z.x = ox;
            Coord.z.y = oy;
        }
    }

    @Test
    void meshScanIndexMatchesFriendCrashCoords() {
        MapMesh.Scan es = new MapMesh.Scan(Coord.z, Coord.of(25, 25));
        Coord lc = Coord.of(21, -5);
        assertEquals(-104, es.o(lc));
        assertFalse(es.has(lc));
        assertFalse(es.has(Coord.of(25, 0)));
        assertFalse(es.has(Coord.of(3, -4)));
        assertEquals(-97, es.o(Coord.of(3, -4)));
    }

    @Test
    void vertexScanRejectsOffByOneThatCrashedFriend() {
        MapMesh.Scan vs = new MapMesh.Scan(Coord.of(-1, -1), Coord.of(28, 28));
        assertEquals(784, vs.l);
        assertEquals(784, vs.o(27, 26));
        assertFalse(vs.has(27, 26));
        assertTrue(vs.has(26, 26));
        assertEquals(783, vs.o(26, 26));
    }

    @Test
    void tilemodAcceptsTranslatedTile() {
        WritableRaster parent = PUtils.imgraster(Coord.of(16, 16));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                parent.setSample(x, y, 0, 128);
                parent.setSample(x, y, 3, 255);
            }
        }
        Raster tile = parent.createChild(4, 4, 8, 8, 4, 4, null);
        WritableRaster dst = PUtils.imgraster(Coord.of(8, 8));
        assertDoesNotThrow(() -> PUtils.tilemod(dst, tile, Coord.z));
    }

    @Test
    void blitAcceptsTranslatedSource() {
        WritableRaster parent = PUtils.imgraster(Coord.of(16, 16));
        Raster src = parent.createChild(4, 4, 8, 8, 4, 4, null);
        WritableRaster dst = PUtils.imgraster(Coord.of(8, 8));
        assertDoesNotThrow(() -> PUtils.blit(dst, src, Coord.z));
    }

    @Test
    void blurmask2AcceptsImageIoPng() throws Exception {
        BufferedImage src = opaque(BufferedImage.TYPE_INT_ARGB, 8);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(src, "PNG", bytes);
        BufferedImage io = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        WritableRaster out = assertDoesNotThrow(
                () -> PUtils.blurmask2(io.getRaster(), 1, 1, Color.BLACK));
        assertTrue(out.getWidth() >= 8);
        assertTrue(out.getHeight() >= 8);
    }

    @Test
    void blurmask2Accepts4ByteAbgr() {
        BufferedImage src = opaque(BufferedImage.TYPE_4BYTE_ABGR, 8);
        WritableRaster out = assertDoesNotThrow(
                () -> PUtils.blurmask2(src.getRaster(), 1, 1, Color.BLACK));
        assertEquals(12, out.getWidth());
        assertEquals(12, out.getHeight());
    }

    private static BufferedImage opaque(int type, int size) {
        BufferedImage img = new BufferedImage(size, size, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, size, size);
        g.dispose();
        return img;
    }
}
