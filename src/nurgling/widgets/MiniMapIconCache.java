package nurgling.widgets;

import haven.Disposable;
import haven.Loading;
import haven.Resource;
import haven.TexI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** UI-thread cache. Loading is propagated so the draw caller can paint a placeholder. */
final class MiniMapIconCache implements Disposable {
    private static final long FAILURE_RETRY_NANOS = 5_000_000_000L;
    private final Map<String, Entry> icons;
    private final Function<String, TexI> loader;
    private final LongSupplier clock;

    MiniMapIconCache(int capacity) {
        this(capacity, Resource::remote, false);
    }

    MiniMapIconCache(int capacity, Supplier<Resource.Pool> pool, boolean scaled) {
        // The remote pool already tries its local parent first; get() never waits for IO.
        this(capacity, path -> {
            Resource.Image image = pool.get().load(path).get().layer(Resource.imgc);
            return new TexI(scaled ? image.scaled() : image.img);
        }, System::nanoTime);
    }

    MiniMapIconCache(int capacity, Function<String, TexI> loader, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.loader = loader;
        this.clock = clock;
        icons = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                if (size() <= capacity) return false;
                eldest.getValue().dispose();
                return true;
            }
        };
    }

    TexI get(String path) {
        Entry entry = icons.get(path);
        if (entry != null) {
            if (entry.texture != null) return entry.texture;
            if (clock.getAsLong() - entry.failedAt < FAILURE_RETRY_NANOS) throw entry.failure;
        }
        try {
            TexI texture = loader.apply(path);
            icons.put(path, new Entry(texture, null, 0));
            return texture;
        } catch (Loading pending) {
            // A pending request is not a failure. The resource pool coalesces repeated requests.
            throw pending;
        } catch (RuntimeException failure) {
            // Throttle wrapper lookups only; Resource.Pool owns failed-request/IO retry policy.
            icons.put(path, new Entry(null, failure, clock.getAsLong()));
            throw failure;
        }
    }

    @Override
    public void dispose() {
        for (Entry entry : icons.values()) entry.dispose();
        icons.clear();
    }

    private static final class Entry implements Disposable {
        final TexI texture;
        final RuntimeException failure;
        final long failedAt;

        Entry(TexI texture, RuntimeException failure, long failedAt) {
            this.texture = texture;
            this.failure = failure;
            this.failedAt = failedAt;
        }

        @Override
        public void dispose() {
            if (texture != null) texture.dispose();
        }
    }
}
