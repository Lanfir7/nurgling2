package nurgling;

import haven.Coord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
