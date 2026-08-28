package nurgling.cookbook.upload;

import nurgling.cookbook.Recipe;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public final class CookbookUploadRecord {
    private final String key;
    private final Recipe recipe;
    private final String genus;

    private CookbookUploadRecord(String key, Recipe recipe, String genus) {
        this.key = key;
        this.recipe = recipe;
        this.genus = genus;
    }

    public static CookbookUploadRecord from(Recipe recipe, String genus) {
        if (recipe == null)
            throw new IllegalArgumentException("recipe must not be null");
        if (genus == null || genus.trim().isEmpty())
            throw new IllegalArgumentException("genus must not be empty");
        return new CookbookUploadRecord(recipe.getHash(), recipe, genus.trim());
    }

    public String key() {
        return key;
    }

    public JSONObject toJson() {
        JSONObject out = new JSONObject()
                .put("itemName", recipe.getName())
                .put("resourceName", recipe.getResourceName())
                .put("energy", recipe.getEnergy())
                .put("hunger", recipe.getHunger());
        out.put("genus", genus);

        JSONArray ingredients = new JSONArray();
        for (Map.Entry<String, Recipe.IngredientInfo> entry : recipe.getIngredients().entrySet()) {
            ingredients.put(new JSONObject()
                    .put("name", entry.getKey())
                    .put("percentage", entry.getValue().percentage));
        }
        out.put("ingredients", ingredients);

        JSONArray feps = new JSONArray();
        for (Map.Entry<String, Recipe.Fep> entry : recipe.getFeps().entrySet()) {
            feps.put(new JSONObject()
                    .put("name", entry.getKey())
                    .put("value", entry.getValue().val));
        }
        out.put("feps", feps);
        return out;
    }
}
