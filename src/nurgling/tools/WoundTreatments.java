package nurgling.tools;

import haven.RichText;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Wound name → healing items, loaded from bundled wiki data.
 */
public final class WoundTreatments {
    private static final String RESOURCE = "/nurgling/data/wound-treatments.json";
    private static final WoundTreatments BUNDLED = loadBundled();

    private final Map<String, List<String>> byNorm;

    WoundTreatments(Map<String, List<String>> byNorm) {
        this.byNorm = byNorm;
    }

    public static List<String> forWound(String woundName) {
        return BUNDLED.lookup(woundName);
    }

    public static WoundTreatments parse(String json) {
        if(json == null || json.isEmpty())
            return new WoundTreatments(Collections.emptyMap());
        return fromObject(new JSONObject(json));
    }

    public List<String> lookup(String woundName) {
        if(woundName == null || woundName.isEmpty())
            return Collections.emptyList();
        List<String> items = byNorm.get(normalize(woundName));
        return items == null ? Collections.emptyList() : items;
    }

    public static boolean isStorageSearchClick(int button, boolean ctrl) {
        return button == 1 && ctrl;
    }

    /** Recipe tooltip plus a faded click-hint footer. */
    public static String treatTipMarkup(String itemName, String hint) {
        String tip = CraftRecipeLookup.ingredientTooltip(itemName);
        if(tip == null || tip.isEmpty())
            return tip;
        String body = tip.replace("$", "$$");
        if(hint == null || hint.isEmpty())
            return body;
        return body + "\n$col[140,140,140]{" + RichText.Parser.quote(hint) + "}";
    }

    /** Candidate gfx/pagina paths for a treatment item icon, VSpec first then name slugs. */
    public static List<String> iconResources(String itemName) {
        if(itemName == null || itemName.isEmpty())
            return Collections.emptyList();
        List<String> paths = new ArrayList<>();
        addPath(paths, VSpec.getIconPath(itemName));
        String slug = itemName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if(!slug.isEmpty()) {
            addPath(paths, "gfx/invobjs/" + slug);
            addPath(paths, "gfx/invobjs/jar-" + slug);
            addPath(paths, "gfx/invobjs/herbs/" + slug);
        }
        return paths;
    }

    private static void addPath(List<String> paths, String path) {
        if(path != null && !path.isEmpty() && !paths.contains(path))
            paths.add(path);
    }

    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static WoundTreatments fromObject(JSONObject obj) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for(String key : obj.keySet()) {
            List<String> items = readItems(obj.optJSONArray(key));
            if(items.isEmpty())
                continue;
            String norm = normalize(key);
            if(norm.isEmpty())
                continue;
            map.putIfAbsent(norm, items);
        }
        return new WoundTreatments(map);
    }

    private static List<String> readItems(JSONArray arr) {
        if(arr == null)
            return Collections.emptyList();
        List<String> items = new ArrayList<>();
        for(int i = 0; i < arr.length(); i++) {
            String item = arr.optString(i, "").trim();
            if(item.isEmpty() || item.equalsIgnoreCase("N/A"))
                continue;
            if(!items.contains(item))
                items.add(item);
        }
        return items.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    private static WoundTreatments loadBundled() {
        try(InputStream in = WoundTreatments.class.getResourceAsStream(RESOURCE)) {
            if(in == null)
                return new WoundTreatments(Collections.emptyMap());
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";
            return parse(json);
        } catch(Exception e) {
            return new WoundTreatments(Collections.emptyMap());
        }
    }
}
