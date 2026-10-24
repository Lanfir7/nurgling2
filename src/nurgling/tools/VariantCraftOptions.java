package nurgling.tools;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Craft catalog choices plus the two inputs whose kinds share one game icon. */
public final class VariantCraftOptions {
    private VariantCraftOptions() {}

    public static boolean has(String input) {
        return CraftCategories.has(input) || !ItemVariants.craftOptions(input).isEmpty();
    }

    public static ArrayList<JSONObject> options(String input) {
        ArrayList<JSONObject> catalog = CraftCategories.options(input);
        ArrayList<JSONObject> result = catalog == null ? new ArrayList<>() : catalog;
        Set<String> names = new HashSet<>();
        for (JSONObject option : result)
            names.add(option.optString("name"));
        // Keep the old plain input selectable: it still accepts any kind.
        if ((ItemVariants.BUTTER.equals(input) || ItemVariants.MILK.equals(input)) && names.add(input)) {
            String icon = ItemVariants.BUTTER.equals(input) ? "gfx/invobjs/butter" : "gfx/invobjs/milk";
            result.add(new JSONObject().put("static", icon).put("name", input));
        }
        for (JSONObject option : ItemVariants.craftOptions(input)) {
            if (names.add(option.optString("name")))
                result.add(option);
        }
        return result;
    }
}
