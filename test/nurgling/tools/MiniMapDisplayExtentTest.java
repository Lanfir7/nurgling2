package nurgling.tools;

import haven.Area;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiniMapDisplayExtentTest {
    @Test
    void markerPassSkippedWhenCacheInvalidated() {
        assertFalse(MiniMapDisplayExtent.canIterate(null, null));
        assertFalse(MiniMapDisplayExtent.canIterate(null, new Object[1]));
        assertFalse(MiniMapDisplayExtent.canIterate(Area.sized(haven.Coord.of(0, 0), haven.Coord.of(1, 1)), null));
    }

    @Test
    void markerPassRunsWhenExtentAndDisplayExist() {
        Area extent = Area.sized(haven.Coord.of(0, 0), haven.Coord.of(1, 1));
        assertTrue(MiniMapDisplayExtent.canIterate(extent, new Object[1]));
    }
}
