package nurgling.widgets.nsettings;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.InputGesture;
import nurgling.hotkeys.presets.HotkeyPreset;
import nurgling.hotkeys.presets.HotkeyPresetLibrary;
import nurgling.hotkeys.presets.HotkeyPresetRepository;
import nurgling.hotkeys.presets.HotkeyPresetStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HotkeySettingsModelTest {
    @Test void assigningGestureForksBuiltInAndSaveUpdatesSameUserPreset() {
        HotkeyRegistry registry = singleActionRegistry();
        MemoryRepository repository = new MemoryRepository(
                new HotkeyPresetLibrary("builtin.default", java.util.Collections.emptyList()));
        HotkeySettingsModel model = HotkeySettingsModel.open(registry, repository);

        model.assign("item.take", InputGesture.none());
        String userId = model.presets().selected().id();
        assertFalse(model.presets().selected().builtIn());
        model.save();
        InputGesture replacement = InputGesture.mouse(3, KeyMatch.MODS, 0);
        model.assign("item.take", replacement);
        model.save();

        assertEquals(userId, model.presets().selected().id());
        assertEquals(replacement, model.presets().selected().gesture("item.take"));
    }

    @Test void selectingPresetStagesRowsUntilSaveAndCancelRestoresView() {
        HotkeyRegistry registry = singleActionRegistry();
        HotkeyPreset user = new HotkeyPreset("user-1", "Custom", false,
                java.util.Collections.singletonMap("item.take", InputGesture.none()));
        MemoryRepository repository = new MemoryRepository(
                new HotkeyPresetLibrary("builtin.default", java.util.Collections.singletonList(user)));
        HotkeySettingsModel model = HotkeySettingsModel.open(registry, repository);
        InputGesture runtimeBefore = registry.find("item.take").current();

        model.selectPreset("user-1");
        assertEquals(InputGesture.none(), model.draft().effective("item.take"));
        assertEquals(runtimeBefore, registry.find("item.take").current());
        model.cancel();

        assertEquals(model.savedPresetId(), model.presets().selected().id());
        assertEquals(runtimeBefore, model.draft().effective("item.take"));
    }
    @Test void querySearchesEveryCategoryAndClearingRestoresPreviousTab() {
        HotkeySettingsModel model = modelWith("Inventory", "Transfer item", "Map", "Quick marker");
        model.selectCategory(HotkeyCategory.INVENTORY);
        model.setQuery("marker");
        assertEquals(Arrays.asList("quick-marker"), ids(model.visibleActions()));
        model.setQuery("");
        assertEquals(HotkeyCategory.INVENTORY, model.selectedCategory());
        assertEquals(Arrays.asList("transfer-item"), ids(model.visibleActions()));
        model.setQuery("Инвентарь");
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
        HotkeyCategory first = category(firstCategory);
        HotkeyCategory second = category(secondCategory);
        registry.register(action(firstId, firstLabel, first, context(first),
                InputGesture.mouse(1, KeyMatch.MODS, 0)));
        registry.register(action(secondId, secondLabel, second, context(first == second ? first : second),
                InputGesture.mouse(1, KeyMatch.MODS, 0)));
        return new HotkeySettingsModel(registry, new HotkeyDraftModel(registry));
    }

    private static HotkeyRegistry singleActionRegistry() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("item.take", "Take", HotkeyCategory.INVENTORY,
                HotkeyContext.INVENTORY_ITEM_GENERIC, InputGesture.mouse(1, KeyMatch.MODS, 0)));
        return registry;
    }

    private static final class MemoryRepository implements HotkeyPresetRepository {
        private HotkeyPresetLibrary library;
        MemoryRepository(HotkeyPresetLibrary library) { this.library = library; }
        public HotkeyPresetStore.LoadResult load() { return HotkeyPresetStore.LoadResult.loaded(library); }
        public void save(HotkeyPresetLibrary library) { this.library = library; }
        public Checkpoint checkpoint() { return new Saved(library); }
        public void restore(Checkpoint checkpoint) { library = ((Saved)checkpoint).library; }
        private static final class Saved implements Checkpoint {
            final HotkeyPresetLibrary library;
            Saved(HotkeyPresetLibrary library) { this.library = library; }
        }
    }

    private static HotkeyCategory category(String value) {
        return HotkeyCategory.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static HotkeyContext context(HotkeyCategory category) {
        return category == HotkeyCategory.INVENTORY ? HotkeyContext.INVENTORY_BACKGROUND :
                category == HotkeyCategory.MAP ? HotkeyContext.MAP_SURFACE : HotkeyContext.GLOBAL;
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

    @Test void categoriesAndContextsExposeStableLocalizedMetadata() {
        assertEquals("hotkey.category.inventory", HotkeyCategory.INVENTORY.labelKey());
        assertEquals("hotkey.context.inventory_background", HotkeyContext.INVENTORY_BACKGROUND.labelKey());
        java.util.Locale previous = nurgling.i18n.L10n.getLocale();
        try {
            nurgling.i18n.L10n.setLocale(java.util.Locale.ENGLISH);
            assertEquals("Inventory", HotkeyCategory.INVENTORY.label());
            assertEquals("Inventory background", HotkeyContext.INVENTORY_BACKGROUND.label());
            nurgling.i18n.L10n.setLocale(java.util.Locale.forLanguageTag("ru"));
            assertEquals("Инвентарь", HotkeyCategory.INVENTORY.label());
            assertEquals("Фон инвентаря", HotkeyContext.INVENTORY_BACKGROUND.label());
        } finally { nurgling.i18n.L10n.setLocale(previous); }
    }

    @Test void missingCategoryAndContextLabelsUseTechnicalIdentifiers() throws Exception {
        java.lang.reflect.Field field = nurgling.i18n.L10n.class.getDeclaredField("messages");
        field.setAccessible(true);
        java.util.Properties previous = (java.util.Properties)field.get(null);
        try {
            field.set(null, new java.util.Properties());
            assertEquals("INVENTORY", HotkeyCategory.INVENTORY.label());
            assertEquals("INVENTORY_BACKGROUND", HotkeyContext.INVENTORY_BACKGROUND.label());
        } finally { field.set(null, previous); }
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
