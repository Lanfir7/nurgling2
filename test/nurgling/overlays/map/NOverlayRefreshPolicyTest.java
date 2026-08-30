package nurgling.overlays.map;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NOverlayRefreshPolicyTest {
    private static final long INTERVAL = 200_000_000L;

    @Test
    void unchangedOverlayIsRefreshedAtMostFiveTimesPerSecond() {
        NOverlayRefreshPolicy policy = new NOverlayRefreshPolicy(INTERVAL);
        Coord center = Coord.of(10, 20);

        assertTrue(policy.shouldRefresh(0, center, 3));
        assertFalse(policy.shouldRefresh(INTERVAL - 1, center, 3));
        assertTrue(policy.shouldRefresh(INTERVAL, center, 3));
    }

    @Test
    void enteringAnotherMapCutRefreshesImmediately() {
        NOverlayRefreshPolicy policy = new NOverlayRefreshPolicy(INTERVAL);

        assertTrue(policy.shouldRefresh(0, Coord.of(10, 20), 3));
        assertTrue(policy.shouldRefresh(1, Coord.of(11, 20), 3));
    }

    @Test
    void invalidatedOverlayRefreshesImmediately() {
        NOverlayRefreshPolicy policy = new NOverlayRefreshPolicy(INTERVAL);
        Coord center = Coord.of(10, 20);

        assertTrue(policy.shouldRefresh(0, center, 3));
        assertTrue(policy.shouldRefresh(1, center, 4));
    }
}
