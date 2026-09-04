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
        prefs.lastSection = "gildings";
        prefs.windowW = 1120;
        prefs.columnWidths.put("gildings.bonus", 140);
        prefs.save(file);

        CraftAtlasPreferences loaded = CraftAtlasPreferences.load(file);
        assertTrue(loaded.favorites.contains("recipe/favorite"));
        assertEquals(50, loaded.recent.size());
        assertEquals("recipe/59", loaded.recent.get(0));
        assertEquals("gildings", loaded.lastSection);
        assertEquals(Integer.valueOf(140), loaded.columnWidths.get("gildings.bonus"));
    }

    @Test
    void corruptFileReturnsDefaultsAndIsPreserved() throws Exception {
        Path file = temp.resolve("bad.json");
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        CraftAtlasPreferences loaded = CraftAtlasPreferences.load(file);
        assertTrue(loaded.favorites.isEmpty());
        assertEquals("{broken", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
