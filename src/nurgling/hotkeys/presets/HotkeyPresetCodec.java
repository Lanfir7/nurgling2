package nurgling.hotkeys.presets;

import nurgling.hotkeys.InputGesture;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Safe, deterministic codec for clipboard-shareable user presets. */
public final class HotkeyPresetCodec {
    public static final String PREFIX = "NURGLING-HOTKEYS-1:";
    static final int MAX_CODE_CHARS = 64 * 1024;
    static final int MAX_JSON_BYTES = 256 * 1024;
    static final int MAX_NAME_CHARS = 64;
    static final int MAX_BINDINGS = 1024;

    private HotkeyPresetCodec() {}

    public static String encode(HotkeyPreset preset) {
        if(preset == null || preset.builtIn())
            throw new IllegalArgumentException("user preset required");
        validateName(preset.name());
        if(preset.gestures().size() > MAX_BINDINGS)
            throw new IllegalArgumentException("too many bindings");
        for(String id : preset.gestures().keySet()) validateId(id);
        byte[] json = toJson(preset).toString().getBytes(StandardCharsets.UTF_8);
        if(json.length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("preset is too large");
        String code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(gzip(json));
        if(code.length() > MAX_CODE_CHARS)
            throw new IllegalArgumentException("preset code is too large");
        return code;
    }

    public static HotkeyPreset decode(String code) {
        if(code == null || code.length() > MAX_CODE_CHARS || !code.startsWith(PREFIX))
            throw new IllegalArgumentException("invalid preset code");
        try {
            byte[] packed = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            byte[] json = gunzipLimited(packed, MAX_JSON_BYTES);
            JSONObject root = new JSONObject(new String(json, StandardCharsets.UTF_8));
            if(root.getInt("version") != 1)
                throw new IllegalArgumentException("unsupported preset version");
            String name = validateName(root.getString("name"));
            JSONArray bindings = root.getJSONArray("bindings");
            if(bindings.length() > MAX_BINDINGS)
                throw new IllegalArgumentException("too many bindings");
            Set<String> seen = new HashSet<>();
            Map<String, InputGesture> values = new TreeMap<>();
            for(int i = 0; i < bindings.length(); i++) {
                JSONObject binding = bindings.getJSONObject(i);
                String id = validateId(binding.getString("id"));
                if(!seen.add(id))
                    throw new IllegalArgumentException("duplicate binding");
                values.put(id, InputGesture.decode(binding.getString("gesture")));
            }
            return new HotkeyPreset(UUID.randomUUID().toString(), name, false, values);
        } catch(IllegalArgumentException failure) {
            throw failure;
        } catch(Exception failure) {
            throw new IllegalArgumentException("invalid preset code", failure);
        }
    }

    private static JSONObject toJson(HotkeyPreset preset) {
        JSONArray bindings = new JSONArray();
        for(Map.Entry<String, InputGesture> entry : preset.gestures().entrySet()) {
            bindings.put(new JSONObject()
                    .put("id", entry.getKey())
                    .put("gesture", entry.getValue().encode()));
        }
        return new JSONObject()
                .put("version", 1)
                .put("name", preset.name())
                .put("bindings", bindings);
    }

    private static String validateName(String value) {
        if(value == null) throw new IllegalArgumentException("missing preset name");
        String name = value.trim();
        if(name.isEmpty() || name.length() > MAX_NAME_CHARS)
            throw new IllegalArgumentException("invalid preset name");
        return name;
    }

    private static String validateId(String value) {
        if(value == null || value.isEmpty() || value.length() > 256)
            throw new IllegalArgumentException("invalid binding id");
        return value;
    }

    private static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(bytes);
            }
            return out.toByteArray();
        } catch(IOException failure) {
            throw new IllegalStateException("unable to encode preset", failure);
        }
    }

    private static byte[] gunzipLimited(byte[] bytes, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[4096];
            for(int read; (read = gzip.read(buffer)) >= 0;) {
                if(read == 0) continue;
                if(out.size() > limit - read)
                    throw new IllegalArgumentException("preset is too large");
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }
}
