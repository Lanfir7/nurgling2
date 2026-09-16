package nurgling.hotkeys.presets;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Staged preset selection and user-preset edits. */
public final class HotkeyPresetDraftModel {
    private static final int MAX_NAME_CHARS = 64;

    private final HotkeyRegistry registry;
    private final List<HotkeyPreset> builtIns;
    private final String warningKey;
    private List<HotkeyPreset> users;
    private String selectedId;
    private boolean migrationPending;

    private List<HotkeyPreset> savedUsers;
    private String savedSelectedId;
    private boolean savedMigrationPending;

    private HotkeyPresetDraftModel(HotkeyRegistry registry, List<HotkeyPreset> builtIns,
                                   List<HotkeyPreset> users, String selectedId,
                                   boolean migrationPending, String warningKey) {
        this.registry = registry;
        this.builtIns = Collections.unmodifiableList(new ArrayList<>(builtIns));
        this.users = new ArrayList<>(users);
        this.selectedId = selectedId;
        this.migrationPending = migrationPending;
        this.warningKey = warningKey;
        this.savedUsers = new ArrayList<>(users);
        this.savedSelectedId = selectedId;
        this.savedMigrationPending = migrationPending;
    }

    public static HotkeyPresetDraftModel open(HotkeyRegistry registry,
                                                List<HotkeyPreset> builtIns,
                                                HotkeyPresetStore.LoadResult loaded) {
        if(registry == null || builtIns == null || loaded == null)
            throw new NullPointerException();
        validateBuiltIns(builtIns);
        if(loaded.migrationRequired()) {
            Map<String, InputGesture> current = currentSnapshot(registry);
            HotkeyPreset defaultPreset = find(builtIns, HotkeyPresetCatalog.DEFAULT_ID);
            if(defaultPreset == null) throw new IllegalArgumentException("default preset is missing");
            List<HotkeyPreset> users = new ArrayList<>();
            String selected = defaultPreset.id();
            if(!current.equals(valuesFor(registry, defaultPreset))) {
                HotkeyPreset migrated = new HotkeyPreset(UUID.randomUUID().toString(),
                        "Пользовательский 1", false, current);
                users.add(migrated);
                selected = migrated.id();
            }
            return new HotkeyPresetDraftModel(registry, builtIns, users, selected,
                    true, loaded.warningKey());
        }

        HotkeyPresetLibrary library = loaded.library();
        List<HotkeyPreset> users = new ArrayList<>(library.userPresets());
        String selected = library.selectedPresetId();
        if(find(builtIns, selected) == null && find(users, selected) == null)
            selected = HotkeyPresetCatalog.DEFAULT_ID;
        return new HotkeyPresetDraftModel(registry, builtIns, users, selected,
                false, loaded.warningKey());
    }

    public List<HotkeyPreset> presets() {
        List<HotkeyPreset> result = new ArrayList<>(builtIns.size() + users.size());
        result.addAll(builtIns);
        result.addAll(users);
        return Collections.unmodifiableList(result);
    }

    public HotkeyPreset selected() {
        HotkeyPreset preset = find(builtIns, selectedId);
        if(preset == null) preset = find(users, selectedId);
        if(preset == null) throw new IllegalStateException("selected preset is missing");
        return preset;
    }

    public Map<String, InputGesture> select(String id) {
        HotkeyPreset preset = find(builtIns, id);
        if(preset == null) preset = find(users, id);
        if(preset == null) throw new IllegalArgumentException("unknown preset: " + id);
        Map<String, InputGesture> values = valuesFor(registry, preset);
        selectedId = preset.id();
        return values;
    }

    public HotkeyPreset create(String requestedName, Map<String, InputGesture> bindings) {
        String name = uniqueName(requireName(requestedName));
        HotkeyPreset preset = new HotkeyPreset(UUID.randomUUID().toString(), name, false, bindings);
        users.add(preset);
        selectedId = preset.id();
        return preset;
    }

    public HotkeyPreset importPreset(HotkeyPreset decoded) {
        if(decoded == null || decoded.builtIn())
            throw new IllegalArgumentException("user preset required");
        HotkeyPreset imported = new HotkeyPreset(UUID.randomUUID().toString(),
                uniqueName(requireName(decoded.name())), false, decoded.gestures());
        users.add(imported);
        selectedId = imported.id();
        return imported;
    }

    public Map<String, InputGesture> deleteSelected() {
        HotkeyPreset selected = selected();
        if(!selected.builtIn()) {
            users.removeIf(value -> value.id().equals(selected.id()));
            selectedId = HotkeyPresetCatalog.DEFAULT_ID;
        }
        return valuesFor(registry, selected());
    }

    public void onBindingsEdited(Map<String, InputGesture> effectiveBindings) {
        if(effectiveBindings == null) throw new NullPointerException("effectiveBindings");
        HotkeyPreset preset = selected();
        if(preset.builtIn()) {
            preset = new HotkeyPreset(UUID.randomUUID().toString(), nextUserName(), false,
                    preset.gestures());
            users.add(preset);
            selectedId = preset.id();
        }
        Map<String, InputGesture> merged = new TreeMap<>(preset.gestures());
        merged.putAll(effectiveBindings);
        replaceUser(new HotkeyPreset(preset.id(), preset.name(), false, merged));
    }

    public HotkeyPresetLibrary persistentState() {
        return new HotkeyPresetLibrary(selectedId, users);
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(users, selectedId, migrationPending);
    }

    public void restore(Checkpoint checkpoint) {
        if(checkpoint == null) throw new NullPointerException("checkpoint");
        users = new ArrayList<>(checkpoint.users);
        selectedId = checkpoint.selectedPresetId;
        migrationPending = checkpoint.migrationPending;
    }

    public void restoreSavedState() {
        users = new ArrayList<>(savedUsers);
        selectedId = savedSelectedId;
        migrationPending = savedMigrationPending;
    }

    public void markSaved() {
        migrationPending = false;
        savedUsers = new ArrayList<>(users);
        savedSelectedId = selectedId;
        savedMigrationPending = false;
    }

    public boolean isDirty() {
        return migrationPending || !selectedId.equals(savedSelectedId) || !users.equals(savedUsers);
    }

    public String warningKey() { return warningKey; }

    public static Map<String, InputGesture> valuesFor(HotkeyRegistry registry, HotkeyPreset preset) {
        if(registry == null || preset == null) throw new NullPointerException();
        Map<String, InputGesture> result = new TreeMap<>(preset.gestures());
        for(HotkeyAction action : registry.snapshot()) {
            InputGesture gesture = preset.gesture(action.id());
            if(gesture == null) gesture = action.defaultGesture();
            if(!action.allows(gesture.type()))
                throw new IllegalArgumentException("gesture type is not allowed for " + action.id());
            result.put(action.id(), gesture);
        }
        return Collections.unmodifiableMap(result);
    }

    private void replaceUser(HotkeyPreset replacement) {
        for(int i = 0; i < users.size(); i++) {
            if(users.get(i).id().equals(replacement.id())) {
                users.set(i, replacement);
                return;
            }
        }
        throw new IllegalStateException("user preset is missing");
    }

    private String nextUserName() {
        for(int number = 1;; number++) {
            String candidate = "Пользовательский " + number;
            if(nameAvailable(candidate)) return candidate;
        }
    }

    private String uniqueName(String requested) {
        if(nameAvailable(requested)) return requested;
        for(int number = 2;; number++) {
            String suffix = " (" + number + ")";
            int keep = Math.min(requested.length(), MAX_NAME_CHARS - suffix.length());
            String candidate = requested.substring(0, Math.max(0, keep)).trim() + suffix;
            if(nameAvailable(candidate)) return candidate;
        }
    }

    private boolean nameAvailable(String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        for(HotkeyPreset preset : presets())
            if(preset.name().toLowerCase(Locale.ROOT).equals(normalized)) return false;
        return true;
    }

    private static String requireName(String value) {
        if(value == null) throw new IllegalArgumentException("missing preset name");
        String trimmed = value.trim();
        if(trimmed.isEmpty() || trimmed.length() > MAX_NAME_CHARS)
            throw new IllegalArgumentException("invalid preset name");
        return trimmed;
    }

    private static Map<String, InputGesture> currentSnapshot(HotkeyRegistry registry) {
        Map<String, InputGesture> result = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot()) result.put(action.id(), action.current());
        return result;
    }

    private static HotkeyPreset find(List<HotkeyPreset> presets, String id) {
        if(id == null) return null;
        for(HotkeyPreset preset : presets) if(preset.id().equals(id)) return preset;
        return null;
    }

    private static void validateBuiltIns(List<HotkeyPreset> builtIns) {
        if(builtIns.isEmpty()) throw new IllegalArgumentException("built-in presets are missing");
        Set<String> ids = new HashSet<>();
        for(HotkeyPreset preset : builtIns)
            if(preset == null || !preset.builtIn() || !ids.add(preset.id()))
                throw new IllegalArgumentException("invalid built-in preset catalog");
    }

    public static final class Checkpoint {
        private final List<HotkeyPreset> users;
        private final String selectedPresetId;
        private final boolean migrationPending;

        private Checkpoint(List<HotkeyPreset> users, String selectedPresetId, boolean migrationPending) {
            this.users = new ArrayList<>(users);
            this.selectedPresetId = selectedPresetId;
            this.migrationPending = migrationPending;
        }

        public String selectedPresetId() { return selectedPresetId; }
    }
}
