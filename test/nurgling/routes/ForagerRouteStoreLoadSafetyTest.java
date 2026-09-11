package nurgling.routes;

import nurgling.widgets.nsettings.ForagerSettingsPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerRouteStoreLoadSafetyTest {

    @Test
    void corruptRouteLoadCannotBeAutosavedAsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keep-me.json");
        byte[] original = "{this is not valid forager route json".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);

        ForagerRouteStore.LoadResult loaded = ForagerRouteStore.loadFromFile("keep-me", file.toString());

        assertTrue(loaded.failed(), "parse/read failure must stay distinguishable from a new empty route");
        assertFalse(loaded.canSave(), "a failed load must not look saveable");
        assertFalse(ForagerSettingsPanel.shouldSaveCurrentRoute(loaded.path(), loaded.failed()),
                "ForagerSettingsPanel change/autosave must not persist a failed load");

        // Mirrors routeDropbox.change() / Panel.save() calling saveCurrentRoute().
        if (ForagerSettingsPanel.shouldSaveCurrentRoute(loaded.path(), loaded.failed())) {
            loaded.path().save(dir.toString());
        }

        assertArrayEquals(original, Files.readAllBytes(file),
                "existing broken route JSON must not be replaced by an empty named route");
    }

    @Test
    void unreadableRouteFileIsNotReplacedByEmptySave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("locked.json");
        Files.createDirectories(file);

        ForagerRouteStore.LoadResult loaded = ForagerRouteStore.loadFromFile("locked", file.toString());

        assertTrue(loaded.failed());
        assertFalse(ForagerSettingsPanel.shouldSaveCurrentRoute(loaded.path(), loaded.failed()));
        assertTrue(Files.isDirectory(file), "unreadable path must not be replaced by an empty route file");
    }

    @Test
    void newEmptyRouteStillSaves(@TempDir Path dir) throws Exception {
        ForagerPath created = new ForagerPath("fresh");
        assertTrue(ForagerSettingsPanel.shouldSaveCurrentRoute(created, false));

        created.save(dir.toString());

        Path file = dir.resolve("fresh.json");
        assertTrue(Files.isRegularFile(file));

        ForagerRouteStore.LoadResult reloaded = ForagerRouteStore.loadFromFile("fresh", file.toString());
        assertFalse(reloaded.failed());
        assertTrue(reloaded.canSave());
        assertNotNull(reloaded.path());
        assertEquals("fresh", reloaded.path().name);
        assertTrue(reloaded.path().waypoints.isEmpty());
    }
}
