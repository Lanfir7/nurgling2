package nurgling.actions.bots.road;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadGeometryTest {
    @Test
    void anchorStepsThreeTilesAlongTheFacing() {
        Coord2d east = RoadGeometry.anchor(Coord2d.of(0, 0), 0, 11);
        Coord2d north = RoadGeometry.anchor(Coord2d.of(0, 0), Math.PI / 2, 11);
        assertEquals(33, east.x, 0.01);
        assertEquals(0, east.y, 0.01);
        assertEquals(0, north.x, 0.01);
        assertEquals(33, north.y, 0.01);
    }

    @Test
    void projectAndPointAtFollowAnElbow() {
        Coord2d[] elbow = {Coord2d.of(0, 0), Coord2d.of(100, 0), Coord2d.of(100, 50)};
        assertEquals(125, RoadGeometry.project(Arrays.asList(elbow), Coord2d.of(100, 25)), 0.01);
        Coord2d mid = RoadGeometry.pointAt(Arrays.asList(elbow), 125);
        assertEquals(100, mid.x, 0.01);
        assertEquals(25, mid.y, 0.01);
        Coord2d end = RoadGeometry.pointAt(Arrays.asList(elbow), 500);
        assertEquals(100, end.x, 0.01);
        assertEquals(50, end.y, 0.01);
        Coord2d dir = RoadGeometry.tangentAt(Arrays.asList(elbow), 0);
        assertEquals(1, dir.x, 0.01);
        assertEquals(0, dir.y, 0.01);
    }

    @Test
    void untilTurnStopsAtTheCorner() {
        Coord2d[] elbow = {Coord2d.of(0, 0), Coord2d.of(110, 0), Coord2d.of(110, 200)};
        assertEquals(110, RoadGeometry.untilTurn(Arrays.asList(elbow), 0, RoadRules.TURN_RADIANS), 0.01);
        Coord2d[] straight = {Coord2d.of(0, 0), Coord2d.of(200, 0), Coord2d.of(400, 0)};
        assertEquals(400, RoadGeometry.untilTurn(Arrays.asList(straight), 0, RoadRules.TURN_RADIANS), 0.01);
    }
}
