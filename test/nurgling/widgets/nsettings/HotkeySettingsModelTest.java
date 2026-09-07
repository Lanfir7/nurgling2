package nurgling.widgets.nsettings;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeySettingsModelTest {
    @Test void querySearchesEveryCategoryAndClearingRestoresPreviousTab() {
        HotkeySettingsModel model = modelWith("Inventory", "Transfer item", "Map", "Quick marker");
        model.selectCategory(HotkeyCategory.INVENTORY);
        model.setQuery("marker");
        assertEquals(Arrays.asList("quick-marker"), ids(model.visibleActions()));
        model.setQuery("");
        assertEquals(HotkeyCategory.INVENTORY, model.selectedCategory());
        assertEquals(Arrays.asList("transfer-item"), ids(model.visibleActions()));
    }

    @Test void conflictsOnlyUsesDraftValues() {
        HotkeySettingsModel model = modelWith("Inventory", "First", "Inventory", "Second");
        model.draft().assign("second", model.draft().effective("first"));
        model.setConflictsOnly(true);
        assertEquals(new HashSet<>(Arrays.asList("first", "second")), idSet(model.visibleActions()));
    }

    private static HotkeySettingsModel modelWith(String firstCategory, String firstLabel,
                                                 String secondCategory, String secondLabel) {
        HotkeyRegistry registry = new HotkeyRegistry();
        String firstId = firstLabel.equals("First") ? "first" : "transfer-item";
        String secondId = secondLabel.equals("Second") ? "second" : "quick-marker";
        registry.register(action(firstId, firstLabel, HotkeyCategory.INVENTORY,
                HotkeyContext.INVENTORY_BACKGROUND, InputGesture.mouse(1, KeyMatch.MODS, 0)));
        registry.register(action(secondId, secondLabel, HotkeyCategory.MAP,
                HotkeyContext.INVENTORY_BACKGROUND, InputGesture.mouse(1, KeyMatch.MODS, 0)));
        return new HotkeySettingsModel(registry, new HotkeyDraftModel(registry));
    }

    private static HotkeyAction action(String id, String label, HotkeyCategory category,
                                       HotkeyContext context, InputGesture gesture) {
        return new HotkeyAction(id, null, label, category, EnumSet.of(context),
                EnumSet.of(gesture.type()), new MemoryBinding(id, gesture), null, 0, false);
    }

    private static List<String> ids(List<HotkeyAction> actions) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for(HotkeyAction action : actions) result.add(action.id());
        return result;
    }

    private static Set<String> idSet(List<HotkeyAction> actions) {
        return new HashSet<>(ids(actions));
    }

    private static final class MemoryBinding implements HotkeyBinding {
        private final String id;
        private final InputGesture defaultGesture;
        private InputGesture current;
        MemoryBinding(String id, InputGesture gesture) { this.id = id; this.defaultGesture = gesture; this.current = gesture; }
        public String id() { return id; }
        public InputGesture defaultGesture() { return defaultGesture; }
        public InputGesture current() { return current; }
        public void set(InputGesture gesture) { current = gesture; }
        public void reset() { current = defaultGesture; }
    }
}
