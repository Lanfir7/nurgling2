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
    void gobIconsOutsideViewportMarginAreRejected() {
        Coord viewport = Coord.of(100, 80);

        assertTrue(MiniMapIconPolicy.insideViewport(Coord.of(-10, 40), viewport, 10));
        assertTrue(MiniMapIconPolicy.insideViewport(Coord.of(110, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(-11, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(111, 40), viewport, 10));
        assertFalse(MiniMapIconPolicy.insideViewport(Coord.of(50, 91), viewport, 10));
    }
}
