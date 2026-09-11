package nurgling.routes;

import haven.Coord;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerPathGenerateSectionsTest {

    private static MiniMap.Location sessloc(long segId) {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        return new MiniMap.Location(file.new Segment(segId), new Coord(0, 0));
    }

    private static ForagerWaypoint wp(long seg, int x, int y) {
        return new ForagerWaypoint(seg, new Coord(x, y));
    }

    private static ForagerWaypoint spliceWp(long seg, int x, int y, String hash) {
        ForagerWaypoint waypoint = wp(seg, x, y);
        waypoint.milestoneHash = hash;
        return waypoint;
    }

    private static ForagerPath crossSegmentSplicePath() {
        ForagerPath path = new ForagerPath("splice");
        path.addWaypoint(wp(1L, 0, 0));
        path.addWaypoint(spliceWp(1L, 1, 0, "hash-a"));
        path.addWaypoint(spliceWp(2L, 0, 0, "hash-a"));
        path.addWaypoint(wp(2L, 2, 0));
        return path;
    }

    @Test
    void generateSectionsDoesNotNpeWhenGameUiIsMissing() {
        ForagerPath path = crossSegmentSplicePath();
        path.generateSections();
    }

    @Test
    void sameHashCrossSegmentSpliceCreatesSectionWhenDestWorldCoordIsNull() {
        ForagerPath path = crossSegmentSplicePath();
        path.generateSections(sessloc(1L));

        assertTrue(path.getSectionCount() >= 2, "origin walk plus splice");
        boolean foundSplice = false;
        for (int i = 0; i < path.getSectionCount(); i++) {
            ForagerSection section = path.getSection(i);
            if (section.waypointIndex == 1 && section.isLastInGap) {
                foundSplice = true;
                break;
            }
        }
        assertTrue(foundSplice, "same-hash splice must stay executable even when dest toWorldCoord is null");
    }

    @Test
    void destSegmentRegenerationCreatesWalkingSectionsAfterSplice() {
        ForagerPath path = crossSegmentSplicePath();
        path.generateSections(sessloc(2L));

        boolean foundDestWalk = false;
        for (int i = 0; i < path.getSectionCount(); i++) {
            ForagerSection section = path.getSection(i);
            if (section.waypointIndex == 2) {
                foundDestWalk = true;
                break;
            }
        }
        assertTrue(foundDestWalk, "after teleport, dest-segment pairs must become walking sections");
    }
}
