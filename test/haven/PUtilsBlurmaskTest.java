package haven;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static BufferedImage opaque(int type, int size) {
        BufferedImage img = new BufferedImage(size, size, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, size, size);
        g.dispose();
        return img;
    }
}
