package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSmokeTest {

    @Test
    void smokeResourceIsUpstreamSentinel() {
        assertEquals("ui/tt/smoked", Recipe.SMOKE_RESOURCE);
    }

    @Test
    void sevenArgConstructorKeepsCallersWorkingAndEmptyWoods() {
        Map<String, Recipe.IngredientInfo> ings = new HashMap<String, Recipe.IngredientInfo>();
        ings.put("Pork", new Recipe.IngredientInfo(50.0, "gfx/invobjs/meat"));
        Map<String, Recipe.Fep> feps = new HashMap<String, Recipe.Fep>();
        feps.put("Strength +1", new Recipe.Fep(1.2, 1.0));

        Recipe recipe = new Recipe("h", "Pie", "gfx/invobjs/pie", 2.5, 40, ings, feps);

        assertEquals("h", recipe.getHash());
        assertEquals("Pie", recipe.getName());
        assertEquals("gfx/invobjs/pie", recipe.getResourceName());
        assertEquals(2.5, recipe.getHunger(), 0.0);
        assertEquals(40, recipe.getEnergy());
        assertSame(ings, recipe.getIngredients());
        assertSame(feps, recipe.getFeps());
        assertNotNull(recipe.getSmokingWoods());
        assertTrue(recipe.getSmokingWoods().isEmpty());
        recipe.getIngredients().put("Salt", new Recipe.IngredientInfo(5.0));
        assertTrue(recipe.getIngredients().containsKey("Salt"));
    }

    @Test
    void sevenArgNullMapsBecomeEmptyMutable() {
        Recipe recipe = new Recipe("h", "Pie", "gfx/x", 1.0, 10, null, null);

        assertNotNull(recipe.getIngredients());
        assertNotNull(recipe.getFeps());
        assertNotNull(recipe.getSmokingWoods());
        assertTrue(recipe.getIngredients().isEmpty());
        assertTrue(recipe.getFeps().isEmpty());
        assertTrue(recipe.getSmokingWoods().isEmpty());

        recipe.getIngredients().put("A", new Recipe.IngredientInfo(1.0));
        recipe.getFeps().put("str", new Recipe.Fep(1.0, 1.0));
        recipe.getSmokingWoods().put("Oak", Double.valueOf(100.0));
        assertEquals(1, recipe.getIngredients().size());
        assertEquals(1, recipe.getFeps().size());
        assertEquals(1, recipe.getSmokingWoods().size());
    }

    @Test
    void eightArgCopiesWoodsInSortedNameOrder() {
        Map<String, Recipe.IngredientInfo> ings = new LinkedHashMap<String, Recipe.IngredientInfo>();
        ings.put("Pork", new Recipe.IngredientInfo(80.0, "gfx/pork"));
        Map<String, Double> woods = new LinkedHashMap<String, Double>();
        woods.put("Willow", Double.valueOf(40.0));
        woods.put("Oak", Double.valueOf(60.0));
        Map<String, Recipe.Fep> feps = new HashMap<String, Recipe.Fep>();

        Recipe recipe = new Recipe("h", "Smoked", "gfx/food", 1.0, 20, ings, woods, feps);

        List<String> order = new ArrayList<String>(recipe.getSmokingWoods().keySet());
        assertEquals(Arrays.asList("Oak", "Willow"), order);
        assertEquals(60.0, recipe.getSmokingWoods().get("Oak"), 0.0);
        assertEquals(40.0, recipe.getSmokingWoods().get("Willow"), 0.0);
        assertFalse(recipe.getIngredients().containsKey("Oak"));
        assertFalse(recipe.getIngredients().containsKey("Willow"));
        woods.put("Birch", Double.valueOf(10.0));
        assertFalse(recipe.getSmokingWoods().containsKey("Birch"));
    }

    @Test
    void eightArgNullWoodsIsEmptyNonNull() {
        Recipe recipe = new Recipe("h", "Pie", "gfx/x", 1.0, 10,
                new HashMap<String, Recipe.IngredientInfo>(),
                null,
                new HashMap<String, Recipe.Fep>());
        assertNotNull(recipe.getSmokingWoods());
        assertTrue(recipe.getSmokingWoods().isEmpty());
    }

    @Test
    void addIngredientRowClassifiesSmokeVsIngredient() {
        Recipe recipe = new Recipe("h", "Pie", "gfx/x", 1.0, 10,
                new HashMap<String, Recipe.IngredientInfo>(),
                new HashMap<String, Recipe.Fep>());

        recipe.addIngredientRow("Pork", 70.0, "gfx/invobjs/meat");
        recipe.addIngredientRow("Oak", 100.0, Recipe.SMOKE_RESOURCE);
        recipe.addIngredientRow("Salt", 5.0, null);
        recipe.addIngredientRow(null, 1.0, Recipe.SMOKE_RESOURCE);
        recipe.addIngredientRow(null, 1.0, "gfx/x");

        assertTrue(recipe.getIngredients().containsKey("Pork"));
        assertTrue(recipe.getIngredients().containsKey("Salt"));
        assertFalse(recipe.getIngredients().containsKey("Oak"));
        assertEquals(100.0, recipe.getSmokingWoods().get("Oak"), 0.0);
        assertFalse(recipe.getSmokingWoods().containsKey("Pork"));
        assertEquals(2, recipe.getIngredients().size());
        assertEquals(1, recipe.getSmokingWoods().size());
    }
}
