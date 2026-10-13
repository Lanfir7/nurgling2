package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VariationsStockTest {
    @Test
    void presenceRequiresEveryIngredientTypeAndSmokingWood() throws Exception {
        HashMap<String, Recipe.IngredientInfo> ingredients = new HashMap<>();
        ingredients.put("Carrot", new Recipe.IngredientInfo(100, "gfx/invobjs/carrot"));
        HashMap<String, Double> woods = new HashMap<>();
        woods.put("Oak", 100.0);
        Recipe recipe = new Recipe("r", "Smoked Carrot", "gfx/invobjs/carrot", 1, 1,
                ingredients, woods, new HashMap<>());
        Variations variations = new Variations(Collections.singletonList(recipe), Collections.emptyList(),
                GenericTypes.bundled());
        Set<String> stored = new HashSet<>();
        stored.add(GenericTypes.stockKey("Carrot"));
        assertFalse(variations.cookable(recipe, stored));
        stored.add(GenericTypes.stockKey("Block of Oak"));
        assertTrue(variations.cookable(recipe, stored));
    }
}
