package haven;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentGridCacheTest {
    @Test
    void recentlyTrimmedGridIsRestoredAndRefreshed() {
        MCache map = new MCache(null);
        Coord gc = Coord.of(12, 34);
        MCache.Grid grid = map.new Grid(gc);
        grid.fill(new MessageBuf(new byte[] {1}));
        map.grids.put(gc, grid);

        map.trimall();
        MCache.Grid restored = map.getgrid(gc);

        assertSame(grid, restored);
        assertFalse(restored.removed);
        assertFalse(restored.serverFresh);
        assertTrue(map.inReq(gc));

        restored.fill(new MessageBuf(new byte[] {1}));
        assertTrue(restored.serverFresh);
    }

    @Test
    void expiredAndEvictedEntriesAreDisposed() {
        AtomicLong now = new AtomicLong(1_000);
        List<String> disposed = new ArrayList<>();
        RecentGridCache<Integer, String> cache = new RecentGridCache<>(
                2, 500, now::get, disposed::add);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        assertTrue(disposed.contains("one"));

        now.set(1_501);
        cache.take(3);
        assertTrue(disposed.contains("two"));
        assertTrue(disposed.contains("three"));
    }

    @Test
    void mapFileWaitsForCachedCenterGridToRefresh() {
        MCache map = new MCache(null);
        Coord gc = Coord.of(12, 34);
        MCache.Grid grid = map.new Grid(gc);
        grid.fill(new MessageBuf(new byte[] {1}));
        map.grids.put(gc, grid);
        map.trimall();

        MapFile file = new MapFile(null, "");
        MCache.LoadingMap loading = assertThrows(
                MCache.LoadingMap.class, () -> file.update(map, gc));

        assertEquals(gc, loading.gc);
    }
}
