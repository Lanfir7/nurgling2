package nurgling.tools;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates read-merge-write persistence for config snapshots shared by several clients. */
public final class NConfigPersistence {
    private NConfigPersistence() {
    }

    public static String mergeAndWrite(String targetPath, String baselineJson, String localJson) throws IOException {
        byte[] written = NFileUtils.updateAtomically(
                targetPath,
                raw -> validObject(raw) != null,
                (target, primary) -> {
            String latestJson = validObject(primary);
            String merged = mergeChangedKeys(baselineJson, localJson, latestJson);
            return merged.getBytes(StandardCharsets.UTF_8);
        });
        return new String(written, StandardCharsets.UTF_8);
    }

    static String mergeChangedKeys(String baselineJson, String localJson, String latestJson) {
        Map<String, Object> baseline = parseObject(baselineJson);
        Map<String, Object> local = parseObject(localJson);
        Map<String, Object> latest = isObject(latestJson)
                ? parseObject(latestJson)
                : new LinkedHashMap<>(baseline);
        Set<String> keys = new HashSet<>(baseline.keySet());
        keys.addAll(local.keySet());

        for (String key : keys) {
            boolean existed = baseline.containsKey(key);
            boolean existsLocally = local.containsKey(key);
            if (existed != existsLocally || !Objects.equals(baseline.get(key), local.get(key))) {
                if (existsLocally) {
                    latest.put(key, local.get(key));
                } else {
                    latest.remove(key);
                }
            }
        }
        return new JSONObject(latest).toString();
    }

    private static String validObject(byte[] raw) {
        if (raw == null) {
            return null;
        }
        String json = new String(raw, StandardCharsets.UTF_8);
        return isObject(json) ? json : null;
    }

    private static boolean isObject(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            new JSONObject(json);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, Object> parseObject(String json) {
        if (!isObject(json)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(new JSONObject(json).toMap());
    }
}
