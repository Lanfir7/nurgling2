package nurgling.areas;

import haven.Area;
import haven.Coord;
import haven.MCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NAreaWorldBoundsTest {
    @Test
    void emptyOrUnavailableGridsHaveNoWorldBounds() {
        MCache cache = new MCache(null);
        NArea area = new NArea("Test zone");
        assertNull(area.getArea(cache));

        area.space = new NArea.Space();
        assertNull(area.getArea(cache));

        area.space.space.put(7L, new NArea.VArea(new Area(Coord.of(3, 5), Coord.of(8, 9))));
        assertNull(area.getArea(cache));
    }

    @Test
    void worldBoundsRecoverWhenSavedGridLoads() {
        MCache cache = new MCache(null);
        NArea area = new NArea("Test zone");
        area.space = new NArea.Space();
        area.space.space.put(7L, new NArea.VArea(new Area(Coord.of(3, 5), Coord.of(8, 9))));
        area.space.space.put(8L, new NArea.VArea(new Area(Coord.of(1, 1), Coord.of(3, 4))));

        assertNull(area.getArea(cache));
        MCache.Grid first = cache.new Grid(Coord.of(2, -1));
        first.id = 7L;
        cache.grids.put(first.gc, first);
        assertEquals(new Area(Coord.of(203, -95), Coord.of(208, -91)), area.getArea(cache));

        MCache.Grid second = cache.new Grid(Coord.of(3, -1));
        second.id = 8L;
        cache.grids.put(second.gc, second);
        assertEquals(new Area(Coord.of(203, -99), Coord.of(303, -91)), area.getArea(cache));
    }
}
