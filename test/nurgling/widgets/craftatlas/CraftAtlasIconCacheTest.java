package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftAtlasIconCacheTest {
    @Test
    void transparentPaddingIsRemovedBeforeTheIconIsScaled() {
        BufferedImage source = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for(int y = 19; y < 39; y++)
            for(int x = 11; x < 21; x++)
                source.setRGB(x, y, 0xffffffff);

        BufferedImage trimmed = CraftAtlasIconCache.trimTransparent(source);

        assertEquals(10, trimmed.getWidth());
        assertEquals(20, trimmed.getHeight());
    }
}
