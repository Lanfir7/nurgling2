package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalVectorTest {
    private static final Coord ORIGIN = new Coord(10, 20);
    private static final Coord TARGET = new Coord(40, 60);

    @Test
    void defaultConstructorHasNoEndpoint() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Ekhagen", -1);
        assertFalse(v.showEndpoint);
        assertEquals(v.getTilePointAt(10000), v.mapEndTile(10000));
    }

    @Test
    void colorConstructorHasNoEndpoint() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Dowse Edge 1", -1, Color.RED);
        assertFalse(v.showEndpoint);
    }

    @Test
    void endpointConstructorStopsAtTarget() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Ekhagen", -1, true);
        assertTrue(v.showEndpoint);
        assertEquals(new haven.Coord2d(TARGET), v.mapEndTile(10000));
    }

    @Test
    void pointerMarkShowsCrossWhenDistanceIsAtMost950() {
        Coord origin = new Coord(0, 0);
        assertTrue(DirectionalVector.showEndpoint(origin, new Coord(950, 0)));
        DirectionalVector v = DirectionalVector.forPointer(origin, new Coord(400, 0), "Oak Tree", 12);
        assertTrue(v.showEndpoint);
        assertEquals(new haven.Coord2d(400, 0), v.mapEndTile(10000));
    }

    @Test
    void pointerMarkBecomesInfiniteRayWhenDistanceExceeds950() {
        Coord origin = new Coord(0, 0);
        assertFalse(DirectionalVector.showEndpoint(origin, new Coord(951, 0)));
        DirectionalVector v = DirectionalVector.forPointer(origin, new Coord(1200, 0), "Oak Tree", -1);
        assertFalse(v.showEndpoint);
        assertEquals(v.getTilePointAt(10000), v.mapEndTile(10000));
    }
}
