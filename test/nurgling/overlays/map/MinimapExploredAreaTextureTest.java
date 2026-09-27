package nurgling.overlays.map;

import haven.MCache;
import haven.PUtils;
import haven.TexI;
import haven.render.NumberFormat;
import haven.render.Texture;
import haven.render.VectorFormat;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MinimapExploredAreaTextureTest {
    @Test
    void fogCellKeepsCornerAlphaAndClampsAtExactTextureEdge() {
        BufferedImage image = PUtils.rasterimg(PUtils.imgraster(MCache.cmaps));
        image.setRGB(0, 0, 0x40123456);
        image.setRGB(99, 99, 0xc0abcdef);

        TexI tex = MinimapExploredAreaRenderer.cellTexture(image);
        try {
            assertSame(image, tex.back);
            assertEquals(MCache.cmaps, tex.sz());
            assertEquals(MCache.cmaps.x, tex.st().data.tex.w);
            assertEquals(MCache.cmaps.y, tex.st().data.tex.h);
            assertEquals(0x40123456, tex.back.getRGB(0, 0));
            assertEquals(0xc0abcdef, tex.back.getRGB(99, 99));
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
