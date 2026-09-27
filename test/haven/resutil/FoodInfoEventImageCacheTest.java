package haven.resutil;

import haven.CharWnd;
import haven.Coord;
import haven.PUtils;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class FoodInfoEventImageCacheTest {
    private NConfig previous;

    @BeforeEach
    void initializeFonts() {
        previous = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach
    void restoreConfig() {
        NConfig.current = previous;
    }

    @Test
    void repeatedIconsReuseIdenticalPixelsButDifferentSourcesAndSizesDoNot() throws Exception {
        try (ClientResourceFixture resources = new ClientResourceFixture()) {
            FoodInfo.EventImageCache cache = new FoodInfo.EventImageCache(8, 100000);
            BufferedImage source = source(32);
            Coord size = new Coord(12, 12);
            BufferedImage expected = PUtils.convolve(source, size, CharWnd.iconfilter);
            BufferedImage actual = cache.get(source, size);
            assertArrayEquals(expected.getRGB(0, 0, 12, 12, null, 0, 12),
                    actual.getRGB(0, 0, 12, 12, null, 0, 12));
            for (int i = 0; i < 100; i++)
                assertSame(actual, cache.get(source, new Coord(12, 12)));
            assertNotSame(actual, cache.get(source(32), size));
            BufferedImage resized = cache.get(source, new Coord(16, 16));
            assertEquals(16, resized.getWidth());
            assertEquals(16, resized.getHeight());
            assertNotSame(actual, resized);
            FoodInfo.Event event = new FoodInfo.Event(null, actual, 2.5);
            assertSame(actual, event.img);
            assertEquals(2.5, event.a);
        }
    }

    @Test
    void boundsEntriesAndRetainedSourcePixelsAndSkipsOversizedImages() throws Exception {
        try (ClientResourceFixture resources = new ClientResourceFixture()) {
            Coord size = new Coord(4, 4);
            BufferedImage a = source(8), b = source(8), c = source(8);
            FoodInfo.EventImageCache entries = new FoodInfo.EventImageCache(2, 10000);
            BufferedImage first = entries.get(a, size);
            BufferedImage second = entries.get(b, size);
            assertSame(first, entries.get(a, size));
            entries.get(c, size);
            assertSame(first, entries.get(a, size));
            assertNotSame(second, entries.get(b, size));

            // Each entry retains 64 source pixels plus 16 output pixels.
            FoodInfo.EventImageCache pixels = new FoodInfo.EventImageCache(8, 100);
            first = pixels.get(a, size);
            pixels.get(b, size);
            assertNotSame(first, pixels.get(a, size));
            BufferedImage oversized = source(16);
            assertNotSame(pixels.get(oversized, size), pixels.get(oversized, size));
            FoodInfo.EventImageCache disabled = new FoodInfo.EventImageCache(0, 10000);
            assertNotSame(disabled.get(a, size), disabled.get(a, size));
        }
    }

    private static BufferedImage source(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_4BYTE_ABGR);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                image.setRGB(x, y, ((80 + (x * 7 + y * 3) % 176) << 24)
                        | ((x * 31 % 256) << 16) | ((y * 47 % 256) << 8) | 127);
        return image;
    }
}
