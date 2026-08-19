package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIngredientCacheTest {

    private static final String CAT = "RecipeLookupTestGroup";
    private static final String ALIAS_CAT = "RecipeLookupTestAliasGroup";

    @AfterEach
    void cleanup() {
        VSpec.categories.remove(CAT);
        VSpec.categories.remove(ALIAS_CAT);
        RecipeIngredientCache.clear();
    }

    @Test
    void findInputRecipesIncludesRecipesThatUseTheVSpecGroup() {
        putCategory(CAT, member("TestBerry", "gfx/invobjs/testberry"));
        RecipeIngredientCache.addInputMapping(CAT, "pagina/group-recipe", "Group Recipe");
        RecipeIngredientCache.addInputMapping("TestBerry", "pagina/specific", "Specific Recipe");

        Set<String> found = resources(RecipeIngredientCache.findInputRecipes(List.of("TestBerry")));

        assertTrue(found.contains("pagina/specific"));
        assertTrue(found.contains("pagina/group-recipe"));
    }

    @Test
    void findInputRecipesMatchesGroupByResourceWhenMemberNameDiffers() {
        putCategory(ALIAS_CAT, member("Blueberry", "gfx/invobjs/herbs/blueberry"));
        RecipeIngredientCache.addInputMapping(ALIAS_CAT, "pagina/fruit-or-berry", "Wanderer's Lunch");

        Set<String> found = resources(RecipeIngredientCache.findInputRecipes(
                List.of("Blueberries"),
                List.of("gfx/invobjs/herbs/blueberry")));

        assertTrue(found.contains("pagina/fruit-or-berry"));
    }

    @Test
    void ingredientTooltipListsRecipeInputs() {
        RecipeIngredientCache.addOutputMapping("Stitch Patch", "pagina/stitchpatch", "Stitch Patch");
        List<RecipeIngredientCache.IngredientSpec> specs = new ArrayList<>();
        specs.add(new RecipeIngredientCache.IngredientSpec("Bone Needle", 1));
        specs.add(new RecipeIngredientCache.IngredientSpec("Silk Thread", 2));
        RecipeIngredientCache.setRecipeSpecsFromDB("pagina/stitchpatch", specs);

        String tip = CraftRecipeLookup.ingredientTooltip("Stitch Patch");

        assertTrue(tip.startsWith("Stitch Patch"));
        assertTrue(tip.contains("- Bone Needle"));
        assertTrue(tip.contains("- Silk Thread x2"));
    }

    @Test
    void ingredientTooltipWithoutRecipeIsJustTheName() {
        org.junit.jupiter.api.Assertions.assertEquals("Yarrow", CraftRecipeLookup.ingredientTooltip("Yarrow"));
    }

    private static void putCategory(String key, JSONObject member) {
        ArrayList<JSONObject> members = new ArrayList<>();
        members.add(member);
        VSpec.categories.put(key, members);
    }

    private static JSONObject member(String name, String resource) {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("static", resource);
        return obj;
    }

    private static Set<String> resources(Set<RecipeIngredientCache.RecipeEntry> recipes) {
        return recipes.stream().map(e -> e.paginaResource).collect(Collectors.toSet());
    }
}
