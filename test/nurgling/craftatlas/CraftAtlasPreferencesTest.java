package nurgling.craftatlas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasPreferencesTest {
    @TempDir Path temp;

    @Test
    void roundTripsProfileStateAndBoundsRecentRecipes() throws Exception {
        Path file = temp.resolve("atlas.json");
        CraftAtlasPreferences prefs = new CraftAtlasPreferences();
        prefs.favorites.add("recipe/favorite");
        for(int i = 0; i < 60; i++) prefs.recordRecent("recipe/" + i);
        for(int i = 0; i < 20; i++) prefs.recordSearch("query " + i);
        prefs.favoriteFilter = true;
        prefs.recentFilter = true;
        prefs.craftFilter = true;
        prefs.storageFilter = true;
        prefs.lastSection = "gildings";
        prefs.windowW = 1120;
        prefs.columnWidths.put("gildings.bonus", 140);
        prefs.requirementQualities.put("station:anvil", 87.5);
        prefs.requirementQualities.put("context:cauldron-water", 62.0);
        prefs.save(file);

        CraftAtlasPreferences loaded = CraftAtlasPreferences.load(file);
        assertTrue(loaded.favorites.contains("recipe/favorite"));
        assertEquals(50, loaded.recent.size());
        assertEquals("recipe/59", loaded.recent.get(0));
        assertEquals(CraftAtlasPreferences.SEARCH_HISTORY_LIMIT, loaded.searchHistory.size());
        assertEquals("query 19", loaded.searchHistory.get(0));
        assertTrue(loaded.favoriteFilter);
        assertTrue(loaded.recentFilter);
        assertTrue(loaded.craftFilter);
        assertTrue(loaded.storageFilter);
        assertEquals("gildings", loaded.lastSection);
        assertEquals(Integer.valueOf(140), loaded.columnWidths.get("gildings.bonus"));
        assertEquals(87.5, loaded.requirementQualities.get("station:anvil"));
        assertEquals(62.0, loaded.requirementQualities.get("context:cauldron-water"));
    }

    @Test
    void corruptFileReturnsDefaultsAndIsPreserved() throws Exception {
        Path file = temp.resolve("bad.json");
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        CraftAtlasPreferences loaded = CraftAtlasPreferences.load(file);
        assertTrue(loaded.favorites.isEmpty());
        assertEquals("{broken", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void migratesLegacyWorkstationKeysWithoutReplacingCanonicalOrClayValues() throws Exception {
        Path file = temp.resolve("migrated.json");
        Files.write(file, ("{\"requirementQualities\":{"
                + "\"station:spinning-wheel\":80,\"tool:sswheel\":90,"
                + "\"tool:potterswheel\":70,\"station:winepress\":60,"
                + "\"tool:wiki-item-meatgrinder\":50,\"station:cauldron\":55,"
                + "\"station:clay-cauldron\":44,\"context:cauldron-clay\":1}}")
                .getBytes(StandardCharsets.UTF_8));

        CraftAtlasPreferences loaded = CraftAtlasPreferences.load(file);
        assertEquals(80.0, loaded.requirementQualities.get("station:spinning-wheel"));
        assertEquals(70.0, loaded.requirementQualities.get("station:potters-wheel"));
        assertEquals(60.0, loaded.requirementQualities.get("station:extraction-press"));
        assertEquals(50.0, loaded.requirementQualities.get("station:meatgrinder"));
        assertEquals(55.0, loaded.requirementQualities.get("station:cauldron"));
        assertEquals(44.0, loaded.requirementQualities.get("station:clay-cauldron"));
        assertEquals(1.0, loaded.requirementQualities.get("context:cauldron-clay"));

        loaded.save(file);
        CraftAtlasPreferences reloaded = CraftAtlasPreferences.load(file);
        assertEquals(loaded.requirementQualities, reloaded.requirementQualities);
    }
}
