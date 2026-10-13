package nurgling.timers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TimerStoreLegacyMigrationTest {
    @TempDir Path temp;

    @Test void anchoredV1TimerKeepsGridAndMetadataWithRecoverableBackup() throws Exception {
        Path file = temp.resolve("resource_timers.nurgling.json");
        JSONObject old = new JSONObject().put("timers", new JSONArray().put(
            new JSONObject().put("resourceId", "res_g_77_3_4_clay")
                .put("segmentId", 91).put("tileX", 103).put("tileY", 204)
                .put("gridId", 77).put("ox", 3).put("oy", 4)
                .put("resourceType", "clay").put("resourceName", "Clay Pit")
                .put("description", "East pit").put("iconRes", "gfx/terobjs/mm/claypit")
                .put("startTime", 1000).put("duration", 60000)));
        String original = old.toString();
        Files.write(file, original.getBytes(StandardCharsets.UTF_8));

        TimerStore store = new TimerStore("world", file.toString());
        assertEquals(1, store.timers().size());
        Timer timer = store.timers().get(0);
        assertEquals(Timer.resourceId("world", 77, 3, 4, "clay"), timer.id);
        assertEquals(77, timer.gridId);
        assertEquals("gfx/terobjs/mm/claypit", timer.icon);
        assertEquals("Clay Pit", timer.resourceName);
        assertEquals("East pit", timer.description);
        assertTrue(timer.shared, "previously shared resource timers must enter the new sync queue");

        store.put(timer);
        assertEquals(original, Files.readString(temp.resolve("resource_timers.nurgling.json.v1.bak")));
        JSONObject migrated = new JSONObject(Files.readString(file));
        assertEquals(2, migrated.getInt("version"));
        Timer loaded = new TimerStore("world", file.toString()).timers().get(0);
        assertEquals(timer.id, loaded.id);
        assertEquals(timer.icon, loaded.icon);
        assertEquals(timer.resourceName, loaded.resourceName);
    }

    @Test void ephemeralV1TimerKeepsRemovalPolicyAndStaysPrivate() throws Exception {
        Path file = temp.resolve("resource_timers.nurgling.json");
        JSONObject old = new JSONObject().put("timers", new JSONArray().put(
            new JSONObject().put("resourceId", "res_1_2_3_leaf")
                .put("segmentId", 1).put("tileX", 2).put("tileY", 3)
                .put("resourceType", "leaf").put("resourceName", "Leaf")
                .put("description", "Sapling").put("iconRes", "gfx/leaf")
                .put("autoRemoveAfterMs", 5000).put("startTime", 1000).put("duration", 1000)));
        Files.writeString(file, old.toString());

        TimerStore store = new TimerStore("world", file.toString());
        Timer timer = store.timers().get(0);
        assertFalse(timer.shared);
        assertEquals(5000, timer.autoRemoveAfterMs);
        store.prune(5999);
        assertEquals(1, store.timers().size());
        store.prune(6000);
        assertTrue(store.timers().isEmpty());
    }

    @Test void oldServerRowCannotOverwriteNewerOfflineRestart() throws Exception {
        Path file = temp.resolve("resource_timers.nurgling.json");
        Timer local = new Timer(Timer.resourceId("world", 77, 3, 4, "clay"),
            Timer.Kind.RESOURCE, 77, 3, 4, "clay", "Clay Pit", null,
            20_000, 60_000, 0, null, true, 0, 0, null);
        Files.writeString(file, new JSONObject().put("version", 2)
            .put("timers", new JSONArray().put(local.toJson())).toString());
        TimerStore store = new TimerStore("world", file.toString());

        store.applyRemote(List.of(local.restarted(10_000, 60_000).withVersion(1)),
            Set.of(), true, name -> false);

        assertEquals(20_000, store.get(local.id).startedAt);
        assertEquals(1, store.pendingUpserts().size());
    }

    @Test void serverRefreshDoesNotEraseLocalLegacyIconAndDescription() throws Exception {
        Path file = temp.resolve("resource_timers.nurgling.json");
        Timer local = new Timer(Timer.resourceId("world", 77, 3, 4, "clay"),
            Timer.Kind.RESOURCE, 77, 3, 4, "clay", "East pit", "gfx/old/icon",
            20_000, 60_000, 0, null, true, 1, 91, haven.Coord.of(103, 204),
            0, "", "Clay Pit", "East pit", 0);
        Files.writeString(file, new JSONObject().put("version", 2)
            .put("timers", new JSONArray().put(local.toJson())).toString());
        TimerStore store = new TimerStore("world", file.toString());
        Timer remote = new Timer(local.id, Timer.Kind.RESOURCE, 77, 3, 4,
            "clay", "East pit", null, 20_000, 60_000, 0, null, true, 2, 0, null);

        store.applyRemote(List.of(remote), Set.of(), true, name -> false);

        assertEquals("gfx/old/icon", store.get(local.id).icon);
        assertEquals("Clay Pit", store.get(local.id).resourceName);
        assertEquals("East pit", store.get(local.id).description);
    }

    @Test void unanchoredResourceNeverUploadsLongLegacyIdBeforePromotion() {
        Path file = temp.resolve("resource_timers.nurgling.json");
        TimerStore store = new TimerStore("world", file.toString());
        String oldId = "res_91_103_204_" + "very_long_resource_path_".repeat(5);
        Timer old = new Timer(oldId, Timer.Kind.RESOURCE, 0, 0, 0,
            "gfx/terobjs/mm/claypit", "Clay Pit", null, 10_000, 60_000,
            0, null, true, 0, 91, haven.Coord.of(103, 204));
        store.put(old);

        assertTrue(oldId.length() > 64);
        assertTrue(store.pendingUpserts().isEmpty(), "unanchored resource cannot enter timers.id");

        store.promoteLegacy(oldId, 77, 3, 4);

        assertNull(store.get(oldId));
        assertEquals(1, store.pendingUpserts().size());
        Timer promoted = store.pendingUpserts().get(0).item;
        assertEquals(Timer.resourceId("world", 77, 3, 4, old.resType), promoted.id);
        assertEquals(36, promoted.id.length());
    }
}
