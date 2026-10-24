package nurgling.widgets.storage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class StashLayoutSeedMigrationTest {
    @TempDir Path temp;

    private static JSONObject tab(String id, String preset, String name, int target) {
        JSONObject slot = new JSONObject().put("name", name).put("target", target);
        JSONObject section = new JSONObject().put("title", "Seeds").put("slots", new JSONArray().put(slot));
        JSONObject tab = new JSONObject().put("id", id).put("title", id).put("pinned", true)
                .put("sections", new JSONArray().put(section));
        if (preset != null)
            tab.put("preset", preset);
        return tab;
    }

    @Test
    void savedOldSlotMigratesWithoutLosingTargetOrCustomTab() throws Exception {
        Path file = temp.resolve("tabs.json");
        JSONObject fixture = new JSONObject().put("version", 1)
                .put("tabs", new JSONArray()
                        .put(tab("user-custom", null, "Barley Seed", 77))
                        .put(tab("preset-garden", "garden@1", "Carrot", 23)))
                .put("dismissedPresets", new JSONArray().put("preset-mine"));
        Files.writeString(file, fixture.toString(), StandardCharsets.UTF_8);

        StashLayout layout = StashLayout.load(file.toString());
        assertEquals("user-custom", layout.tabs.get(0).id);
        assertEquals(77, layout.tabs.get(0).find("Barley Seeds").target);
        assertEquals("preset-garden", layout.tabs.get(1).id);
        StashLayout.Tab newGarden = layout.newPresets().stream()
                .filter(t -> "garden@2".equals(t.preset)).findFirst().orElseThrow();
        assertNotNull(newGarden.find("Wheat Seeds"));
        assertEquals(2, layout.tabs.size(), "new preset is offered, not silently installed");

        layout.addPresets(java.util.List.of(newGarden));
        assertEquals("user-custom", layout.tabs.get(0).id);
        assertEquals(77, layout.tabs.get(0).find("Barley Seeds").target);
        assertEquals("garden@2", layout.tabs.get(1).preset);
        layout.save();
        StashLayout reloaded = StashLayout.load(file.toString());
        assertEquals("user-custom", reloaded.tabs.get(0).id);
        assertEquals(77, reloaded.tabs.get(0).find("Barley Seeds").target);
        assertTrue(Files.readString(file).contains("preset-mine"), "dismissed preset list survives");
    }

    @Test
    void importedOldExportMigratesNameButPreservesTargetAndOrder() {
        StashLayout layout = StashLayout.load(temp.resolve("missing-tabs.json").toString());
        JSONObject old = tab("old-id", null, "Barley Seed", 64);
        String encoded = Base64.getEncoder().encodeToString(old.toString().getBytes(StandardCharsets.UTF_8));
        StashLayout.Tab imported = layout.importTab("nstash1:" + encoded);
        assertNotNull(imported);
        assertTrue(imported.id.startsWith("user-"));
        assertNull(imported.preset);
        assertEquals(64, imported.find("Barley Seeds").target);
        assertEquals("Barley Seeds", imported.slots().get(0).name);
        assertTrue(StashLayout.export(imported).startsWith("nstash1:"));
    }
}
