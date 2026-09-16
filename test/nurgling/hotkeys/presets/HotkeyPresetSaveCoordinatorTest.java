package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;
import nurgling.hotkeys.GestureBinding;
import nurgling.hotkeys.PreferenceStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyPresetSaveCoordinatorTest {
    @Test void successfulSaveCommitsBindingsAndSelectedUserPreset() {
        Fixture f = fixture(false);
        f.hotkeys.assign("item.take", InputGesture.none());
        f.presets.onBindingsEdited(f.hotkeys.effectiveSnapshot());

        f.coordinator.save();

        assertEquals(InputGesture.none(), f.registry.find("item.take").current());
        assertEquals(f.presets.selected().id(), f.repository.current.selectedPresetId());
        assertEquals(InputGesture.none(), f.repository.current.userPresets().get(0).gesture("item.take"));
        assertFalse(f.hotkeys.isDirty());
        assertFalse(f.presets.isDirty());
    }

    @Test void repositoryFailureRestoresBindingsStoreAndDrafts() {
        Fixture f = fixture(false);
        InputGesture original = f.registry.find("item.take").current();
        HotkeyPresetLibrary libraryBefore = f.repository.current;
        f.hotkeys.assign("item.take", InputGesture.none());
        f.presets.onBindingsEdited(f.hotkeys.effectiveSnapshot());
        f.repository.failure = new IOException("disk full");

        RuntimeException failure = assertThrows(RuntimeException.class, f.coordinator::save);

        assertEquals("disk full", failure.getCause().getMessage());
        assertEquals(original, f.registry.find("item.take").current());
        assertTrue(f.hotkeys.isDirty());
        assertTrue(f.presets.isDirty());
        assertEquals(libraryBefore, f.repository.current);
        assertEquals(1, f.repository.restoreCalls);
    }

    @Test void conflictsPreventRuntimeAndStoreWrites() {
        Fixture f = fixture(true);
        InputGesture occupied = f.registry.find("other").current();
        f.hotkeys.assign("item.take", occupied);
        Map<String, InputGesture> runtimeBefore = runtime(f.registry);

        assertThrows(IllegalStateException.class, f.coordinator::save);

        assertEquals(0, f.repository.saveCalls);
        assertEquals(0, f.repository.checkpointCalls);
        assertEquals(runtimeBefore, runtime(f.registry));
    }

    @Test void cancelRestoresSavedPresetSelectionAndDiscardsBindingDraft() {
        Fixture f = fixture(false);
        f.hotkeys.assign("item.take", InputGesture.none());
        f.presets.onBindingsEdited(f.hotkeys.effectiveSnapshot());
        assertNotEquals(HotkeyPresetCatalog.DEFAULT_ID, f.presets.selected().id());

        f.coordinator.cancel();

        assertEquals(HotkeyPresetCatalog.DEFAULT_ID, f.presets.selected().id());
        assertFalse(f.hotkeys.isDirty());
        assertFalse(f.presets.isDirty());
    }

    @Test void failedSaveRestoresMissingPreferenceInsteadOfPinningCurrentDefault() {
        MemoryPreferences preferences = new MemoryPreferences();
        HotkeyRegistry registry = new HotkeyRegistry();
        InputGesture original = InputGesture.mouse(1, KeyMatch.MODS, 0);
        GestureBinding binding = new GestureBinding("item.take", original, preferences);
        registry.register(new HotkeyAction("item.take", null, "item.take", HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC),
                EnumSet.of(InputGesture.Type.MOUSE_BUTTON), binding, null, 0, false));
        HotkeyDraftModel hotkeys = new HotkeyDraftModel(registry);
        HotkeyPresetLibrary library = new HotkeyPresetLibrary(
                HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList());
        FakeRepository repository = new FakeRepository(library);
        repository.failure = new IOException("disk full");
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.loaded(library));
        HotkeyPresetSaveCoordinator coordinator = new HotkeyPresetSaveCoordinator(
                registry, hotkeys, presets, repository);

        assertThrows(RuntimeException.class, coordinator::save);

        assertFalse(preferences.values.containsKey("gesturebind/item.take"));
        assertEquals(original, binding.current());
    }

    @Test void savingSelectionDefaultsActionsRegisteredAfterThePresetWasSelected() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("known", InputGesture.mouse(1, KeyMatch.MODS, 0)));
        Map<String, InputGesture> selectedValues = new LinkedHashMap<>();
        selectedValues.put("known", InputGesture.none());
        selectedValues.put("late-preset", InputGesture.none());
        HotkeyPreset selected = new HotkeyPreset("user-1", "Mine", false, selectedValues);
        HotkeyPresetLibrary library = new HotkeyPresetLibrary("user-1",
                Collections.singletonList(selected));
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.loaded(library));
        HotkeyDraftModel hotkeys = new HotkeyDraftModel(registry);
        hotkeys.stageSnapshot(presets.select("user-1"));

        HotkeyAction latePreset = action("late-preset", InputGesture.mouse(2, KeyMatch.MODS, 0));
        latePreset.binding().set(InputGesture.mouse(3, KeyMatch.MODS, 0));
        registry.register(latePreset);
        HotkeyAction lateDefault = action("late-default", InputGesture.mouse(2, KeyMatch.MODS, 0));
        lateDefault.binding().set(InputGesture.mouse(3, KeyMatch.MODS, 0));
        registry.register(lateDefault);
        FakeRepository repository = new FakeRepository(library);

        new HotkeyPresetSaveCoordinator(registry, hotkeys, presets, repository).save();

        assertEquals(InputGesture.none(), registry.find("known").current());
        assertEquals(InputGesture.none(), latePreset.current());
        assertEquals(lateDefault.defaultGesture(), lateDefault.current());
        assertNull(repository.current.userPresets().get(0).gesture("late-default"));
    }

    private static Fixture fixture(boolean withOther) {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("item.take", InputGesture.mouse(1, KeyMatch.MODS, 0)));
        if(withOther)
            registry.register(action("other", InputGesture.mouse(3, KeyMatch.MODS, 0)));
        HotkeyDraftModel hotkeys = new HotkeyDraftModel(registry);
        HotkeyPresetLibrary original = new HotkeyPresetLibrary(
                HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList());
        FakeRepository repository = new FakeRepository(original);
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), HotkeyPresetStore.LoadResult.loaded(original));
        return new Fixture(registry, hotkeys, presets, repository,
                new HotkeyPresetSaveCoordinator(registry, hotkeys, presets, repository));
    }

    private static HotkeyAction action(String id, InputGesture gesture) {
        TestBinding binding = new TestBinding(id, gesture);
        return new HotkeyAction(id, null, id, HotkeyCategory.INVENTORY,
                EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC),
                EnumSet.of(InputGesture.Type.MOUSE_BUTTON), binding, null, 0, false);
    }

    private static Map<String, InputGesture> runtime(HotkeyRegistry registry) {
        Map<String, InputGesture> result = new LinkedHashMap<>();
        for(HotkeyAction action : registry.snapshot()) result.put(action.id(), action.current());
        return result;
    }

    private static final class Fixture {
        final HotkeyRegistry registry;
        final HotkeyDraftModel hotkeys;
        final HotkeyPresetDraftModel presets;
        final FakeRepository repository;
        final HotkeyPresetSaveCoordinator coordinator;
        Fixture(HotkeyRegistry registry, HotkeyDraftModel hotkeys, HotkeyPresetDraftModel presets,
                FakeRepository repository, HotkeyPresetSaveCoordinator coordinator) {
            this.registry = registry;
            this.hotkeys = hotkeys;
            this.presets = presets;
            this.repository = repository;
            this.coordinator = coordinator;
        }
    }

    private static final class TestBinding implements HotkeyBinding {
        private final String id;
        private final InputGesture defaultGesture;
        private InputGesture current;
        TestBinding(String id, InputGesture defaultGesture) {
            this.id = id;
            this.defaultGesture = defaultGesture;
            this.current = defaultGesture;
        }
        public String id() { return id; }
        public InputGesture defaultGesture() { return defaultGesture; }
        public InputGesture current() { return current; }
        public void set(InputGesture gesture) { current = gesture; }
        public void reset() { current = defaultGesture; }
    }

    private static final class FakeRepository implements HotkeyPresetRepository {
        HotkeyPresetLibrary current;
        IOException failure;
        int saveCalls;
        int checkpointCalls;
        int restoreCalls;
        FakeRepository(HotkeyPresetLibrary current) { this.current = current; }
        public HotkeyPresetStore.LoadResult load() { return HotkeyPresetStore.LoadResult.loaded(current); }
        public void save(HotkeyPresetLibrary library) throws IOException {
            saveCalls++;
            current = library;
            if(failure != null) throw failure;
        }
        public Checkpoint checkpoint() {
            checkpointCalls++;
            return new FakeCheckpoint(current);
        }
        public void restore(Checkpoint checkpoint) {
            restoreCalls++;
            current = ((FakeCheckpoint)checkpoint).value;
        }
    }

    private static final class FakeCheckpoint implements HotkeyPresetRepository.Checkpoint {
        final HotkeyPresetLibrary value;
        FakeCheckpoint(HotkeyPresetLibrary value) { this.value = value; }
    }

    private static final class MemoryPreferences implements PreferenceStore {
        final Map<String, String> values = new LinkedHashMap<>();
        public String get(String key, String fallback) {
            return values.containsKey(key) ? values.get(key) : fallback;
        }
        public void set(String key, String value) {
            if(value == null) values.remove(key);
            else values.put(key, value);
        }
    }
}
