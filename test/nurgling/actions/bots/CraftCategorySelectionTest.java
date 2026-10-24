package nurgling.actions.bots;

import nurgling.tools.CraftCategories;
import nurgling.tools.VSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftCategorySelectionTest {
    @Test
    void catalogOnlyCategoryStillSelectsConcreteIngredientWhenPresetRequestsCategory() {
        assertTrue(CraftCategories.has("Any Flour"));
        assertFalse(VSpec.categories.containsKey("Any Flour"));
        assertTrue(Craft.shouldAutoSelectCategory("Any Flour", true));
    }

    @Test
    void existingSharedCategoryKeepsItsBroadFetchRule() {
        assertTrue(VSpec.categories.containsKey("Nuts"));
        assertFalse(Craft.shouldAutoSelectCategory("Nuts", true));
        assertTrue(Craft.shouldAutoSelectCategory("Nuts", false));
    }
}
