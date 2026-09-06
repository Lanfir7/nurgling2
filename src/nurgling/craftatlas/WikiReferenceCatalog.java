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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bundled, offline Ring of Brodgar recipe snapshot. */
public final class WikiReferenceCatalog {
    private static final String RESOURCE = "/nurgling/craftatlas/wiki-reference.json";
    private static final Pattern GILD_CHANCE = Pattern.compile("(?i).*?(\\d+(?:\\.\\d+)?)%\\s*[-–]\\s*(\\d+(?:\\.\\d+)?)%");
    private static final Set<String> GEMSTONES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "amber", "amethyst", "diamond", "emerald", "jade", "moonstone",
            "onyx", "opal", "ruby", "sapphire", "topaz", "turquoise")));

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
        for(int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            if(!isGemstone(value.optString("name", ""))) entries.add(decode(value));
        }
        return Collections.unmodifiableList(entries);
    }

    private static boolean isGemstone(String name) {
        return GEMSTONES.contains(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
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
        JSONArray equipmentSlots = value.optJSONArray("equipmentSlots");
        if(equipmentSlots != null) for(int i = 0; i < equipmentSlots.length(); i++)
            builder.equipmentSlot(equipmentSlots.getString(i));
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
            CraftAtlasEntry.RequirementKind kind = CraftAtlasEntry.RequirementKind.valueOf(requirement.getString("kind"));
            String resource = emptyToNull(requirement.optString("resource", null));
            String name = requirement.getString("name");
            if(kind == CraftAtlasEntry.RequirementKind.SKILL)
                resource = CraftAtlasAttributes.resource(name, resource);
            builder.requirement(new CraftAtlasEntry.Requirement(kind, resource, name,
                    emptyToNull(requirement.optString("description", null))));
        }
        CraftAtlasEntry.Gilding gilding = decodeGilding(value.optJSONObject("gilding"));
        JSONObject curiosity = value.optJSONObject("curiosity");
        if(curiosity != null) builder.curiosity(new CraftAtlasEntry.Curiosity(
                curiosity.optInt("learningPoints", 0), curiosity.optInt("studyMinutes", 0),
                curiosity.optInt("mentalWeight", 0)));
        JSONArray bonuses = value.optJSONArray("bonuses");
        if(bonuses != null) for(int i = 0; i < bonuses.length(); i++) {
            JSONObject bonus = bonuses.getJSONObject(i);
            String name = bonus.getString("name");
            Matcher chance = GILD_CHANCE.matcher(name);
            if(chance.matches()) {
                if(gilding == null) gilding = new CraftAtlasEntry.Gilding(
                        Double.parseDouble(chance.group(1)) / 100.0,
                        Double.parseDouble(chance.group(2)) / 100.0,
                        Collections.<CraftAtlasEntry.AttributeRef>emptyList());
                continue;
            }
            String resource = bonus.optString("resource", "wiki-bonus:" + i);
            builder.bonus(new CraftAtlasEntry.Bonus(CraftAtlasAttributes.resource(name, resource), name,
                    bonus.has("value") && !bonus.isNull("value") ? bonus.getDouble("value") : null));
        }
        builder.gilding(gilding);
        return builder.build();
    }

    private static CraftAtlasEntry.Gilding decodeGilding(JSONObject value) {
        if(value == null) return null;
        List<CraftAtlasEntry.AttributeRef> attributes = new ArrayList<>();
        JSONArray values = value.optJSONArray("attributes");
        if(values != null) for(int i = 0; i < values.length(); i++) {
            Object raw = values.get(i);
            if(raw instanceof JSONObject) {
                JSONObject attribute = (JSONObject)raw;
                attributes.add(CraftAtlasAttributes.ref(emptyToNull(attribute.optString("resource", null)),
                        attribute.getString("name")));
            } else {
                String name = String.valueOf(raw);
                attributes.add(CraftAtlasAttributes.ref(null, name));
            }
        }
        return new CraftAtlasEntry.Gilding(value.optDouble("min", 0), value.optDouble("max", 0), attributes);
    }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
