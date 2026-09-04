package nurgling.craftatlas;

import nurgling.NConfig;
import nurgling.tools.NFileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profile-local cache of recipes the server has shown in Make windows. */
public final class CraftAtlasObservationStore {
    private static CraftAtlasObservationStore current;
    private final Path file;
    private final Map<String, CraftAtlasObservation> observations = new LinkedHashMap<>();
    private long revision;

    public CraftAtlasObservationStore(Path file) {
        this.file = file;
        load();
    }

    public static synchronized CraftAtlasObservationStore current() {
        Path path = Paths.get(NConfig.current == null ? "craft_atlas_recipes.nurgling.json"
                : NConfig.current.getProfileAwarePath("craft_atlas_recipes.nurgling.json"));
        if(current == null || !current.file.toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize()))
            current = new CraftAtlasObservationStore(path);
        return current;
    }

    public synchronized CraftAtlasObservation get(String resource) { return observations.get(resource); }
    public synchronized long revision() { return revision; }
    public synchronized Map<String, CraftAtlasObservation> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(observations));
    }

    public synchronized void record(CraftAtlasObservation observation) {
        if(observation == null) return;
        observations.put(observation.recipeResource, observation);
        revision++;
        save();
    }

    private void load() {
        if(file == null || !Files.isRegularFile(file)) return;
        try {
            JSONObject root = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            JSONArray entries = root.optJSONArray("recipes");
            if(entries == null) return;
            for(int i = 0; i < entries.length(); i++) {
                CraftAtlasObservation observation = decode(entries.getJSONObject(i));
                observations.put(observation.recipeResource, observation);
            }
        } catch(Exception ignored) {
            observations.clear();
        }
    }

    private void save() {
        try {
            JSONArray values = new JSONArray();
            for(CraftAtlasObservation observation : observations.values()) values.put(encode(observation));
            NFileUtils.writeAtomically(file.toString(), new JSONObject().put("recipes", values).toString(2));
        } catch(Exception e) {
            System.err.println("Unable to save Craft Atlas observations: " + e.getMessage());
        }
    }

    private static JSONObject encode(CraftAtlasObservation o) {
        JSONObject value = new JSONObject().put("recipe", o.recipeResource).put("name", o.displayName);
        value.put("inputs", encodeItems(o.inputs)).put("outputs", encodeItems(o.outputs));
        JSONArray requirements = new JSONArray();
        for(CraftAtlasObservation.RequirementResource r : o.requirements)
            requirements.put(new JSONObject().put("resource", r.resource).put("name", r.name));
        JSONArray bonuses = new JSONArray();
        for(CraftAtlasObservation.BonusResource b : o.bonuses) {
            JSONObject item = new JSONObject().put("resource", b.resource).put("name", b.name);
            if(b.value != null) item.put("value", b.value);
            bonuses.put(item);
        }
        return value.put("requirements", requirements).put("bonuses", bonuses);
    }

    private static JSONArray encodeItems(List<CraftAtlasObservation.Item> source) {
        JSONArray values = new JSONArray();
        for(CraftAtlasObservation.Item item : source)
            values.put(new JSONObject().put("resource", item.resource).put("name", item.name)
                    .put("quantity", item.quantity).put("optional", item.optional));
        return values;
    }

    private static CraftAtlasObservation decode(JSONObject value) {
        List<CraftAtlasObservation.RequirementResource> requirements = new ArrayList<>();
        JSONArray req = value.optJSONArray("requirements");
        if(req != null) for(int i = 0; i < req.length(); i++) {
            JSONObject r = req.getJSONObject(i);
            requirements.add(new CraftAtlasObservation.RequirementResource(r.optString("resource", null), r.optString("name", null)));
        }
        List<CraftAtlasObservation.BonusResource> bonuses = new ArrayList<>();
        JSONArray bon = value.optJSONArray("bonuses");
        if(bon != null) for(int i = 0; i < bon.length(); i++) {
            JSONObject b = bon.getJSONObject(i);
            bonuses.add(new CraftAtlasObservation.BonusResource(b.optString("resource", null), b.optString("name", null),
                    b.has("value") ? b.optDouble("value") : null));
        }
        return new CraftAtlasObservation(value.getString("recipe"), value.optString("name", null),
                decodeItems(value.optJSONArray("inputs")), decodeItems(value.optJSONArray("outputs")), requirements, bonuses);
    }

    private static List<CraftAtlasObservation.Item> decodeItems(JSONArray values) {
        List<CraftAtlasObservation.Item> items = new ArrayList<>();
        if(values != null) for(int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            items.add(new CraftAtlasObservation.Item(item.optString("resource", null), item.optString("name", null),
                    item.optInt("quantity", 1), item.optBoolean("optional", false)));
        }
        return items;
    }
}
