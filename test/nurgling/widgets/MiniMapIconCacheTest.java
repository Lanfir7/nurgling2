package nurgling.widgets;

import haven.Loading;
import haven.MessageBuf;
import haven.Resource;
import haven.TexI;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MiniMapIconCacheTest {
    @Test
    void resourcePoolLookupDoesNotWaitAndTimerTextureKeepsScaledImage() throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        Resource.Virtual resource = new Resource.Virtual(null, "test/minimap-icon", 1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[11]); // Legacy image-layer header: z, subz, flags, id, offset.
        BufferedImage raw = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage scaled = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(raw, "png", bytes);
        Resource.Image layer = resource.new Image(new MessageBuf(bytes.toByteArray())) {
            @Override
            public BufferedImage scaled() { return scaled; }
        };
        resource.add(layer);
        Resource.Pool pool = new Resource.Pool() {
            @Override
            public Resource.Named load(String path, int version, int priority) {
                requests.incrementAndGet();
                return new Resource.Named(path, version) {
                    @Override
                    public Resource get() {
                        if (!ready.get()) throw new Loading();
                        return resource;
                    }
                };
            }

            @Override
            public Resource loadwait(String path, int version) {
                throw new AssertionError("Minimap draw must not wait for resource loading");
            }
        };
        MiniMapIconCache timers = new MiniMapIconCache(8, () -> pool, true);
        MiniMapIconCache rawIcons = new MiniMapIconCache(8, () -> pool, false);
        assertThrows(Loading.class, () -> timers.get(resource.name));
        ready.set(true);
        TexI timer = timers.get(resource.name);
        assertSame(scaled, timer.back);
        assertEquals(layer.tex().sz(), timer.sz());
        assertNotSame(layer.tex(), timer, "Cache must own, not dispose, the resource's shared texture");
        assertSame(layer.img, rawIcons.get(resource.name).back);
        assertSame(timer, timers.get(resource.name));
        assertEquals(3, requests.get());
        timers.dispose();
        rawIcons.dispose();
        layer.tex().dispose();
    }

    private static class Icon extends TexI {
        int disposals;

        Icon() {
            super(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
        }

        @Override
        public void dispose() {
            disposals++;
            super.dispose();
        }
    }

    @Test
    void pendingLoadReturnsControlToDrawAndIsRetriedUntilReady() {
        AtomicBoolean ready = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        Loading pending = new Loading();
        Icon icon = new Icon();
        MiniMapIconCache cache = new MiniMapIconCache(8, path -> {
            requests.incrementAndGet();
            if (!ready.get()) throw pending;
            return icon;
        }, System::nanoTime);

        assertSame(pending, assertThrows(Loading.class, () -> cache.get("oak")));
        assertSame(pending, assertThrows(Loading.class, () -> cache.get("oak")));
        ready.set(true);
        assertSame(icon, cache.get("oak"));
        for (int frame = 0; frame < 100; frame++) assertSame(icon, cache.get("oak"));
        assertEquals(3, requests.get());
        cache.dispose();
    }

    @Test
    void failedLoaderIsThrottledAndLookedUpAgainAfterCooldown() {
        AtomicLong now = new AtomicLong();
        AtomicInteger requests = new AtomicInteger();
        Icon icon = new Icon();
        RuntimeException failure = new IllegalArgumentException("Missing icon");
        MiniMapIconCache cache = new MiniMapIconCache(8, path -> {
            if (requests.incrementAndGet() == 1) throw failure;
            return icon;
        }, now::get);

        assertSame(failure, assertThrows(RuntimeException.class, () -> cache.get("fish")));
        for (int frame = 0; frame < 100; frame++) {
            assertSame(failure, assertThrows(RuntimeException.class, () -> cache.get("fish")));
        }
        assertEquals(1, requests.get());
        now.set(5_000_000_000L);
        assertSame(icon, cache.get("fish"));
        assertEquals(2, requests.get());
        cache.dispose();
    }

    @Test
    void evictionAndClosingReleaseOwnedTextures() {
        Icon first = new Icon();
        Icon second = new Icon();
        MiniMapIconCache cache = new MiniMapIconCache(1,
                path -> path.equals("first") ? first : second, System::nanoTime);
        assertSame(first, cache.get("first"));
        assertSame(second, cache.get("second"));
        assertEquals(1, first.disposals);
        assertEquals(0, second.disposals);
        cache.dispose();
        cache.dispose();
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
    }
}
