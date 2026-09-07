package nurgling.hotkeys.presets;

import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyPresetStoreTest {
    @Test void storeRoundTripsUserPresetsAndSelection(@TempDir Path dir) throws Exception {
        HotkeyPresetStore store = new HotkeyPresetStore(dir.resolve("hotkey-presets.json"));
        HotkeyPreset user = userPreset("user-1", "Mine", "item.take", InputGesture.none());
        HotkeyPresetLibrary expected = new HotkeyPresetLibrary("user-1", Collections.singletonList(user));

        store.save(expected);
        HotkeyPresetStore.LoadResult loaded = store.load();

        assertFalse(loaded.migrationRequired());
        assertNull(loaded.warningKey());
        assertEquals(expected, loaded.library());
        assertTrue(Files.exists(dir.resolve("hotkey-presets.json")));
    }

    @Test void missingAndCorruptFilesRequestMigrationWithoutOverwriting(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hotkey-presets.json");
        HotkeyPresetStore store = new HotkeyPresetStore(file);
        assertTrue(store.load().migrationRequired());

        Files.write(file, "not-json".getBytes(StandardCharsets.UTF_8));
        HotkeyPresetStore.LoadResult corrupt = store.load();

        assertTrue(corrupt.migrationRequired());
        assertEquals("hotkeys.presets.warning.corrupt", corrupt.warningKey());
        assertEquals("not-json", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test void persistentLibraryRejectsBuiltIns() {
        HotkeyPreset builtIn = new HotkeyPreset(HotkeyPresetCatalog.DEFAULT_ID, "Default", true,
                Collections.emptyMap());
        assertThrows(IllegalArgumentException.class,
                () -> new HotkeyPresetLibrary(HotkeyPresetCatalog.DEFAULT_ID,
                        Collections.singletonList(builtIn)));
    }

    @Test void checkpointRestoresMissingFileAndOriginalBytes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hotkey-presets.json");
        HotkeyPresetStore store = new HotkeyPresetStore(file);
        HotkeyPresetRepository.Checkpoint missing = store.checkpoint();

        store.save(new HotkeyPresetLibrary("user-1", Collections.singletonList(
                userPreset("user-1", "Mine", "item.take", InputGesture.none()))));
        store.restore(missing);
        assertFalse(Files.exists(file));

        byte[] original = "original-corrupt-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        HotkeyPresetRepository.Checkpoint corrupt = store.checkpoint();
        Files.write(file, "replacement".getBytes(StandardCharsets.UTF_8));
        store.restore(corrupt);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void duplicateBindingsInStoredJsonAreRejected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hotkey-presets.json");
        String json = "{\"version\":1,\"selectedPresetId\":\"user-1\",\"presets\":[" +
                "{\"id\":\"user-1\",\"name\":\"Mine\",\"bindings\":[" +
                "{\"id\":\"item.take\",\"gesture\":\"n\"}," +
                "{\"id\":\"item.take\",\"gesture\":\"n\"}]}]}";
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));

        HotkeyPresetStore.LoadResult loaded = new HotkeyPresetStore(file).load();

        assertTrue(loaded.migrationRequired());
        assertEquals("hotkeys.presets.warning.corrupt", loaded.warningKey());
    }

    @Test void saveRejectsLibrariesLargerThanTheLoadLimit(@TempDir Path dir) {
        Path file = dir.resolve("hotkey-presets.json");
        List<HotkeyPreset> presets = new ArrayList<>();
        String padding = repeated('x', 180);
        for(int preset = 0; preset < 100; preset++) {
            Map<String, InputGesture> values = new LinkedHashMap<>();
            for(int binding = 0; binding < 512; binding++)
                values.put("action." + preset + "." + binding + "." + padding, InputGesture.none());
            presets.add(new HotkeyPreset("user-" + preset, "Preset " + preset, false, values));
        }
        HotkeyPresetLibrary library = new HotkeyPresetLibrary("user-0", presets);

        assertThrows(IllegalArgumentException.class,
                () -> new HotkeyPresetStore(file).save(library));
        assertFalse(Files.exists(file));
    }

    @Test void saveRejectsActionIdsThatLoadWouldReject(@TempDir Path dir) {
        String longId = repeated('x', 257);
        HotkeyPreset preset = userPreset("user-long", "Long", longId, InputGesture.none());
        assertThrows(IllegalArgumentException.class, () -> new HotkeyPresetStore(
                dir.resolve("hotkey-presets.json")).save(new HotkeyPresetLibrary(
                "user-long", Collections.singletonList(preset))));
    }

    private static String repeated(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static HotkeyPreset userPreset(String id, String name, String actionId, InputGesture gesture) {
        Map<String, InputGesture> values = new LinkedHashMap<>();
        values.put(actionId, gesture);
        return new HotkeyPreset(id, name, false, values);
    }
}
