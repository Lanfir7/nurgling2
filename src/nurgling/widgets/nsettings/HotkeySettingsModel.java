package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.HotkeyConflict;
import nurgling.hotkeys.InputGesture;
import nurgling.hotkeys.presets.HotkeyPreset;
import nurgling.hotkeys.presets.HotkeyPresetCatalog;
import nurgling.hotkeys.presets.HotkeyPresetCodec;
import nurgling.hotkeys.presets.HotkeyPresetDraftModel;
import nurgling.hotkeys.presets.HotkeyPresetLibrary;
import nurgling.hotkeys.presets.HotkeyPresetRepository;
import nurgling.hotkeys.presets.HotkeyPresetSaveCoordinator;
import nurgling.hotkeys.presets.HotkeyPresetStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure state and filtering model for the hotkey settings page. */
public final class HotkeySettingsModel {
    private final HotkeyRegistry registry;
    private final HotkeyDraftModel draft;
    private final HotkeyPresetDraftModel presets;
    private final HotkeyPresetSaveCoordinator saveCoordinator;
    private String savedPresetId;
    private HotkeyCategory selectedCategory = HotkeyCategory.ALL;
    private String query = "";
    private boolean conflictsOnly;

    public HotkeySettingsModel(HotkeyRegistry registry, HotkeyDraftModel draft) {
        this(registry, draft, ephemeralPresets(registry), new EphemeralRepository());
    }

    private HotkeySettingsModel(HotkeyRegistry registry, HotkeyDraftModel draft,
                                HotkeyPresetDraftModel presets,
                                HotkeyPresetRepository repository) {
        if(registry == null)
            throw new NullPointerException("registry");
        if(draft == null)
            throw new NullPointerException("draft");
        if(presets == null || repository == null)
            throw new NullPointerException();
        this.registry = registry;
        this.draft = draft;
        this.presets = presets;
        this.saveCoordinator = new HotkeyPresetSaveCoordinator(registry, draft, presets, repository);
        this.savedPresetId = presets.selected().id();
    }

    public HotkeySettingsModel(HotkeyRegistry registry) {
        this(registry, new HotkeyDraftModel(registry));
    }

    public HotkeyRegistry registry() { return registry; }
    public HotkeyDraftModel draft() { return draft; }
    public HotkeyPresetDraftModel presets() { return presets; }
    public String savedPresetId() { return savedPresetId; }

    /** Opens the preset library only when the hotkey settings page itself is created. */
    public static HotkeySettingsModel open(HotkeyRegistry registry,
                                           HotkeyPresetRepository repository) {
        if(registry == null || repository == null) throw new NullPointerException();
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
                HotkeyPresetCatalog.builtIns(registry), repository.load());
        return new HotkeySettingsModel(registry, draft, presets, repository);
    }

    public List<HotkeyConflict> assign(String id, InputGesture gesture) {
        List<HotkeyConflict> conflicts = draft.assign(id, gesture);
        if(conflicts.isEmpty()) bindingsEdited();
        return conflicts;
    }

    public void reset(String id) {
        draft.reset(id);
        bindingsEdited();
    }

    public void replace(HotkeyConflict conflict) {
        draft.replace(conflict);
        bindingsEdited();
    }

    public void resetCategory(HotkeyCategory category) {
        draft.resetCategory(category);
        bindingsEdited();
    }

    public void resetAll() {
        draft.resetAll();
        bindingsEdited();
    }

    public void selectPreset(String id) {
        draft.stageSnapshot(presets.select(id));
    }

    public HotkeyPreset createPreset(String name) {
        return presets.create(name, draft.effectiveSnapshot());
    }

    public HotkeyPreset importPreset(String code) {
        HotkeyPreset decoded = validateImportCode(code);
        HotkeyDraftModel.Checkpoint hotkeysBefore = draft.checkpoint();
        HotkeyPresetDraftModel.Checkpoint presetsBefore = presets.checkpoint();
        try {
            HotkeyPreset imported = presets.importPreset(decoded);
            draft.stageSnapshot(HotkeyPresetDraftModel.valuesFor(registry, imported));
            return imported;
        } catch(RuntimeException failure) {
            draft.restore(hotkeysBefore);
            presets.restore(presetsBefore);
            throw failure;
        }
    }

    public HotkeyPreset validateImportCode(String code) {
        HotkeyPreset decoded = HotkeyPresetCodec.decode(code);
        HotkeyPresetDraftModel.valuesFor(registry, decoded);
        return decoded;
    }

    public String copyPresetCode() {
        return HotkeyPresetCodec.encode(presets.selected());
    }

    public void deleteSelectedPreset() {
        draft.stageSnapshot(presets.deleteSelected());
    }

    public void save() {
        saveCoordinator.save();
        savedPresetId = presets.selected().id();
    }

    public void cancel() {
        saveCoordinator.cancel();
        savedPresetId = presets.selected().id();
    }

    public boolean hasUnsavedChanges() {
        return draft.isDirty() || presets.isDirty();
    }

    private void bindingsEdited() {
        presets.onBindingsEdited(draft.effectiveSnapshot());
    }

    public void selectCategory(HotkeyCategory category) {
        if(category == null)
            throw new NullPointerException("category");
        selectedCategory = category;
    }

    public HotkeyCategory selectedCategory() { return selectedCategory; }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim();
    }

    public String query() { return query; }

    public void setConflictsOnly(boolean conflictsOnly) {
        this.conflictsOnly = conflictsOnly;
    }

    public boolean conflictsOnly() { return conflictsOnly; }

    public List<HotkeyAction> visibleActions() {
        String needle = query.toLowerCase(Locale.ROOT);
        List<HotkeyAction> result = new ArrayList<>();
        for(HotkeyAction action : registry.snapshot()) {
            if(needle.length() == 0 && selectedCategory != HotkeyCategory.ALL &&
                    action.category() != selectedCategory)
                continue;
            if(needle.length() != 0 && !matches(action, needle))
                continue;
            if(conflictsOnly && draft.conflicts(action.id()).isEmpty())
                continue;
            result.add(action);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean matches(HotkeyAction action, String needle) {
        if(contains(action.id(), needle) || contains(action.label(), needle) ||
                contains(action.labelKey(), needle) || contains(action.category().name(), needle) ||
                contains(displayName(action.category()), needle) ||
                contains(action.category().label(), needle))
            return true;
        for(HotkeyContext context : action.contexts())
            if(contains(context.name(), needle) || contains(displayName(context), needle) ||
                    contains(context.label(), needle))
                return true;
        return false;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String displayName(Enum<?> value) {
        String name = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(name.length());
        boolean upper = true;
        for(int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if(upper && c != ' ') {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
                upper = c == ' ';
            }
        }
        return result.toString();
    }

    private static HotkeyPresetDraftModel ephemeralPresets(HotkeyRegistry registry) {
        return HotkeyPresetDraftModel.open(registry, HotkeyPresetCatalog.builtIns(registry),
                HotkeyPresetStore.LoadResult.loaded(new HotkeyPresetLibrary(
                        HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList())));
    }

    private static final class EphemeralRepository implements HotkeyPresetRepository {
        private static final Checkpoint CHECKPOINT = new Checkpoint() {};
        public HotkeyPresetStore.LoadResult load() {
            return HotkeyPresetStore.LoadResult.loaded(new HotkeyPresetLibrary(
                    HotkeyPresetCatalog.DEFAULT_ID, Collections.emptyList()));
        }
        public void save(HotkeyPresetLibrary library) { }
        public Checkpoint checkpoint() { return CHECKPOINT; }
        public void restore(Checkpoint checkpoint) { }
    }

}
