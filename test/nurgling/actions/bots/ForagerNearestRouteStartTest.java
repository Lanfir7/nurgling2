package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerWaypoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForagerNearestRouteStartTest {

    private static MiniMap.Location sessloc(long segId) {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        return new MiniMap.Location(file.new Segment(segId), new Coord(0, 0));
    }

    private static ForagerPath path(long segId, Coord... tiles) {
        ForagerPath path = new ForagerPath("nearest-start");
        for (Coord tile : tiles) {
            path.addWaypoint(new ForagerWaypoint(segId, tile));
        }
        return path;
    }

    @Test
    void choosesMiddleResolvableWaypointNearestToPlayer() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(5, 0), new Coord(10, 0));
        Coord2d player = path.waypoints.get(1).toWorldCoord(loc).add(1, 0);

        assertEquals(1, Forager.nearestResolvableWaypointIndex(path, loc, player));
    }

    @Test
    void keepsFirstWaypointWhenItIsNearest() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(5, 0), new Coord(10, 0));
        Coord2d player = path.waypoints.get(0).toWorldCoord(loc);

        assertEquals(0, Forager.nearestResolvableWaypointIndex(path, loc, player));
    }

    @Test
    void ignoresWaypointsThatDoNotResolveInCurrentSegment() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(10, 0), new Coord(20, 0));
        path.addWaypoint(new ForagerWaypoint(2L, new Coord(0, 0)));

        assertEquals(0, Forager.nearestResolvableWaypointIndex(path, loc, new Coord2d(0, 0)));
    }

    @Test
    void tiesChooseLowerWaypointIndex() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(2, 0));
        Coord2d player = path.waypoints.get(0).toWorldCoord(loc).add(11, 0);

        assertEquals(0, Forager.nearestResolvableWaypointIndex(path, loc, player));
    }

    @Test
    void missingPlayerPositionFallsBackToFirstWaypoint() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(5, 0));

        assertEquals(0, Forager.nearestResolvableWaypointIndex(path, loc, null));
    }

    @Test
    void startsAtFirstOutgoingSectionForNearestMiddleWaypointAfterLongGap() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(10, 0), new Coord(12, 0));
        path.generateSections(loc);

        int sectionIndex = Forager.firstSectionIndexAtOrAfterWaypoint(path, 1);
        assertEquals(3, sectionIndex, "10-tile first gap must have three sub-sections");
        assertEquals(1, path.getSection(sectionIndex).waypointIndex);
    }

    @Test
    void finalWaypointHasNoOutgoingSections() {
        MiniMap.Location loc = sessloc(1L);
        ForagerPath path = path(1L, new Coord(0, 0), new Coord(10, 0), new Coord(12, 0));
        path.generateSections(loc);

        assertEquals(path.getSectionCount(), Forager.firstSectionIndexAtOrAfterWaypoint(path, 2));
    }
}
