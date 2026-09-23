package nurgling.areas;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NAreaLoadedGeometryPlayerSnapshotTest {
    private static final Coord2d BEGIN = new Coord2d(0, 0);
    private static final Coord2d END = new Coord2d(100, 100);

    @Test
    void missingGameUiDoesNotCrashZoneArrow() {
        NArea area = new NArea("zone");
        assertFalse(area.isVisible());
        assertNull(area.getLoadedRCArea(false));
    }

    @Test
    void missingPlayerKeepsLoadedGeometryAvailable() {
        assertFalse(NArea.isOutsidePlayerRange(BEGIN, END, null));
    }

    @Test
    void playerNearEitherAreaCornerKeepsLoadedGeometryAvailable() {
        assertFalse(NArea.isOutsidePlayerRange(BEGIN, END, new Coord2d(200, 200)));
    }

    @Test
    void playerFarFromBothAreaCornersHidesLoadedGeometry() {
        assertTrue(NArea.isOutsidePlayerRange(BEGIN, END, new Coord2d(2_000, 2_000)));
    }
}
