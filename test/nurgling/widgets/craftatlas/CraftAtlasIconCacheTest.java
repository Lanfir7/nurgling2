package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;
import haven.Loading;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void pendingGameResourceIsRetriedInsteadOfBecomingAPermanentMiss() {
        AtomicBoolean ready = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        BufferedImage icon = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        CraftAtlasIconCache cache = new CraftAtlasIconCache(resource -> {
            requests.incrementAndGet();
            if(!ready.get()) throw new Loading();
            return icon;
        }, name -> null);

        assertNull(cache.icon("gfx/invobjs/beeswax", "Beeswax"));

        ready.set(true);
        assertNotNull(cache.icon("gfx/invobjs/beeswax", "Beeswax"));
        assertEquals(2, requests.get());
        cache.dispose();
    }
}
