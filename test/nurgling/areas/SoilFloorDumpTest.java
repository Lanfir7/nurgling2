package nurgling.areas;

import haven.Coord2d;
import haven.Pair;
import nurgling.widgets.Specialisation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilFloorDumpTest {
    @Test
    void soilOnPutZoneWithSoilDumpDropsOnFloor() {
        NArea area = areaWithSpec(Specialisation.SpecName.soilDump.toString());
        assertTrue(SoilFloorDump.shouldDump("Soil", area));
    }

    @Test
    void soilWithoutDumpSpecStillGoesToPiles() {
        NArea area = new NArea("soil put");
        assertFalse(SoilFloorDump.shouldDump("Soil", area));
    }

    @Test
    void earthwormOnDumpZoneStillGoesToPiles() {
        NArea area = areaWithSpec(Specialisation.SpecName.soilDump.toString());
        assertFalse(SoilFloorDump.shouldDump("Earthworm", area));
    }

    @Test
    void mulchOnDumpZoneStillGoesToPiles() {
        NArea area = areaWithSpec(Specialisation.SpecName.soilDump.toString());
        assertFalse(SoilFloorDump.shouldDump("Mulch", area));
    }

    @Test
    void nullAreaDoesNotDump() {
        assertFalse(SoilFloorDump.shouldDump("Soil", null));
    }

    @Test
    void dumpCenterIsMidpointOfArea() {
        Pair<Coord2d, Coord2d> rca = new Pair<>(new Coord2d(0, 0), new Coord2d(10, 20));
        Coord2d center = SoilFloorDump.center(rca);
        assertEquals(5.0, center.x, 0.001);
        assertEquals(10.0, center.y, 0.001);
    }

    private static NArea areaWithSpec(String specName) {
        NArea area = new NArea("dump");
        area.spec.add(new NArea.Specialisation(specName));
        return area;
    }
}
