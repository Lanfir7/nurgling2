package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementSnapTest {
    @Test
    void asymmetricTableEdgeMeetsTheNeighbour() {
        double near = -2.96484375;
        double far = 3.9638671875;
        double wall = 10.0;
        double cursor = PlacementSnap.flushCandidates(near, far, 0.0, wall)[1];
        assertEquals(wall, cursor + near, 1e-9);
    }

    @Test
    void centredBoxMatchesHalfExtentSnap() {
        double[] got = PlacementSnap.flushCandidates(-3.0, 3.0, 0.0, 10.0);
        assertEquals(-3.0, got[0], 1e-9);
        assertEquals(13.0, got[1], 1e-9);
        assertEquals(3.0, got[2], 1e-9);
        assertEquals(7.0, got[3], 1e-9);
    }
}
