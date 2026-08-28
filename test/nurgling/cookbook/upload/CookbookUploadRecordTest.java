package nurgling.cookbook.upload;

import nurgling.cookbook.Recipe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CookbookUploadRecordTest {
    @Test
    void serializesServerCompatibleFoodRecord() {
        Map<String, Recipe.IngredientInfo> ingredients = new LinkedHashMap<>();
        ingredients.put("Fox Meat", new Recipe.IngredientInfo(60.0, "gfx/invobjs/meat-fox"));
        ingredients.put("Carrot", new Recipe.IngredientInfo(40.0, "gfx/invobjs/carrot"));
        Map<String, Recipe.Fep> feps = new LinkedHashMap<>();
        feps.put("Strength +2", new Recipe.Fep(8.25, 0.75));

        Recipe recipe = new Recipe("recipe-hash", "Autumn Steak", "gfx/invobjs/autumnsteak",
                1.75, 80, ingredients, feps);

        JSONObject json = CookbookUploadRecord.from(recipe, "w17").toJson();

        assertEquals("Autumn Steak", json.getString("itemName"));
        assertEquals("gfx/invobjs/autumnsteak", json.getString("resourceName"));
        assertEquals(80, json.getInt("energy"));
        assertEquals(1.75, json.getDouble("hunger"), 0.0001);
        assertEquals("w17", json.getString("genus"));

        JSONArray ingredientJson = json.getJSONArray("ingredients");
        assertEquals(2, ingredientJson.length());
        assertEquals("Fox Meat", ingredientJson.getJSONObject(0).getString("name"));
        assertEquals(60.0, ingredientJson.getJSONObject(0).getDouble("percentage"), 0.0001);

        JSONArray fepJson = json.getJSONArray("feps");
        assertEquals(1, fepJson.length());
        assertEquals("Strength +2", fepJson.getJSONObject(0).getString("name"));
        assertEquals(8.25, fepJson.getJSONObject(0).getDouble("value"), 0.0001);
    }

    @Test
    void rejectsEmptyWorldIdentifier() {
        Recipe recipe = new Recipe("hash", "Bread", "gfx/invobjs/bread",
                0.5, 100, new LinkedHashMap<>(), new LinkedHashMap<>());

        assertThrows(IllegalArgumentException.class,
                () -> CookbookUploadRecord.from(recipe, ""));
    }
}
