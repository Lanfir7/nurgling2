package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.Hotkeys;
import nurgling.hotkeys.InputGesture;
import nurgling.widgets.nsettings.HotkeySettingsModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeyPresetCompatibilityTest {
    @Test void oldPresetDefaultsNewActionsAndPreservesUnknownNewerActions() {
        Map<String, InputGesture> bindings = new TreeMap<>();
        bindings.put("known", InputGesture.none());
        InputGesture future = InputGesture.mouse(3, KeyMatch.MODS, 0);
        bindings.put("future.action", future);
        HotkeyPreset old = new HotkeyPreset("old", "Old", false, bindings);
        HotkeyRegistry current = registryWith("known", "new.action");

        Map<String, InputGesture> applied = HotkeyPresetDraftModel.valuesFor(current, old);

        assertEquals(InputGesture.none(), applied.get("known"));
        assertEquals(current.find("new.action").defaultGesture(), applied.get("new.action"));
        assertEquals(future, old.gesture("future.action"));
        assertEquals(2, old.gestures().size(), "applying must not rewrite the imported preset");
    }

    @Test void initializingHotkeyRegistryDoesNotReadPresetFile() {
        CountingRepository repository = new CountingRepository();
        Hotkeys.registry();
        assertEquals(0, repository.loadCalls);

        HotkeySettingsModel.open(new HotkeyRegistry(), repository);

        assertEquals(1, repository.loadCalls);
    }

    private static HotkeyRegistry registryWith(String... ids) {
        HotkeyRegistry registry = new HotkeyRegistry();
        for(String id : ids) {
            InputGesture gesture = InputGesture.mouse(1, KeyMatch.MODS, 0);
            registry.register(new HotkeyAction(id, null, id, HotkeyCategory.INVENTORY,
                    EnumSet.of(HotkeyContext.INVENTORY_ITEM_GENERIC),
                    EnumSet.of(InputGesture.Type.MOUSE_BUTTON),
                    new MemoryBinding(id, gesture), null, 0, false));
        }
        return registry;
    }

    private static final class CountingRepository implements HotkeyPresetRepository {
        int loadCalls;
        public HotkeyPresetStore.LoadResult load() {
            loadCalls++;
            return HotkeyPresetStore.LoadResult.loaded(new HotkeyPresetLibrary(
                    HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList()));
        }
        public void save(HotkeyPresetLibrary library) { }
        public Checkpoint checkpoint() { return new Checkpoint() {}; }
        public void restore(Checkpoint checkpoint) { }
    }

    private static final class MemoryBinding implements HotkeyBinding {
        private final String id;
        private final InputGesture value;
        MemoryBinding(String id, InputGesture value) { this.id = id; this.value = value; }
        public String id() { return id; }
        public InputGesture defaultGesture() { return value; }
        public InputGesture current() { return value; }
        public void set(InputGesture gesture) { }
        public void reset() { }
    }
}
