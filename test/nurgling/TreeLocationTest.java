package nurgling;

import haven.Coord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeLocationTest {
    @Test
    void mapLabelShowsGrowthNotQuantity() {
        TreeLocation loc = new TreeLocation(1L, new Coord(10, 20), "Oak Tree",
                "gfx/terobjs/trees/oak", 7, 150);
        assertEquals("150%", loc.getMapLabel());
        assertEquals("Oak Tree (150%)", loc.getListLabel());
    }

    @Test
    void labelsOmitQuantityWhenGrowthMissing() {
        TreeLocation loc = new TreeLocation(1L, new Coord(10, 20), "Oak Tree",
                "gfx/terobjs/trees/oak", 7);
        assertEquals("", loc.getMapLabel());
        assertEquals("Oak Tree", loc.getListLabel());
    }

    @Test
    void jsonRoundtripKeepsGrowthPercent() {
        TreeLocation original = new TreeLocation(3L, new Coord(4, 8), "Birch Tree",
                "gfx/terobjs/trees/birch", 2, 220);
        TreeLocation loaded = new TreeLocation(original.toJson());
        assertEquals(220, loaded.getGrowthPercent());
        assertEquals("220%", loaded.getMapLabel());
    }

    @Test
    void jsonWithoutGrowthDefaultsToZero() {
        JSONObject json = new JSONObject();
        json.put("locationId", "tree_1_0_0_Oak_Tree");
        json.put("segmentId", 1L);
        json.put("tileX", 0);
        json.put("tileY", 0);
        json.put("treeName", "Oak Tree");
        json.put("treeResource", "gfx/terobjs/trees/oak");
        json.put("timestamp", 1L);
        json.put("quantity", 4);

        TreeLocation loaded = new TreeLocation(json);
        assertEquals(0, loaded.getGrowthPercent());
        assertEquals("", loaded.getMapLabel());
        assertEquals("Oak Tree", loaded.getListLabel());
    }

    @Test
    void relocatesWithSegmentMerge() {
        TreeLocation loc = new TreeLocation(1L, new Coord(10, 20), "Oak Tree",
                "gfx/terobjs/trees/oak", 7, 150);
        TreeLocation moved = loc.relocated(99L, new Coord(haven.MCache.cmaps.x, 0));
        assertEquals(99L, moved.getSegmentId());
        assertEquals(new Coord(10 - haven.MCache.cmaps.x, 20), moved.getTileCoords());
        assertEquals(150, moved.getGrowthPercent());
        assertEquals(7, moved.getQuantity());
        assertEquals(loc.getTimestamp(), moved.getTimestamp());
        assertTrue(moved.getLocationId().contains("99"));
    }
}
