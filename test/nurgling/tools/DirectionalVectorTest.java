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
}
