package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyPresetDraftModelTest {
    @Test void existingCustomBindingsMigrateWithoutChangingThem() {
        HotkeyRegistry registry = registry(InputGesture.mouse(1, KeyMatch.MODS, 0),
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S));

        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.migrationRequired(null));

        assertEquals("Пользовательский 1", presets.selected().name());
        assertEquals(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S),
                presets.selected().gesture("item.take"));
        assertTrue(presets.isDirty());
    }

    @Test void unchangedBindingsMigrateToDefault() {
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = registry(original, original);

        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.migrationRequired(null));

        assertEquals(HotkeyPresetCatalog.DEFAULT_ID, presets.selected().id());
        assertEquals(3, presets.presets().size());
    }

    @Test void editingBuiltInForksOnceAndFurtherEditsUpdateSameUserPreset() {
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = registry(original, original);
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), loadedDefault());

        presets.onBindingsEdited(snapshot("item.take", InputGesture.none()));
        String forkId = presets.selected().id();
        presets.onBindingsEdited(snapshot("item.take", InputGesture.mouse(3, KeyMatch.MODS, 0)));

        assertFalse(presets.selected().builtIn());
        assertEquals("Пользовательский 1", presets.selected().name());
        assertEquals(forkId, presets.selected().id());
        assertEquals(InputGesture.mouse(3, KeyMatch.MODS, 0), presets.selected().gesture("item.take"));
        assertEquals(1, presets.persistentState().userPresets().size());
    }

    @Test void editingUserPresetPreservesUnknownNewerActionIds() {
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = registry(original, original);
        Map<String, InputGesture> savedBindings = snapshot("item.take", original);
        savedBindings.put("future.action", InputGesture.modifier(KeyMatch.M));
        HotkeyPreset saved = new HotkeyPreset("user-1", "Mine", false, savedBindings);
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.loaded(
                        new HotkeyPresetLibrary("user-1", Collections.singletonList(saved))));

        presets.onBindingsEdited(snapshot("item.take", InputGesture.none()));

        assertEquals(InputGesture.modifier(KeyMatch.M), presets.selected().gesture("future.action"));
        assertEquals(InputGesture.none(), presets.selected().gesture("item.take"));
    }

    @Test void createImportDeleteAndRestoreAreFullyStaged() {
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = registry(original, original);
        HotkeyPreset saved = new HotkeyPreset("user-1", "Saved", false,
                snapshot("item.take", original));
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.loaded(
                        new HotkeyPresetLibrary("user-1", Collections.singletonList(saved))));
        HotkeyPresetDraftModel.Checkpoint before = presets.checkpoint();

        presets.create("Mine", snapshot("item.take", InputGesture.none()));
        presets.importPreset(new HotkeyPreset("imported", "Mine", false,
                snapshot("item.take", InputGesture.mouse(3, KeyMatch.MODS, 0))));
        assertEquals("Mine (2)", presets.selected().name());
        presets.deleteSelected();
        assertEquals(HotkeyPresetCatalog.DEFAULT_ID, presets.selected().id());
        presets.restore(before);

        assertEquals("user-1", presets.selected().id());
        assertFalse(presets.isDirty());
    }

    @Test void missingCurrentActionsUseDefaultsWithoutRemovingUnknownIds() {
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = registry(original, original);
        Map<String, InputGesture> imported = new LinkedHashMap<>();
        imported.put("future.action", InputGesture.none());
        HotkeyPreset preset = new HotkeyPreset("user-1", "Future", false, imported);

        Map<String, InputGesture> effective = HotkeyPresetDraftModel.valuesFor(registry, preset);

        assertEquals(original, effective.get("item.take"));
        assertEquals(InputGesture.none(), preset.gesture("future.action"));
        assertFalse(effective.containsKey("future.action"));
    }

    private static HotkeyPresetStore.LoadResult loadedDefault() {
        return HotkeyPresetStore.LoadResult.loaded(new HotkeyPresetLibrary(
                HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList()));
    }

    private static Map<String, InputGesture> snapshot(String id, InputGesture value) {
        Map<String, InputGesture> values = new LinkedHashMap<>();
        values.put(id, value);
        return values;
    }

    private static HotkeyRegistry registry(InputGesture defaultGesture, InputGesture current) {
        HotkeyRegistry registry = new HotkeyRegistry();
        TestBinding binding = new TestBinding("item.take", defaultGesture, current);
        registry.register(new HotkeyAction("item.take", null, "Take", HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC),
                EnumSet.of(InputGesture.Type.MOUSE_BUTTON), binding, null, 0, false));
        return registry;
    }

    private static final class TestBinding implements HotkeyBinding {
        private final String id;
        private final InputGesture defaultGesture;
        private InputGesture current;
        TestBinding(String id, InputGesture defaultGesture, InputGesture current) {
            this.id = id;
            this.defaultGesture = defaultGesture;
            this.current = current;
        }
        public String id() { return id; }
        public InputGesture defaultGesture() { return defaultGesture; }
        public InputGesture current() { return current; }
        public void set(InputGesture gesture) { current = gesture; }
        public void reset() { current = defaultGesture; }
    }
}
