package nurgling.overlays.map;

import haven.MapFile;
import haven.Loading;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapClaimRendererTest {
    @Test
    void resolvedLayerIsReusedButLoadingRetriesAndNewOverlayResolvesAgain() {
        MinimapClaimRenderer.OverlayLayerCache<String> cache =
                new MinimapClaimRenderer.OverlayLayerCache<>();
        MapFile.Overlay missingLayer = new MapFile.Overlay(null, new boolean[1]);
        AtomicInteger calls = new AtomicInteger();
        assertNull(cache.get(missingLayer, () -> {
            calls.incrementAndGet();
            return null;
        }));
        assertEquals("appeared", cache.get(missingLayer, () -> {
            calls.incrementAndGet();
            return "appeared";
        }));
        assertEquals("appeared", cache.get(missingLayer, () -> "unexpected"));
        assertEquals(2, calls.get(), "an absent layer is retried");

        MapFile.Overlay loading = new MapFile.Overlay(null, new boolean[1]);
        assertThrows(Loading.class, () -> cache.get(loading, () -> {
            calls.incrementAndGet();
            throw new Loading("not ready");
        }));
        assertEquals("ready", cache.get(loading, () -> {
            calls.incrementAndGet();
            return "ready";
        }));
        assertEquals("ready", cache.get(loading, () -> "unexpected"));
        assertEquals(4, calls.get());

        MapFile.Overlay refreshed = new MapFile.Overlay(null, new boolean[1]);
        assertEquals("updated", cache.get(refreshed, () -> "updated"));
    }

    @Test
    void localRectanglesExactlyCoverClaimTilesAndReuseStableGrid() {
        boolean[] mask = {
                true, true, false, false, false,
                true, true, false, true, true,
                false, true, false, true, true,
                false, false, false, false, true
        };
        MapFile.Overlay overlay = new MapFile.Overlay(null, mask);

        List<MinimapClaimRenderer.Rect> rectangles = MinimapClaimRenderer.rectanglesFor(overlay, 5, 4);
        assertSame(rectangles, MinimapClaimRenderer.rectanglesFor(overlay, 5, 4));

        boolean[] covered = new boolean[mask.length];
        for (MinimapClaimRenderer.Rect rect : rectangles) {
            assertTrue(rect.width > 0 && rect.height > 0);
            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    int index = x + y * 5;
                    assertTrue(mask[index]);
                    assertFalse(covered[index], "rectangles must not overlap");
                    covered[index] = true;
                }
            }
        }
        assertEquals(java.util.Arrays.toString(mask), java.util.Arrays.toString(covered));
    }

    @Test
    void refreshedOverlayAndGridDimensionsRebuildLocalGeometry() {
        MapFile.Overlay first = new MapFile.Overlay(null,
                new boolean[]{true, true, true, false, false, false});
        List<MinimapClaimRenderer.Rect> old = MinimapClaimRenderer.rectanglesFor(first, 3, 2);
        List<MinimapClaimRenderer.Rect> resized = MinimapClaimRenderer.rectanglesFor(first, 2, 3);
        assertNotSame(old, resized);
        assertEquals(3, old.get(0).width);
        assertEquals(2, resized.get(0).width);

        MapFile.Overlay refreshed = new MapFile.Overlay(null,
                new boolean[]{false, false, false, false, false, true});
        List<MinimapClaimRenderer.Rect> next = MinimapClaimRenderer.rectanglesFor(refreshed, 3, 2);
        assertNotSame(old, next);
        assertEquals(1, next.get(0).y);
    }
}
