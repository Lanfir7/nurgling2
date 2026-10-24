package nurgling.tools;

import nurgling.cookbook.GenericTypes;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftCategoriesTest {
    private static boolean hasName(List<JSONObject> options, String name) {
        return options != null && options.stream().anyMatch(o -> name.equals(o.optString("name")));
    }

    @Test
    void craftOnlyCorrectionsDoNotChangeLegacyStackMembershipOrCookbookMatching() throws Exception {
        assertTrue(GenericTypes.bundled().members("Edible Mushroom").contains("Block of Trombone Chantrelle"));
        assertTrue(VSpec.categories.get("Edible Mushroom").stream()
                .anyMatch(o -> "Block of Trombone Chantrelle".equals(o.optString("name"))));
        assertFalse(hasName(CraftCategories.options("Edible Mushroom"), "Block of Trombone Chantrelle"));
        assertFalse(hasName(CraftCategories.options("Nuts"), "Beech Seed"));
        assertTrue(hasName(CraftCategories.options("Nuts"), "Withercorn"));
        assertTrue(hasName(VSpec.categories.get("Nuts"), "Witherstand Seed"));
        assertTrue(hasName(CraftCategories.options("Raw Meat"), "Bullfinch Meat"));
        assertEquals(List.of("Duck Meat"), GenericTypes.itemNames("Raw Duck Meat"));
        assertTrue(GenericTypes.stockNames("Raw Duck Meat").contains("Raw Duck Meat"));
        assertEquals("gfx/invobjs/seed-acacia", GenericTypes.icon("Acacia Pod"));
    }

    @Test
    void milkAndButterVariantsStayDistinctWhenTheirSpritesAreIdentical() {
        ArrayList<JSONObject> milk = VariantCraftOptions.options("Milk");
        assertTrue(hasName(milk, "Milk"));
        assertTrue(hasName(milk, "Cowsmilk"));
        assertTrue(hasName(milk, "Goatsmilk"));
        assertTrue(hasName(milk, "Sheepsmilk"));
        ArrayList<JSONObject> butter = VariantCraftOptions.options("Butter");
        assertTrue(hasName(butter, "Butter"));
        assertTrue(hasName(butter, "Butter (Cowsmilk)"));
        assertTrue(hasName(butter, "Butter (Goatsmilk)"));
        assertTrue(hasName(butter, "Butter (Sheepsmilk)"));
        assertTrue(hasName(butter, "Butter (Generic)"));
    }

    @Test
    void missingIconNeverFreezesAnIncompleteCategoryAndReturnedJsonCannotPoisonIt() throws Exception {
        String category = "Craft Test Rare Type";
        String member = "Craft Test Rare Ingredient 8391";
        Field typesField = CraftCategories.class.getDeclaredField("types");
        typesField.setAccessible(true);
        Field cacheField = CraftCategories.class.getDeclaredField("complete");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ArrayList<JSONObject>> cache = (Map<String, ArrayList<JSONObject>>) cacheField.get(null);
        Object oldTypes = typesField.get(null);
        try {
            Constructor<GenericTypes> ctor = GenericTypes.class.getDeclaredConstructor(Map.class);
            ctor.setAccessible(true);
            typesField.set(null, ctor.newInstance(Map.of(category, List.of(member))));
            cache.remove(category);
            assertTrue(CraftCategories.has(category));
            assertFalse(hasName(CraftCategories.options(category), member));
            assertFalse(cache.containsKey(category));
            ItemIcons.register(member, new JSONObject().put("static", "gfx/invobjs/pretuber"));
            ArrayList<JSONObject> resolved = CraftCategories.options(category);
            assertTrue(hasName(resolved, member));
            assertTrue(cache.containsKey(category));
            resolved.get(0).put("name", "Corrupted by caller");
            assertTrue(hasName(CraftCategories.options(category), member));
        } finally {
            typesField.set(null, oldTypes);
            cache.remove(category);
        }
    }
}
