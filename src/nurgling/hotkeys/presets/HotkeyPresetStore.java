package nurgling.hotkeys.presets;

import nurgling.NConfig;
import nurgling.hotkeys.InputGesture;
import nurgling.tools.NFileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Versioned global JSON store for user hotkey presets. */
public final class HotkeyPresetStore implements HotkeyPresetRepository {
    public static final String FILE = "hotkey-presets.json";
    private static final int VERSION = 1;
    private static final int MAX_PRESETS = 256;
    private static final int MAX_BINDINGS = 1024;
    private static final int MAX_NAME_CHARS = 64;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;

    private final Path file;

    public HotkeyPresetStore(Path file) {
        if(file == null) throw new NullPointerException("file");
        this.file = file.toAbsolutePath().normalize();
    }

    public static HotkeyPresetStore global() {
        return new HotkeyPresetStore(Paths.get(
                NConfig.getGlobalInstance().getProfileAwarePath(FILE)));
    }

    @Override
    public LoadResult load() {
        if(!Files.isRegularFile(file)) return LoadResult.migrationRequired(null);
        try {
            byte[] bytes = Files.readAllBytes(file);
            if(bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("preset file is too large");
            return LoadResult.loaded(parse(new String(bytes, StandardCharsets.UTF_8)));
        } catch(Exception failure) {
            return LoadResult.migrationRequired("hotkeys.presets.warning.corrupt");
        }
    }

    @Override
    public void save(HotkeyPresetLibrary library) throws IOException {
        if(library == null) throw new NullPointerException("library");
        NFileUtils.writeAtomically(file.toString(), encode(library).toString(2));
    }

    @Override
    public Checkpoint checkpoint() throws IOException {
        boolean exists = Files.isRegularFile(file);
        return new FileCheckpoint(exists, exists ? Files.readAllBytes(file) : null);
    }

    @Override
    public void restore(Checkpoint checkpoint) throws IOException {
        if(!(checkpoint instanceof FileCheckpoint))
            throw new IllegalArgumentException("foreign preset checkpoint");
        FileCheckpoint saved = (FileCheckpoint)checkpoint;
        if(saved.existed)
            NFileUtils.writeAtomically(file.toString(), saved.bytes);
        else
            Files.deleteIfExists(file);
    }

    private static JSONObject encode(HotkeyPresetLibrary library) {
        if(library.userPresets().size() > MAX_PRESETS)
            throw new IllegalArgumentException("too many presets");
        JSONArray presets = new JSONArray();
        for(HotkeyPreset preset : library.userPresets()) {
            validateUserPreset(preset);
            JSONArray bindings = new JSONArray();
            for(Map.Entry<String, InputGesture> entry : preset.gestures().entrySet())
                bindings.put(new JSONObject().put("id", entry.getKey()).put("gesture", entry.getValue().encode()));
            presets.put(new JSONObject().put("id", preset.id()).put("name", preset.name())
                    .put("bindings", bindings));
        }
        return new JSONObject().put("version", VERSION)
                .put("selectedPresetId", library.selectedPresetId()).put("presets", presets);
    }

    private static HotkeyPresetLibrary parse(String json) {
        JSONObject root = new JSONObject(json);
        if(root.getInt("version") != VERSION)
            throw new IllegalArgumentException("unsupported preset version");
        String selected = requireId(root.getString("selectedPresetId"));
        JSONArray array = root.getJSONArray("presets");
        if(array.length() > MAX_PRESETS) throw new IllegalArgumentException("too many presets");
        List<HotkeyPreset> presets = new ArrayList<>();
        Set<String> presetIds = new HashSet<>();
        for(int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);
            String id = requireId(value.getString("id"));
            if(id.startsWith("builtin.") || !presetIds.add(id))
                throw new IllegalArgumentException("duplicate or built-in preset id");
            String name = requireName(value.getString("name"));
            JSONArray bindings = value.getJSONArray("bindings");
            if(bindings.length() > MAX_BINDINGS) throw new IllegalArgumentException("too many bindings");
            Map<String, InputGesture> gestures = new TreeMap<>();
            Set<String> bindingIds = new HashSet<>();
            for(int b = 0; b < bindings.length(); b++) {
                JSONObject binding = bindings.getJSONObject(b);
                String actionId = requireId(binding.getString("id"));
                if(!bindingIds.add(actionId)) throw new IllegalArgumentException("duplicate binding id");
                gestures.put(actionId, InputGesture.decode(binding.getString("gesture")));
            }
            presets.add(new HotkeyPreset(id, name, false, gestures));
        }
        return new HotkeyPresetLibrary(selected, presets);
    }

    private static void validateUserPreset(HotkeyPreset preset) {
        if(preset == null || preset.builtIn() || preset.id().startsWith("builtin."))
            throw new IllegalArgumentException("built-in preset cannot be persisted");
        requireId(preset.id());
        requireName(preset.name());
        if(preset.gestures().size() > MAX_BINDINGS)
            throw new IllegalArgumentException("too many bindings");
    }

    private static String requireId(String value) {
        if(value == null || value.isEmpty() || value.length() > 256)
            throw new IllegalArgumentException("invalid id");
        return value;
    }

    private static String requireName(String value) {
        if(value == null || value.trim().isEmpty() || value.length() > MAX_NAME_CHARS)
            throw new IllegalArgumentException("invalid preset name");
        return value.trim();
    }

    private static final class FileCheckpoint implements Checkpoint {
        final boolean existed;
        final byte[] bytes;
        FileCheckpoint(boolean existed, byte[] bytes) {
            this.existed = existed;
            this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
        }
    }

    public static final class LoadResult {
        private final HotkeyPresetLibrary library;
        private final boolean migrationRequired;
        private final String warningKey;

        private LoadResult(HotkeyPresetLibrary library, boolean migrationRequired, String warningKey) {
            this.library = library;
            this.migrationRequired = migrationRequired;
            this.warningKey = warningKey;
        }

        public static LoadResult loaded(HotkeyPresetLibrary library) {
            if(library == null) throw new NullPointerException("library");
            return new LoadResult(library, false, null);
        }

        public static LoadResult migrationRequired(String warningKey) {
            return new LoadResult(null, true, warningKey);
        }

        public HotkeyPresetLibrary library() { return library; }
        public boolean migrationRequired() { return migrationRequired; }
        public String warningKey() { return warningKey; }
    }
}
