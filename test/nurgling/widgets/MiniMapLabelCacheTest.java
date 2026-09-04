package nurgling.widgets;

import haven.Text;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class MiniMapLabelCacheTest {
    private static class Label extends Text {
        int disposals;

        Label(String text) {
            super(text, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        }

        @Override
        public void dispose() {
            disposals++;
            super.dispose();
        }
    }

    private static class Furnace extends Text.Furnace {
        int renders;

        @Override
        public Text render(String text) {
            renders++;
            return new Label(text);
        }
    }

    @Test
    void unchangedLabelsReuseTextAndTextureAcrossFrames() {
        MiniMapLabelCache cache = new MiniMapLabelCache(8);
        Furnace furnace = new Furnace();
        Text first = cache.get(furnace, "Oak q123");
        for (int frame = 0; frame < 100; frame++) {
            Text next = cache.get(furnace, new String("Oak q123"));
            assertSame(first, next);
            assertSame(first.tex(), next.tex());
        }
        assertEquals(1, furnace.renders);
        cache.dispose();
    }

    @Test
    void changedNameOrStyleDoesNotReuseStaleLabel() {
        MiniMapLabelCache cache = new MiniMapLabelCache(8);
        Furnace active = new Furnace();
        Furnace expired = new Furnace();
        Text original = cache.get(active, "00:01");
        assertNotSame(original, cache.get(active, "Ready"));
        assertNotSame(original, cache.get(expired, "00:01"));
        cache.dispose();
    }

    @Test
    void peerLabelColorIsPartOfCacheKey() {
        MiniMapLabelCache cache = new MiniMapLabelCache(8);
        Text.Foundry furnace = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 10));
        Text white = cache.get(furnace, "Friend", Color.WHITE);
        Text green = cache.get(furnace, "Friend", Color.GREEN);
        assertNotSame(white, green);
        assertSame(green, cache.get(furnace, "Friend", new Color(0, 255, 0)));
        cache.dispose();
    }

    @Test
    void evictionReleasesLeastRecentlyUsedLabelAndDisposeClearsRest() {
        MiniMapLabelCache cache = new MiniMapLabelCache(2);
        Furnace furnace = new Furnace();
        Label first = (Label) cache.get(furnace, "first");
        Label second = (Label) cache.get(furnace, "second");
        assertSame(first, cache.get(furnace, "first"));
        Label third = (Label) cache.get(furnace, "third");
        assertEquals(0, first.disposals);
        assertEquals(1, second.disposals);
        cache.dispose();
        cache.dispose();
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
        assertEquals(1, third.disposals);
        assertNotSame(first, cache.get(furnace, "first"));
        cache.dispose();
    }
}
