package nurgling.craftatlas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bundled, offline Ring of Brodgar recipe snapshot. */
public final class WikiReferenceCatalog {
    private static final String RESOURCE = "/nurgling/craftatlas/wiki-reference.json";

    private WikiReferenceCatalog() { }

    public static List<CraftAtlasEntry> loadBundled() {
        InputStream input = WikiReferenceCatalog.class.getResourceAsStream(RESOURCE);
        if(input == null) return Collections.emptyList();
        try(InputStream closeable = input) {
            return parse(closeable);
        } catch(Exception e) {
            System.err.println("Unable to load Craft Atlas wiki reference: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    static List<CraftAtlasEntry> parse(InputStream input) throws IOException {
        StringBuilder text = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        JSONArray values = new JSONObject(text.toString()).optJSONArray("entries");
        if(values == null) return Collections.emptyList();
        List<CraftAtlasEntry> entries = new ArrayList<>();
        for(int i = 0; i < values.length(); i++) entries.add(decode(values.getJSONObject(i)));
        return Collections.unmodifiableList(entries);
    }

    public static String itemResource(String name) {
        String slug = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("^-+|-+$", "");
        return "wiki-item:" + slug;
    }

    private static CraftAtlasEntry decode(JSONObject value) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder(value.getString("id"), value.getString("name"))
                .output(value.optString("output", itemResource(value.getString("name"))))
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .description(value.optString("description", null));
        JSONArray categories = value.optJSONArray("categories");
        if(categories != null) for(int i = 0; i < categories.length(); i++) builder.category(categories.getString(i));
        JSONArray inputs = value.optJSONArray("inputs");
        if(inputs != null) for(int i = 0; i < inputs.length(); i++) {
            JSONObject input = inputs.getJSONObject(i);
            builder.input(new CraftAtlasEntry.InputSlot(Math.max(1, input.optInt("quantity", 1)),
                    input.optBoolean("optional", false), Collections.singletonList(
                    new CraftAtlasEntry.IngredientOption(input.optString("resource", itemResource(input.getString("name"))),
                            input.getString("name")))));
        }
        JSONArray requirements = value.optJSONArray("requirements");
        if(requirements != null) for(int i = 0; i < requirements.length(); i++) {
            JSONObject requirement = requirements.getJSONObject(i);
            builder.requirement(new CraftAtlasEntry.Requirement(
                    CraftAtlasEntry.RequirementKind.valueOf(requirement.getString("kind")),
                    emptyToNull(requirement.optString("resource", null)), requirement.getString("name"),
                    emptyToNull(requirement.optString("description", null))));
        }
        JSONArray bonuses = value.optJSONArray("bonuses");
        if(bonuses != null) for(int i = 0; i < bonuses.length(); i++) {
            JSONObject bonus = bonuses.getJSONObject(i);
            builder.bonus(new CraftAtlasEntry.Bonus(bonus.optString("resource", "wiki-bonus:" + i),
                    bonus.getString("name"), bonus.has("value") && !bonus.isNull("value") ? bonus.getDouble("value") : null));
        }
        return builder.build();
    }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
