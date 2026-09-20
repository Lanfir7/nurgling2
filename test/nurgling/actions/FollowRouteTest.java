package nurgling.actions;

import haven.Coord;
import haven.Coord2d;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerSection;
import nurgling.routes.ForagerWaypoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FollowRouteTest {
    private static MiniMap.Location sessloc(long segment) {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        return new MiniMap.Location(file.new Segment(segment), Coord.z);
    }

    @Test
    void intermediateLongGapUsesSectionEndInsteadOfFinalWaypoint() {
        ForagerPath path = new ForagerPath("long");
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(0, 0)));
        path.addWaypoint(new ForagerWaypoint(1L, new Coord(10, 0)));
        MiniMap.Location location = sessloc(1L);
        path.generateSections(location);
        ForagerSection first = path.getSection(0);
        assertFalse(first.isLastInGap);
        assertSame(first.endPoint,
                FollowRoute.resolveSectionWalkTarget(first, path.waypoints.get(1), location));
    }

    @Test
    void nearestWaypointMustResolveOnCurrentSegment() {
        ForagerPath path = new ForagerPath("elsewhere");
        path.addWaypoint(new ForagerWaypoint(2L, Coord.z));
        path.addWaypoint(new ForagerWaypoint(2L, Coord.of(1, 0)));
        assertEquals(-1, FollowRoute.nearestResolvableWaypointIndex(
                path, sessloc(1L), new Coord2d(0, 0)));
    }

    @Test
    void destinationPollWaitsForNewSegmentAndPropagatesInterrupt() throws Exception {
        ForagerWaypoint destination = new ForagerWaypoint(2L, Coord.z);
        MiniMap.Location origin = sessloc(1L);
        MiniMap.Location target = sessloc(2L);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();
        Coord2d resolved = FollowRoute.waitForDestinationWorldCoord(destination,
                () -> reads.getAndIncrement() == 0 ? origin : target,
                4, waits::incrementAndGet);
        assertEquals(destination.toWorldCoord(target), resolved);
        assertEquals(1, waits.get());

        assertThrows(InterruptedException.class, () ->
                FollowRoute.waitForDestinationWorldCoord(destination, () -> origin, 2,
                        () -> { throw new InterruptedException("stop"); }));
    }

    @Test
    void regeneratedRouteContinuesAfterCompletedMilestoneSplice() {
        ForagerPath path = new ForagerPath("milestone");
        path.addWaypoint(new ForagerWaypoint(1L, Coord.z));
        ForagerWaypoint from = new ForagerWaypoint(1L, Coord.of(1, 0));
        from.milestoneHash = "road";
        path.addWaypoint(from);
        ForagerWaypoint to = new ForagerWaypoint(2L, Coord.z);
        to.milestoneHash = "road";
        path.addWaypoint(to);
        path.addWaypoint(new ForagerWaypoint(2L, Coord.of(2, 0)));
        path.generateSections(sessloc(2L));

        int next = FollowRoute.nextSectionIndexAfterTeleport(path, 1);
        assertTrue(next < path.getSectionCount());
        assertEquals(2, path.getSection(next).waypointIndex);
        assertTrue(FollowRoute.isMilestoneSplice(from, to));
    }

    @Test
    void unresolvedTailCannotBeReportedAsComplete() {
        ForagerPath path = new ForagerPath("split-without-milestone");
        path.addWaypoint(new ForagerWaypoint(1L, Coord.z));
        path.addWaypoint(new ForagerWaypoint(1L, Coord.of(1, 0)));
        path.addWaypoint(new ForagerWaypoint(2L, Coord.z));

        assertFalse(FollowRoute.routeCompleted(path, 1));
        assertTrue(FollowRoute.routeCompleted(path, 2));
    }
}
