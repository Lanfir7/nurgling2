package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTableTimesCatalogTest {

    @Test
    void wikiSnapshotHasSeventeenUniqueItems() {
        List<HTableTimesCatalog.Entry> entries = HTableTimesCatalog.all();
        assertEquals(17, entries.size());
        Set<String> names = new HashSet<>();
        for (HTableTimesCatalog.Entry entry : entries) {
            assertTrue(names.add(entry.item), "duplicate item: " + entry.item);
            assertFalse(entry.product.isEmpty(), "missing product: " + entry.item);
            assertFalse(entry.realTime.isEmpty(), "missing real time: " + entry.item);
            assertFalse(entry.inGameTime.isEmpty(), "missing in-game time: " + entry.item);
        }
    }

    @Test
    void grapesAndTeaLeavesMatchWiki() {
        HTableTimesCatalog.Entry grapes = HTableTimesCatalog.find("Grapes");
        assertNotNull(grapes);
        assertEquals("Raisins", grapes.product);
        assertEquals("00:50:27", grapes.realTime);
        assertEquals("02:46:00", grapes.inGameTime);

        HTableTimesCatalog.Entry tea = HTableTimesCatalog.find("Tea Leaves");
        assertNotNull(tea);
        assertEquals("Green Tea Leaves", tea.product);
        assertEquals("00:36:28", tea.realTime);
        assertEquals("02:00:00", tea.inGameTime);
    }

    @Test
    void remainingWikiRows() {
        assertRow("Bat Wings", "Dried Batwings", "21:53:04", "72:00:00");
        assertRow("Boiled Pepper Drupes", "Dried Pepper Drupes", "36:28:27", "120:00:00");
        assertRow("Camomile", "Dried Camomile", "58:21:31", "192:00:00");
        assertRow("Fresh Hemp Bud", "Cured Hemp Bud", "36:28:27", "120:00:00");
        assertRow("Fresh Leaf of Pipeweed", "Cured Pipeweed", "36:28:27", "120:00:00");
        assertRow("Green Tea Leaves", "Black Tea Leaves", "14:35:23", "48:00:00");
        assertRow("Morels", "Dried Morels", "29:10:46", "96:00:00");
        assertRow("Seeds of Barley", "Seeds of Sprouted Barley", "14:35:23", "48:00:00");
        assertRow("Seeds of Wheat", "Seeds of Sprouted Wheat", "14:35:23", "48:00:00");
        assertRow("Seeds of Millet", "Seeds of Sprouted Millet", "14:35:23", "48:00:00");
        assertRow("Silkworm Egg", "Silkworm", "07:17:41", "24:00:00");
        assertRow("Seasponge", "Sponge", "29:11:00", "96:00:00");
        assertRow("Treeplanter's Pot", "Sprouted Sapling", "01:12:57", "04:00:00");
        assertRow("Wild Windsown Weed", "Seed", "02:25:54", "08:00:00");
        assertRow("Wet Pearl Glue", "Pearl Glue", "51:03:00", "168:00:00");
    }

    private static void assertRow(String item, String product, String real, String ingame) {
        HTableTimesCatalog.Entry entry = HTableTimesCatalog.find(item);
        assertNotNull(entry, item);
        assertEquals(product, entry.product, item);
        assertEquals(real, entry.realTime, item);
        assertEquals(ingame, entry.inGameTime, item);
    }
}
