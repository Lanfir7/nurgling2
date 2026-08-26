package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProspectingLocationTest {
    @Test
    void relocatesWithSegmentMerge() {
        ProspectingLocation loc = new ProspectingLocation(1L, new Coord(10, 20), "diabase");
        ProspectingLocation moved = loc.relocated(99L, new Coord(haven.MCache.cmaps.x, 0));
        assertEquals(99L, moved.getSegmentId());
        assertEquals(new Coord(10 - haven.MCache.cmaps.x, 20), moved.getTileCoords());
        assertEquals("diabase", moved.getResourceType());
        assertEquals(loc.getTimestamp(), moved.getTimestamp());
        assertTrue(moved.getLocationId().contains("99"));
    }
}
