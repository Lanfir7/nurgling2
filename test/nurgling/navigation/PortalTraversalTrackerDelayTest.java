package nurgling.navigation;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PortalTraversalTrackerDelayTest {
    private static final String HUT = "gfx/terobjs/arch/thatchedhut";
    private static final String MINEHOLE = "gfx/terobjs/minehole";

    @Test
    void ordinaryGridBoundaryAndUnknownPortalDoNotScheduleExitLookup() {
        AtomicLong clock = new AtomicLong(1_000);
        PortalTraversalTracker tracker = tracker(clock);
        assertFalse(tracker.beginTransition(1, 2, new Coord2d(100, 200), new Coord(5, 6)));
        assertNull(tracker.takeDueTransition(2));

        tracker.bindLastActionPortal(null, new Coord(7, 8), 1, "gfx/terobjs/stone");
        assertFalse(tracker.beginTransition(1, 2, new Coord2d(100, 200), new Coord(5, 6)));
        clock.addAndGet(1_000);
        assertNull(tracker.takeDueTransition(2));
    }

    @Test
    void knownPortalWaitsOneHundredMillisecondsThenRunsExactlyOnce() {
        AtomicLong clock = new AtomicLong(1_000);
        PortalTraversalTracker tracker = tracker(clock);
        tracker.bindLastActionPortal(null, new Coord(98, 7), 42, HUT);

        assertTrue(tracker.beginTransition(41, 901, new Coord2d(1234, 5678), new Coord(3, 4)));
        assertNull(tracker.takeDueTransition(901));
        clock.set(1_099);
        assertNull(tracker.takeDueTransition(901));
        clock.set(1_100);
        PortalTraversalTracker.PendingTransition due = tracker.takeDueTransition(901);
        assertNotNull(due);
        assertEquals(41, due.fromGridId);
        assertEquals(901, due.toGridId);
        assertEquals(42, due.entrance.gridId, "door grid may differ from player's source grid");
        assertEquals(new Coord(98, 7), due.entrance.localCoord);
        assertEquals(new Coord(3, 4), due.landingLocalCoord);
        assertEquals(new Coord2d(1234, 5678), due.landingPosition);
        assertNull(tracker.takeDueTransition(901));
    }

    @Test
    void nextGridBoundaryAndResetCancelPriorCheck() {
        AtomicLong clock = new AtomicLong(1_000);
        PortalTraversalTracker tracker = tracker(clock);
        tracker.bindLastActionPortal(null, new Coord(1, 2), 42, HUT);
        assertTrue(tracker.beginTransition(42, 901, new Coord2d(100, 200), new Coord(5, 6)));

        // Another grid change supersedes the first landing even without a second portal.
        assertFalse(tracker.beginTransition(901, 902, new Coord2d(300, 400), new Coord(7, 8)));
        clock.set(1_100);
        assertNull(tracker.takeDueTransition(901));
        assertNull(tracker.takeDueTransition(902));

        tracker.bindLastActionPortal(null, new Coord(1, 2), 42, HUT);
        assertTrue(tracker.beginTransition(42, 901, new Coord2d(100, 200), new Coord(5, 6)));
        tracker.reset();
        assertNull(tracker.takeDueTransition(901));
    }

    @Test
    void laterClickCannotChangePendingEntranceOrLanding() {
        AtomicLong clock = new AtomicLong(1_000);
        PortalTraversalTracker tracker = tracker(clock);
        HomePortalLearningService.Pending originalHome = tracker.bindLastActionPortal(
                null, new Coord(98, 7), 42, HUT);
        assertTrue(tracker.beginTransition(41, 901, new Coord2d(1234, 5678), new Coord(3, 4)));

        HomePortalLearningService.Pending laterHome = tracker.bindLastActionPortal(
                null, new Coord(11, 12), 77, MINEHOLE);
        clock.set(1_100);
        PortalTraversalTracker.PendingTransition first = tracker.takeDueTransition(901);
        assertNotNull(first);
        assertEquals(HUT, first.entrance.name);
        assertEquals(42, first.entrance.gridId);
        assertEquals(new Coord(98, 7), first.entrance.localCoord);
        assertEquals(new Coord(3, 4), first.landingLocalCoord);
        assertSame(originalHome, first.entrance.homeLearning);

        assertTrue(tracker.beginTransition(901, 902, new Coord2d(3333, 4444), new Coord(5, 6)));
        clock.set(1_200);
        PortalTraversalTracker.PendingTransition second = tracker.takeDueTransition(902);
        assertNotNull(second);
        assertEquals(MINEHOLE, second.entrance.name);
        assertEquals(77, second.entrance.gridId);
        assertSame(laterHome, second.entrance.homeLearning);
    }

    @Test
    void missingExitDoesNotMarkEntranceProcessedBeforeConfirmation() {
        AtomicLong clock = new AtomicLong(1_000);
        PortalTraversalTracker tracker = tracker(clock);
        tracker.rememberClickedPortal(123, HUT, "hut-hash", new Coord(9, 10), 42, null);

        assertTrue(tracker.beginTransition(42, 901, new Coord2d(100, 200), new Coord(5, 6)));
        assertFalse(tracker.isProcessedPortal(123));
        clock.set(1_100);
        assertNotNull(tracker.takeDueTransition(901));
        assertFalse(tracker.isProcessedPortal(123),
                "an exit lookup can fail after this point and the next traversal must retry");

        tracker.rememberClickedPortal(123, HUT, "hut-hash", new Coord(9, 10), 42, null);
        assertTrue(tracker.beginTransition(42, 901, new Coord2d(100, 200), new Coord(5, 6)));
    }

    private static PortalTraversalTracker tracker(AtomicLong clock) {
        return new PortalTraversalTracker(null, null, null,
                HomePortalLearningService.disabled(), clock::get);
    }
}
