package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import nurgling.actions.Results;
import nurgling.actions.UseMilestone;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerSection;
import nurgling.routes.ForagerWaypoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerMilestoneExecutionTest {

    private static MiniMap.Location sessloc(long segId) {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        return new MiniMap.Location(file.new Segment(segId), new Coord(0, 0));
    }

    @Test
    void nonFinalLongGapHopUsesSectionEndPointNotDestinationWaypoint() {
        ForagerPath path = new ForagerPath("long-gap");
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(0, 0)));
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(10, 0)));
        MiniMap.Location loc = sessloc(1L);
        path.generateSections(loc);

        assertTrue(path.getSectionCount() > 1, "10-tile gap must subdivide");
        ForagerSection firstHop = path.getSection(0);
        assertFalse(firstHop.isLastInGap);

        ForagerWaypoint dest = path.waypoints.get(1);
        Coord2d destWorld = dest.toWorldCoord(loc);
        Coord2d target = Forager.resolveSectionWalkTarget(firstHop, dest, loc);
        assertSame(firstHop.endPoint, target);
        assertNotEquals(destWorld, target);
    }

    @Test
    void finalLongGapHopUsesDestinationWaypointCoordinate() {
        ForagerPath path = new ForagerPath("long-gap-final");
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(0, 0)));
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(10, 0)));
        MiniMap.Location loc = sessloc(1L);
        path.generateSections(loc);

        ForagerSection lastHop = path.getSection(path.getSectionCount() - 1);
        assertTrue(lastHop.isLastInGap);
        ForagerWaypoint dest = path.waypoints.get(1);
        Coord2d destWorld = dest.toWorldCoord(loc);
        assertEquals(destWorld, Forager.resolveSectionWalkTarget(lastHop, dest, loc));
    }

    @Test
    void missingMilestoneGobFallsBackToSectionEnd() {
        Coord2d sectionEnd = new Coord2d(40, 50);
        assertEquals(sectionEnd, Forager.resolveMilestoneWalkTarget(null, new Coord2d(1, 1), sectionEnd));
    }

    @Test
    void nextSectionIndexAfterTeleportSkipsCompletedSplice() {
        ForagerPath path = new ForagerPath("splice");
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(0, 0)));
        ForagerWaypoint from = new ForagerWaypoint(1L, new Coord(1, 0));
        from.milestoneHash = "hash-a";
        path.addWaypoint(from);
        ForagerWaypoint to = new ForagerWaypoint(2L, new Coord(0, 0));
        to.milestoneHash = "hash-a";
        path.addWaypoint(to);
        path.addWaypoint(new ForagerWaypoint(2L, new Coord(2, 0)));
        path.generateSections(sessloc(2L));

        int next = Forager.nextSectionIndexAfterTeleport(path, 1);
        assertEquals(1, next);
        assertEquals(2, path.getSection(next).waypointIndex);
    }

    @Test
    void waitForDestinationWorldCoordRetriesUntilSesslocCatchesUp() throws InterruptedException {
        ForagerWaypoint dest = new ForagerWaypoint(2L, new Coord(0, 0));
        MiniMap.Location origin = sessloc(1L);
        MiniMap.Location destLoc = sessloc(2L);
        AtomicInteger polls = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();

        Coord2d world = Forager.waitForDestinationWorldCoord(
                dest,
                () -> polls.getAndIncrement() == 0 ? origin : destLoc,
                5,
                waits::incrementAndGet);

        assertNull(dest.toWorldCoord(origin), "origin sessloc must not resolve dest world coord");
        assertEquals(dest.toWorldCoord(destLoc), world);
        assertNotNull(world);
        assertEquals(1, waits.get(), "must wait once after the initial null dest coordinate");
    }

    @Test
    void waitForDestinationWorldCoordTimesOutWithoutHearth() throws InterruptedException {
        ForagerWaypoint dest = new ForagerWaypoint(2L, new Coord(0, 0));
        MiniMap.Location origin = sessloc(1L);
        AtomicInteger waits = new AtomicInteger();

        Coord2d world = Forager.waitForDestinationWorldCoord(
                dest,
                () -> origin,
                3,
                waits::incrementAndGet);

        assertNull(world);
        assertEquals(2, waits.get());
        Results stop = Forager.resultAfterUnresolvedDestinationMap();
        assertFalse(stop.IsSuccess(), "permanent unresolved dest map must stop the route without hearth");
    }

    @Test
    void waitForDestinationWorldCoordPropagatesInterrupt() {
        ForagerWaypoint dest = new ForagerWaypoint(2L, new Coord(0, 0));
        assertThrows(InterruptedException.class, () ->
                Forager.waitForDestinationWorldCoord(dest, () -> sessloc(1L), 3, () -> {
                    throw new InterruptedException("stop");
                }));
    }

    @Test
    void lateResolvingNearbyLandingIsArrivalOkAndAllowsDestinationSteps() {
        Coord2d destWorld = new Coord2d(100, 200);
        Coord2d playerRc = new Coord2d(105, 200);
        Forager.MilestoneContinuation cont = Forager.continuationAfterMilestoneArrival(destWorld, playerRc);
        assertEquals(UseMilestone.Arrival.OK, cont.arrival);
        assertTrue(cont.runDestinationSteps);
        assertTrue(cont.regenerateSections);
        assertNull(cont.stop, "Arrival.OK must continue the route");
    }

    @Test
    void lateResolvingFarLandingUsesConfirmedWrongPlaceSemantics() {
        Coord2d destWorld = new Coord2d(0, 0);
        Coord2d playerRc = new Coord2d(100_000, 0);
        Forager.MilestoneContinuation cont = Forager.continuationAfterMilestoneArrival(destWorld, playerRc);
        assertEquals(UseMilestone.Arrival.WRONG_PLACE, cont.arrival);
        assertFalse(cont.runDestinationSteps);
        assertFalse(cont.regenerateSections);
        assertNotNull(cont.stop);
        assertFalse(cont.stop.IsSuccess());
        assertFalse(UseMilestone.resultAfterConfirmedWrongPlace(Results.SUCCESS()).IsSuccess(),
                "hearth SUCCESS must still stop the route");
    }

    @Test
    void permanentlyUnresolvedDestinationFailsWithoutHearth() {
        Forager.MilestoneContinuation cont = Forager.continuationAfterMilestoneArrival(null, new Coord2d(0, 0));
        assertEquals(UseMilestone.Arrival.UNRESOLVED, cont.arrival);
        assertFalse(cont.runDestinationSteps);
        assertFalse(cont.regenerateSections);
        assertNotNull(cont.stop);
        assertFalse(cont.stop.IsSuccess());
        assertFalse(Forager.resultAfterUnresolvedDestinationMap().IsSuccess());
    }

    @Test
    void destinationStepsRunOnlyAfterArrivalOk() {
        Forager.MilestoneContinuation ok =
                Forager.continuationAfterMilestoneArrival(new Coord2d(0, 0), new Coord2d(0, 0));
        Forager.MilestoneContinuation wrong =
                Forager.continuationAfterMilestoneArrival(new Coord2d(0, 0), new Coord2d(100_000, 0));
        Forager.MilestoneContinuation unresolved =
                Forager.continuationAfterMilestoneArrival(null, new Coord2d(0, 0));

        assertTrue(ok.runDestinationSteps);
        assertTrue(ok.regenerateSections);
        assertFalse(wrong.runDestinationSteps);
        assertFalse(wrong.regenerateSections);
        assertFalse(unresolved.runDestinationSteps);
        assertFalse(unresolved.regenerateSections);
    }

    @Test
    void failedInitialPathFinderDoesNotEnterSectionLoop() {
        Results fail = Results.FAIL();
        assertFalse(Forager.shouldContinueAfterInitialPathFinder(fail));
        assertFalse(Forager.shouldContinueAfterInitialPathFinder(null));
    }

    @Test
    void successfulInitialPathFinderContinuesToStart() {
        assertTrue(Forager.shouldContinueAfterInitialPathFinder(Results.SUCCESS()));
    }
}
