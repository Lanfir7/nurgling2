package nurgling.actions.bots;

import nurgling.areas.NContext;
import nurgling.tools.RecipeIngredientCache;
import nurgling.tools.VSpec;
import org.json.JSONObject;

import java.util.*;

/**
 * Resolves missing ingredients by finding sub-recipes that produce them.
 * Used in AUTO craft mode when a zone for an ingredient is not found.
 */
public class SubRecipeResolver {

    private static final int MAX_DEPTH = 10;

    /**
     * Check if an item has an input zone available (directly or via category members).
     */
    public static boolean hasZone(String itemName) {
        if (NContext.findIn(itemName) != null) return true;
        if (NContext.findInGlobal(itemName) != null) return true;
        ArrayList<JSONObject> cats = VSpec.categories.get(itemName);
        if (cats != null) {
            for (JSONObject obj : cats) {
                String name = obj.optString("name", null);
                if (name != null && (NContext.findIn(name) != null || NContext.findInGlobal(name) != null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if an item can be sub-crafted (has a recipe in the output cache).
     */
    public static boolean canSubCraft(String itemName) {
        if (itemName == null) return false;
        return !RecipeIngredientCache.findOutputRecipesForItem(itemName).isEmpty();
    }

    /**
     * Find recipes that produce the given item.
     */
    public static Set<RecipeIngredientCache.RecipeEntry> findRecipesFor(String itemName) {
        return RecipeIngredientCache.findOutputRecipesForItem(itemName);
    }
}
