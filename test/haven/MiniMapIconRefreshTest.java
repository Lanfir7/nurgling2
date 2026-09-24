package haven;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMapIconRefreshTest {

    @Test
    void gobIconsAreRescannedAtMostFiveTimesPerSecond() {
        AtomicInteger scans = new AtomicInteger();
        MiniMapIconPolicy.TimedRefresh<Integer> refresh = MiniMapIconPolicy.newRefresh();

        MiniMapIconPolicy.RefreshResult<Integer> first = refresh.update(0, scans::incrementAndGet);
        MiniMapIconPolicy.RefreshResult<Integer> cached = refresh.update(0.199, scans::incrementAndGet);
        MiniMapIconPolicy.RefreshResult<Integer> next = refresh.update(0.002, scans::incrementAndGet);

        assertTrue(first.refreshed);
        assertEquals(1, first.value);
        assertFalse(cached.refreshed);
        assertEquals(1, cached.value);
        assertTrue(next.refreshed);
        assertEquals(2, next.value);
        assertEquals(2, scans.get());
    }

    @Test
    void ownCharacterAndPartyMembersAreNotIconAlarms() {
        assertTrue(MiniMapIconPolicy.skipPartyGob(7, 7, false));
        assertTrue(MiniMapIconPolicy.skipPartyGob(7, -1, true));
        assertFalse(MiniMapIconPolicy.skipPartyGob(8, 7, false));
        assertTrue(MiniMapIconPolicy.isPlayerMapIcon("gfx/hud/mmap/plo"));
        assertTrue(MiniMapIconPolicy.skipUnsyncedPlayerIcon(false, "gfx/hud/mmap/plo"));
        assertFalse(MiniMapIconPolicy.skipUnsyncedPlayerIcon(true, "gfx/hud/mmap/plo"));
        assertFalse(MiniMapIconPolicy.skipUnsyncedPlayerIcon(false, "gfx/invobjs/kritter/bear"));
    }

    @Test
    void gobIconsOutsideViewportMarginAreRejected() {
        Coord viewport = Coord.of(100, 80);

        assertTrue(MiniMapIconPolicy.insideViewport(Coord.of(-10, 40), viewport, 10));
        assertTrue(MiniMapIconPolicy.insideViewport(Coord.of(110, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(-11, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(111, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(50, 91), viewport, 10));
    }

    @Test
    void takeLoadedSwallowsDeferredLoaderError() throws Exception {
        Loader loader = new Loader();
        Loader.Future<GobIcon.Icon> load = loader.defer(() -> {
            throw new NullPointerException("rimg");
        });
        long deadline = System.currentTimeMillis() + 2000;
        while (!load.done() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(load.done());
        assertEquals(null, MiniMapIconPolicy.takeLoaded(load));
    }
}
