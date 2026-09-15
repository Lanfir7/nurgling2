package nurgling.actions.bots;

import haven.Coord2d;
import nurgling.actions.PathFinder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerBreadcrumbTest {
    @Test
    void failedFallbackHopKeepsTheBreadcrumbAndEndsTheReturn() {
        assertFalse(Forager.shouldDiscardBreadcrumbAfterFallback(false));
        assertTrue(Forager.shouldDiscardBreadcrumbAfterFallback(true));
    }

    @Test
    void retriesHearthAfterUnloadEvenIfUnloadFailed() {
        assertTrue(Forager.shouldRunSecondHearth(true, false));
    }

    @Test
    void exitPointIsOutsideEveryOverlappingDangerZone() {
        PathFinder.AvoidZone first = new PathFinder.AvoidZone(Coord2d.of(0, 0), Coord2d.of(0, 0), 10, "first");
        PathFinder.AvoidZone second = new PathFinder.AvoidZone(Coord2d.of(12, 0), Coord2d.of(12, 0), 10, "second");

        Coord2d exit = Forager.outsideDangerZones(Coord2d.of(0, 0), Arrays.asList(first, second));

        assertFalse(first.contains(exit));
        assertFalse(second.contains(exit));
    }
}
