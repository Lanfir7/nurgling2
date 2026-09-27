package haven;

import haven.render.NumberFormat;
import haven.render.Texture;
import haven.render.VectorFormat;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MiniMapCellTextureTest {
    @Test
    void mapCellUsesExactRgbaPixelsWithoutPowerOfTwoPadding() {
        checkCell(TexI.mkbuf(MCache.cmaps)); // MapSource.drawmap format.
        checkCell(PUtils.rasterimg(PUtils.imgraster(MCache.cmaps))); // ZoomGrid.render format.
    }

    private static void checkCell(BufferedImage image) {
        image.setRGB(0, 0, 0xff123456);
        image.setRGB(99, 99, 0xffabcdef);
        TexI tex = MiniMap.DisplayGrid.mapCellTexture(image);
        try {
            assertSame(image, tex.back);
            assertEquals(MCache.cmaps, tex.sz());
            assertEquals(MCache.cmaps, tex.tdim);
            assertEquals(0xff123456, tex.back.getRGB(0, 0));
            assertEquals(0xffabcdef, tex.back.getRGB(99, 99));
            assertEquals(new VectorFormat(4, NumberFormat.UNORM8), TexI.detectfmt(image));
            assertEquals(TexI.detectfmt(image), tex.st().data.tex.efmt);
            assertEquals(Texture.Wrapping.CLAMP, tex.st().data.swrap);
            assertEquals(Texture.Wrapping.CLAMP, tex.st().data.twrap);
            assertEquals(Texture.Filter.NEAREST, tex.st().data.magfilter);
            assertEquals(Texture.Filter.NEAREST, tex.st().data.minfilter);
        } finally {
            tex.dispose();
        }
    }
}
