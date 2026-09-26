package nurgling.tools;

import org.junit.jupiter.api.Test;
import haven.res.lib.itemtex.ItemTex;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecGemstonesTest {
    @Test
    void everyCutAndSizeBelongsToItsKindSizeAndOverallGroup() {
        String[] kinds = {"Amber", "Amethyst", "Diamond", "Dust Jewel", "Emerald", "Jade",
                "Moonstone", "Onyx", "Opal", "Red Coral", "Ruby", "Sapphire",
                "Star Shard", "Sugar Diamond", "Topaz", "Turquoise"};
        String[] sizes = {"Tiny", "Small", "Fair", "Large", "Grand", "Jotun"};
        String[] cuts = {"Rough", "Smooth", "Cabochon", "Pear", "Heart", "Brilliant"};
        for (String kind : kinds)
            for (String size : sizes)
                for (String cut : cuts) {
                    String name = size + " " + cut + " " + kind;
                    List<String> groups = VSpec.getCategory(name);
                    assertTrue(groups.contains("Gemstone - " + kind), name);
                    assertTrue(groups.contains("Gemstones - " + size), name);
                    assertTrue(groups.contains("Gemstones"), name);
                }
        assertEquals(kinds.length * sizes.length * cuts.length + 3,
                VSpec.getCategoryContent("Gemstones").size());
    }

    @Test
    void pearlsAreOnlyInOverallAndTheirOwnGroups() {
        for (String pearl : List.of("Oyster Pearl", "Pink Pearl", "River Pearl")) {
            assertTrue(VSpec.getCategory(pearl).contains("Gemstone - " + pearl));
            assertTrue(VSpec.getCategory(pearl).contains("Gemstones"));
            assertFalse(VSpec.getCategory(pearl).contains("Gemstones - Tiny"));
        }
    }

    @Test
    void sharedGemResourceDoesNotMakeEveryGemMatchEveryGroup() {
        List<String> groups = VSpec.categoriesFor(
                Collections.singleton("Tiny Smooth Moonstone"),
                Collections.singleton("gfx/invobjs/gem/gemstone"));
        assertTrue(groups.contains("Gemstone - Moonstone"));
        assertTrue(groups.contains("Gemstones - Tiny"));
        assertFalse(groups.contains("Gemstone - Ruby"));
        assertFalse(groups.contains("Gemstones - Large"));
        assertFalse(VSpec.categoriesFor(Collections.emptyList(),
                Collections.singleton("gfx/invobjs/gem/gemstone")).contains("Gemstones - Tiny"));
    }

    @Test
    void gemstoneEntriesHaveALoadablePreviewDespiteSharedDynamicResource() {
        JSONObject entry = VSpec.categories.get("Gemstone - Moonstone").get(0);
        assertEquals("gfx/invobjs/gem/gemstone", entry.getString("static"));
        assertEquals("mm/gem", entry.getString("preview"));
        assertTrue(ItemTex.create(entry).getWidth() > 0);
    }
}
