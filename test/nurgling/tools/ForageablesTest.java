package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForageablesTest {
    @Test
    void visibilityUsesFirstAndAllSeenBoundaries() {
        assertEquals(Forageables.Visibility.RED, Forageables.visibility(4, 5, 20));
        assertEquals(Forageables.Visibility.YELLOW, Forageables.visibility(5, 5, 20));
        assertEquals(Forageables.Visibility.YELLOW, Forageables.visibility(19, 5, 20));
        assertEquals(Forageables.Visibility.GREEN, Forageables.visibility(20, 5, 20));
    }

    @Test
    void parsesThresholdSeasonAndTerrainArrayFields() {
        List<Forageables.Entry> entries = Forageables.parse("[{"
                + "\"name\":\"Bloated Bolete\",\"first\":110,\"base\":220,\"all\":440,"
                + "\"icon\":\"gfx/invobjs/herbs/bloatedbolete\","
                + "\"terrains\":[\"Forest\",\"Grassland\"],\"spring\":\"Y\",\"summer\":\"Y\","
                + "\"autumn\":\"Y\",\"winter\":\"(Y)\"}]");

        assertEquals(1, entries.size());
        Forageables.Entry entry = entries.get(0);
        assertEquals("Bloated Bolete", entry.name);
        assertEquals(110, entry.first);
        assertEquals(220, entry.base);
        assertEquals(440, entry.all);
        assertEquals("gfx/invobjs/herbs/bloatedbolete", entry.icon);
        assertEquals(List.of("Forest", "Grassland"), entry.terrains);
        assertEquals("Forest, Grassland", entry.terrainText());
        assertEquals("(Y)", entry.winter);
    }

    @Test
    void parsesLegacyTerrainStringIntoBiomeList() {
        List<Forageables.Entry> entries = Forageables.parse("[{"
                + "\"name\":\"Coltsfoot\",\"first\":10,\"base\":20,\"all\":40,"
                + "\"terrain\":\"Leaf Patch Shady Copse Green Brake Ox Pasture\"}]");

        assertEquals(List.of("Leaf Patch", "Shady Copse", "Green Brake", "Ox Pasture"),
                entries.get(0).terrains);
        assertEquals("Leaf Patch, Shady Copse, Green Brake, Ox Pasture",
                entries.get(0).terrainText());
    }

    @Test
    void bundledWikiDataIsCompleteUniqueAndOrdered() {
        List<Forageables.Entry> entries = Forageables.all();
        assertEquals(92, entries.size());

        Set<String> names = new HashSet<>();
        for(Forageables.Entry entry : entries) {
            assertTrue(names.add(entry.name), "duplicate forageable: " + entry.name);
            assertTrue(entry.icon.startsWith("gfx/"), "missing icon: " + entry.name);
            assertTrue(entry.first <= entry.base, entry.name + " first > base");
            assertTrue(entry.base <= entry.all, entry.name + " base > all");
        }

        Forageables.Entry blueberries = entries.stream()
                .filter(entry -> entry.name.equals("Blueberries"))
                .findFirst().orElse(null);
        assertNotNull(blueberries);
        assertEquals("gfx/invobjs/herbs/blueberry", blueberries.icon);
        assertEquals("N", blueberries.spring);
        assertEquals("Y", blueberries.summer);
        assertEquals("Y", blueberries.autumn);
        assertEquals("N", blueberries.winter);
    }
}
