package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreesTest {
    @Test
    void parsesNameResourceAndTerrainArray() {
        List<Trees.Entry> entries = Trees.parse("[{"
                + "\"name\":\"Almond Tree\","
                + "\"resource\":\"gfx/terobjs/trees/almondtree\","
                + "\"terrains\":[\"Blue Sod\",\"Deep Tangle\"]}]");

        assertEquals(1, entries.size());
        Trees.Entry entry = entries.get(0);
        assertEquals("Almond Tree", entry.name);
        assertEquals("gfx/terobjs/trees/almondtree", entry.resource);
        assertEquals(List.of("Blue Sod", "Deep Tangle"), entry.terrains);
    }

    @Test
    void parsesLegacyTerrainStringIntoBiomeList() {
        List<Trees.Entry> entries = Trees.parse("[{"
                + "\"name\":\"Oak Tree\","
                + "\"terrain\":\"Bounty Acre Blue Sod Oak Wilds\"}]");

        assertEquals(List.of("Bounty Acre", "Blue Sod", "Oak Wilds"), entries.get(0).terrains);
    }

    @Test
    void skipsNamelessEntriesAndEmptyJson() {
        assertTrue(Trees.parse(null).isEmpty());
        assertTrue(Trees.parse("").isEmpty());
        assertTrue(Trees.parse("[{\"terrains\":[\"Forest\"]}]").isEmpty());
    }

    @Test
    void bundledWikiDataCoversAlmondAndLooksUpNameVariants() {
        List<Trees.Entry> entries = Trees.all();
        assertTrue(entries.size() >= 80, "expected Category:Trees coverage, got " + entries.size());

        Set<String> names = new HashSet<>();
        for(Trees.Entry entry : entries)
            assertTrue(names.add(entry.name), "duplicate tree: " + entry.name);

        Trees.Entry almond = Trees.find("Almond Tree");
        assertNotNull(almond);
        assertEquals("gfx/terobjs/trees/almondtree", almond.resource);
        assertTrue(almond.terrains.contains("Deep Tangle"));
        assertTrue(almond.terrains.contains("Blue Sod"));
        assertEquals(almond, Trees.find("almond tree"));
        assertEquals(almond, Trees.find("almond"));
        assertEquals(almond, Trees.findByResource("gfx/terobjs/trees/almondtree"));
    }

    @Test
    void findsWikiNameVariantsAndUnknownIsSafe() {
        assertNotNull(Trees.find("bird cherry tree"));
        assertNotNull(Trees.find("Dwarf Pine"));
        assertEquals("gfx/terobjs/trees/gnomeshat", Trees.find("gnome's cap tree").resource);
        assertNull(Trees.find("not a real tree"));
        assertNull(Trees.find(""));
        assertNull(Trees.find(null));
        assertNull(Trees.findByResource("gfx/terobjs/trees/missing"));
    }

    @Test
    void emptyTerrainTreesStayLoadableWithoutTerrains() {
        Trees.Entry mallorn = Trees.find("Mallorn Tree");
        assertNotNull(mallorn);
        assertTrue(mallorn.terrains.isEmpty());
    }
}
