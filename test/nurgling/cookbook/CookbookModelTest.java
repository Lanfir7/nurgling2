package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CookbookModelTest {
    private static final String STR1 = FepAttr.STR.key(1);
    private static final String STR2 = FepAttr.STR.key(2);
    private static final String WIL1 = FepAttr.WIL.key(1);

    @Test
    void emptyModelHasNoRows() {
        CookbookModel model = new CookbookModel();
        assertEquals(0, model.total());
        assertTrue(model.view().isEmpty());
        assertTrue(model.ingredientNames().isEmpty());
        assertEquals(0, model.queryVersion());
    }

    @Test
    void setRecipesNullAndEmptyClearCatalogAndBumpQueryVersion() {
        CookbookModel model = new CookbookModel();
        model.setRecipes(catalog());
        assertEquals(5, model.total());
        assertFalse(model.view().isEmpty());
        assertFalse(model.ingredientNames().isEmpty());
        int afterLoad = model.queryVersion();
        assertTrue(afterLoad > 0);

        model.setRecipes(null);
        assertEquals(0, model.total());
        assertTrue(model.view().isEmpty());
        assertTrue(model.ingredientNames().isEmpty());
        assertTrue(model.queryVersion() > afterLoad);

        int afterNull = model.queryVersion();
        model.setRecipes(catalog());
        int afterReload = model.queryVersion();
        assertTrue(afterReload > afterNull);

        model.setRecipes(Collections.<Recipe>emptyList());
        assertEquals(0, model.total());
        assertTrue(model.view().isEmpty());
        assertTrue(model.ingredientNames().isEmpty());
        assertTrue(model.queryVersion() > afterReload);
    }

    @Test
    void viewReturnsEveryFilteredRowWithNoSilentCap() {
        List<Recipe> many = new ArrayList<Recipe>();
        for(int i = 0; i < 40; i++) {
            many.add(recipe("h-" + i, "Dish " + i, 1.0, i,
                    ings(i % 2 == 0 ? "Pork" : "Apple"),
                    feps("Strength +1", 1.0 + i)));
        }
        CookbookModel model = new CookbookModel();
        model.setRecipes(many);
        assertEquals(40, model.total());
        assertEquals(40, model.view().size());

        model.setIngredients(Collections.singleton("Pork"));
        assertEquals(40, model.total());
        assertEquals(20, model.view().size());
        for(CookbookRow row : model.view())
            assertTrue(row.sourceNames.contains("Pork"), row.name());
    }

    @Test
    void emptyFepSelectionLetsEveryRowThrough() {
        CookbookModel model = loaded();
        assertTrue(model.fepCodes().isEmpty());
        assertEquals(CookbookModel.Mode.ANY, model.fepMode());
        assertEquals(names(catalog()), names(model.view()));

        model.setFepMode(CookbookModel.Mode.ALL);
        assertEquals(names(catalog()), names(model.view()));
        model.setFepMode(CookbookModel.Mode.NONE);
        assertEquals(names(catalog()), names(model.view()));
    }

    @Test
    void fepAnyAllNoneFilterByCodes() {
        CookbookModel model = loaded();
        model.setFepCodes(Arrays.asList(STR2, WIL1));

        model.setFepMode(CookbookModel.Mode.ANY);
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill", "Will Broth"), names(model.view()));

        model.setFepMode(CookbookModel.Mode.ALL);
        assertEquals(Collections.singletonList("Mixed Grill"), names(model.view()));

        model.setFepMode(CookbookModel.Mode.NONE);
        assertEquals(Arrays.asList("Apple Pie", "Water"), names(model.view()));
    }

    @Test
    void toggleFepAddsAndRemovesCode() {
        CookbookModel model = loaded();
        model.toggleFep(STR2);
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill"), names(model.view()));
        model.toggleFep(STR2);
        assertTrue(model.fepCodes().isEmpty());
        assertEquals(names(catalog()), names(model.view()));
    }

    @Test
    void fepFilterDistinguishesTierCodesNotNumericThresholds() {
        CookbookModel model = loaded();
        for(CookbookRow row : model.view()) {
            if("Apple Pie".equals(row.name())) {
                assertTrue(row.codes.contains(STR1));
                assertFalse(row.codes.contains(STR2));
            }
            if("Beef Stew".equals(row.name())) {
                assertTrue(row.codes.contains(STR2));
                assertFalse(row.codes.contains(STR1));
            }
        }

        model.setFepCodes(Collections.singleton(STR2));
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill"), names(model.view()));

        model.setFepCodes(Collections.singleton(STR1));
        assertEquals(Collections.singletonList("Apple Pie"), names(model.view()));

        model.setFepCodes(Collections.<String>emptySet());
        model.setSearch("str2>5");
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill"), names(model.view()));
        assertFalse(names(model.view()).contains("Apple Pie"));
    }

    @Test
    void emptyIngredientSelectionLetsEveryRowThrough() {
        CookbookModel model = loaded();
        assertTrue(model.ingredients().isEmpty());
        assertEquals(names(catalog()), names(model.view()));
        model.setIngredientMode(CookbookModel.Mode.ALL);
        assertEquals(names(catalog()), names(model.view()));
        model.setIngredientMode(CookbookModel.Mode.NONE);
        assertEquals(names(catalog()), names(model.view()));
    }

    @Test
    void smokingWoodsAppearInSourceNamesFilterAndDisplay() {
        Map<String, Double> woods = new LinkedHashMap<String, Double>();
        woods.put("Willow", Double.valueOf(40.0));
        woods.put("Oak", Double.valueOf(60.0));
        Recipe smoked = new Recipe("h-sm", "Smoked Pork", "gfx/invobjs/food", 1.0, 20,
                ings("Pork"), woods, feps("Strength +1", 2.0));
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(smoked));
        model.setFavoritesFirst(false);

        assertTrue(model.ingredientNames().contains("Oak"));
        assertTrue(model.ingredientNames().contains("Willow"));
        assertTrue(model.ingredientNames().contains("Pork"));

        CookbookRow row = model.view().get(0);
        assertTrue(row.sourceNames.contains("Oak"));
        assertTrue(row.sourceNames.contains("Willow"));
        assertTrue(row.sourceNames.contains("Pork"));
        assertEquals("Pork", row.ingredientText);
        assertEquals("Oak, Willow", row.woodText);
        assertEquals(60.0, CookbookRow.smokingWoods(smoked).get("Oak"), 0.0);
        assertTrue(CookbookRow.smokingWoods(null).isEmpty());

        model.setIngredients(Collections.singleton("Oak"));
        assertEquals(Collections.singletonList("Smoked Pork"), names(model.view()));

        model.setSearch("from:willow");
        model.setIngredients(Collections.<String>emptySet());
        assertEquals(Collections.singletonList("Smoked Pork"), names(model.view()));
    }

    @Test
    void ingredientAnyAllNoneFilterBySourceNames() {
        CookbookModel model = loaded();
        assertTrue(model.ingredientNames().containsAll(Arrays.asList("Apple", "Flour", "Pork", "Salt", "Water")));

        model.setIngredients(Arrays.asList("Pork", "Apple"));
        model.setIngredientMode(CookbookModel.Mode.ANY);
        assertEquals(Arrays.asList("Apple Pie", "Beef Stew", "Mixed Grill", "Will Broth"), names(model.view()));

        model.setIngredientMode(CookbookModel.Mode.ALL);
        assertEquals(Collections.singletonList("Mixed Grill"), names(model.view()));

        model.setIngredientMode(CookbookModel.Mode.NONE);
        assertEquals(Collections.singletonList("Water"), names(model.view()));
    }

    @Test
    void toggleIngredientAddsAndRemovesName() {
        CookbookModel model = loaded();
        model.toggleIngredient("Pork");
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill", "Will Broth"), names(model.view()));
        model.toggleIngredient("Pork");
        assertTrue(model.ingredients().isEmpty());
        assertEquals(names(catalog()), names(model.view()));
    }

    @Test
    void setSearchUsesSearchQueryAndNullBecomesEmpty() {
        CookbookModel model = loaded();
        model.setSearch("PIE");
        assertEquals("PIE", model.search());
        assertEquals(Collections.singletonList("Apple Pie"), names(model.view()));

        model.setSearch("from:pork");
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill", "Will Broth"), names(model.view()));

        model.setSearch(null);
        assertEquals("", model.search());
        assertEquals(names(catalog()), names(model.view()));
    }

    @Test
    void sortByReversesSameColumnAndDefaultsTextAscNumericDesc() {
        CookbookModel model = new CookbookModel();
        model.setRecipes(catalog());
        model.setFavoritesFirst(false);
        assertEquals(CookbookModel.Column.FH, model.sortColumn());
        assertTrue(model.descending());

        model.sortBy(CookbookModel.Column.NAME);
        assertEquals(CookbookModel.Column.NAME, model.sortColumn());
        assertNull(model.sortAttr());
        assertFalse(model.descending());
        assertEquals(Arrays.asList("Apple Pie", "Beef Stew", "Mixed Grill", "Water", "Will Broth"),
                names(model.view()));

        model.sortBy(CookbookModel.Column.NAME);
        assertTrue(model.descending());
        assertEquals(Arrays.asList("Will Broth", "Water", "Mixed Grill", "Beef Stew", "Apple Pie"),
                names(model.view()));

        model.sortBy(CookbookModel.Column.ENERGY);
        assertTrue(model.descending());
        assertEquals(Arrays.asList("Beef Stew", "Mixed Grill", "Will Broth", "Apple Pie", "Water"),
                names(model.view()));

        model.sortBy(CookbookModel.Column.ENERGY);
        assertFalse(model.descending());
        assertEquals(Arrays.asList("Water", "Apple Pie", "Will Broth", "Mixed Grill", "Beef Stew"),
                names(model.view()));
    }

    @Test
    void sortByFepStatAndSetStatTier() {
        CookbookModel model = loaded();
        model.sortByFep(FepAttr.STR, 2);
        assertEquals(FepAttr.STR, model.sortAttr());
        assertEquals(2, model.sortTier());
        assertTrue(model.descending());
        assertNull(model.sortColumn());
        List<String> str2Desc = names(model.view());
        assertEquals("Beef Stew", str2Desc.get(0));
        assertEquals("Mixed Grill", str2Desc.get(1));

        model.sortByFep(FepAttr.STR, 2);
        assertFalse(model.descending());
        List<String> str2Asc = names(model.view());
        assertEquals("Beef Stew", str2Asc.get(str2Asc.size() - 1));
        assertEquals("Mixed Grill", str2Asc.get(str2Asc.size() - 2));

        model.setStatTier(1);
        assertEquals(1, model.statTier());
        assertEquals(1, model.sortTier());
        assertTrue(model.descending());
        assertEquals("Apple Pie", names(model.view()).get(0));

        model.sortByStat(FepAttr.WIL);
        assertEquals(FepAttr.WIL, model.sortAttr());
        assertEquals(1, model.sortTier());
        assertEquals("Will Broth", names(model.view()).get(0));
    }

    @Test
    void sortTiesBreakByNameIgnoreCaseThenHash() {
        Recipe zed = recipe("h-z", "Stew", 2.0, 40, ings("Salt"), feps("Strength +1", 10.0));
        Recipe aaa = recipe("h-m", "Broth", 2.0, 40, ings("Salt"), feps("Strength +1", 10.0));
        Recipe stewB = recipe("h-b", "Stew", 2.0, 40, ings("Salt"), feps("Strength +1", 10.0));
        Recipe stewA = recipe("h-a", "stew", 2.0, 40, ings("Salt"), feps("Strength +1", 10.0));
        CookbookModel model = new CookbookModel();
        model.setRecipes(Arrays.asList(zed, aaa, stewB, stewA));
        model.setFavoritesFirst(false);
        model.sortBy(CookbookModel.Column.ENERGY);
        assertTrue(model.descending());
        assertEquals(Arrays.asList("Broth", "stew", "Stew", "Stew"), names(model.view()));
        assertEquals(Arrays.asList("h-m", "h-a", "h-b", "h-z"), hashes(model.view()));

        model.sortBy(CookbookModel.Column.ENERGY);
        assertFalse(model.descending());
        assertEquals(Arrays.asList("Broth", "stew", "Stew", "Stew"), names(model.view()));
        assertEquals(Arrays.asList("h-m", "h-a", "h-b", "h-z"), hashes(model.view()));
    }

    @Test
    void favoritesFirstAndOnlyDoNotUseToggleForQueryVersion() {
        CookbookModel model = loaded();
        CookbookRow pie = row(model, "Apple Pie");
        int qv = model.queryVersion();
        int ver = model.version();
        model.toggleFavorite(pie);
        assertTrue(pie.recipe.isFavorite());
        assertEquals(qv, model.queryVersion());
        assertTrue(model.version() > ver);

        model.setFavoritesFirst(true);
        assertEquals("Apple Pie", names(model.view()).get(0));

        CookbookRow water = row(model, "Water");
        model.toggleFavorite(water);
        assertEquals(Arrays.asList("Apple Pie", "Water", "Beef Stew", "Mixed Grill", "Will Broth"),
                names(model.view()));

        int qvBeforeOnly = model.queryVersion();
        model.setFavoritesOnly(true);
        assertTrue(model.queryVersion() > qvBeforeOnly);
        assertEquals(5, model.total());
        assertEquals(Arrays.asList("Apple Pie", "Water"), names(model.view()));

        int qvBeforeChanged = model.queryVersion();
        int verBeforeChanged = model.version();
        pie.recipe.setFavorite(false);
        model.favoritesChanged();
        assertEquals(qvBeforeChanged, model.queryVersion());
        assertTrue(model.version() > verBeforeChanged);
        assertEquals(Collections.singletonList("Water"), names(model.view()));
    }

    @Test
    void queryVersionIncrementsOnSearchFilterSpiceSortRecipesAndClear() {
        CookbookModel model = loaded();
        int qv = model.queryVersion();

        model.setSearch("pie");
        qv = assertBumped(model, qv);
        model.setFepCodes(Collections.singleton(STR2));
        qv = assertBumped(model, qv);
        model.toggleFep(WIL1);
        qv = assertBumped(model, qv);
        model.setFepMode(CookbookModel.Mode.ALL);
        qv = assertBumped(model, qv);
        model.setIngredients(Collections.singleton("Pork"));
        qv = assertBumped(model, qv);
        model.toggleIngredient("Apple");
        qv = assertBumped(model, qv);
        model.setIngredientMode(CookbookModel.Mode.NONE);
        qv = assertBumped(model, qv);
        model.setFavoritesOnly(true);
        qv = assertBumped(model, qv);
        model.sortBy(CookbookModel.Column.NAME);
        qv = assertBumped(model, qv);
        model.setSpice(SpiceCalc.Spice.PEPPER, 10);
        qv = assertBumped(model, qv);
        model.setRecipes(catalog());
        qv = assertBumped(model, qv);
        model.clearFilters();
        assertBumped(model, qv);
    }

    @Test
    void setSpiceRebuildsRowsAndNonPositiveQualityRemovesSpice() {
        Recipe dish = recipe("h-sp", "Peppered", 4.0, 20, ings("Pork"), feps("Strength +2", 10.0));
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(dish));
        CookbookRow plain = model.view().get(0);
        assertEquals(10.0, plain.total, 1e-9);
        assertEquals(4.0, plain.hunger, 1e-9);
        assertTrue(plain.codes.contains(STR2));
        assertFalse(plain.codes.contains(WIL1));
        assertEquals(0.0, model.spice(SpiceCalc.Spice.PEPPER));
        int qv = model.queryVersion();

        model.setSpice(SpiceCalc.Spice.PEPPER, 10);
        assertTrue(model.queryVersion() > qv);
        CookbookRow peppered = model.view().get(0);
        assertEquals(15.0, peppered.total, 1e-9);
        assertEquals(6.0, peppered.hunger, 1e-9);
        assertEquals(10.0, model.spice(SpiceCalc.Spice.PEPPER));

        qv = model.queryVersion();
        model.setSpice(SpiceCalc.Spice.PEPPER, 10);
        assertEquals(qv, model.queryVersion());

        model.setSpice(SpiceCalc.Spice.PEPPER, 0);
        CookbookRow cleared = model.view().get(0);
        assertEquals(10.0, cleared.total, 1e-9);
        assertEquals(4.0, cleared.hunger, 1e-9);
        assertEquals(0.0, model.spice(SpiceCalc.Spice.PEPPER));
        assertTrue(model.activeSpices().isEmpty());

        model.setSpice(SpiceCalc.Spice.BLACK_TRUFFLE, 10);
        CookbookRow truffled = model.view().get(0);
        assertTrue(truffled.codes.contains(STR2));
        assertTrue(truffled.codes.contains(WIL1));
        assertTrue(truffled.total > 10.0);

        model.setSpice(SpiceCalc.Spice.BLACK_TRUFFLE, -3);
        CookbookRow unspiced = model.view().get(0);
        assertEquals(10.0, unspiced.total, 1e-9);
        assertFalse(unspiced.codes.contains(WIL1));
        assertEquals(0.0, model.spice(SpiceCalc.Spice.BLACK_TRUFFLE));
    }

    @Test
    void clearFiltersRestoresSearchFepIngredientsSpiceAndFavoritesOnly() {
        CookbookModel model = loaded();
        model.setSearch("pie");
        model.setFepCodes(Collections.singleton(STR2));
        model.setFepMode(CookbookModel.Mode.ALL);
        model.setIngredients(Collections.singleton("Pork"));
        model.setIngredientMode(CookbookModel.Mode.NONE);
        model.setFavoritesOnly(true);
        model.setSpice(SpiceCalc.Spice.PEPPER, 8);
        CookbookModel.Column sort = model.sortColumn();
        boolean desc = model.descending();

        model.clearFilters();
        assertEquals("", model.search());
        assertTrue(model.fepCodes().isEmpty());
        assertEquals(CookbookModel.Mode.ANY, model.fepMode());
        assertTrue(model.ingredients().isEmpty());
        assertEquals(CookbookModel.Mode.ANY, model.ingredientMode());
        assertFalse(model.favoritesOnly());
        assertTrue(model.activeSpices().isEmpty());
        assertEquals(sort, model.sortColumn());
        assertEquals(desc, model.descending());
        assertEquals(names(catalog()), names(model.view()));
    }

    private static int assertBumped(CookbookModel model, int previous) {
        assertTrue(model.queryVersion() > previous);
        return model.queryVersion();
    }

    private static CookbookModel loaded() {
        CookbookModel model = new CookbookModel();
        model.setRecipes(catalog());
        model.setFavoritesFirst(false);
        model.sortBy(CookbookModel.Column.NAME);
        return model;
    }

    private static List<Recipe> catalog() {
        return Arrays.asList(
                recipe("h-pie", "Apple Pie", 2.0, 10, ings("Apple", "Flour"), feps("Strength +1", 4.0)),
                recipe("h-stew", "Beef Stew", 2.0, 80, ings("Pork", "Salt"), feps("Strength +2", 10.0)),
                recipe("h-mix", "Mixed Grill", 2.0, 50, ings("Pork", "Apple"),
                        feps("Strength +2", 6.0, "Will +1", 4.0)),
                recipe("h-water", "Water", 1.0, 1, ings("Water"), Collections.<String, Recipe.Fep>emptyMap()),
                recipe("h-broth", "Will Broth", 2.0, 30, ings("Pork", "Water"), feps("Will +1", 8.0))
        );
    }

    private static CookbookRow row(CookbookModel model, String name) {
        for(CookbookRow r : model.view()) {
            if(name.equals(r.name()))
                return r;
        }
        fail("missing row " + name);
        return null;
    }

    private static List<String> names(List<?> rows) {
        List<String> out = new ArrayList<String>();
        if(!rows.isEmpty() && rows.get(0) instanceof Recipe) {
            for(Object o : rows)
                out.add(((Recipe)o).getName());
            return out;
        }
        for(Object o : rows)
            out.add(((CookbookRow)o).name());
        return out;
    }

    private static List<String> hashes(List<CookbookRow> rows) {
        List<String> out = new ArrayList<String>();
        for(CookbookRow r : rows)
            out.add(r.recipe.getHash());
        return out;
    }

    private static Recipe recipe(String hash, String name, double hunger, int energy,
                                 Map<String, Recipe.IngredientInfo> ingredients,
                                 Map<String, Recipe.Fep> feps) {
        return new Recipe(hash, name, "gfx/invobjs/food", hunger, energy, ingredients, feps);
    }

    private static Map<String, Recipe.IngredientInfo> ings(String... names) {
        Map<String, Recipe.IngredientInfo> m = new LinkedHashMap<String, Recipe.IngredientInfo>();
        for(String n : names)
            m.put(n, new Recipe.IngredientInfo(1.0));
        return m;
    }

    private static Map<String, Recipe.Fep> feps(Object... pairs) {
        Map<String, Recipe.Fep> m = new LinkedHashMap<String, Recipe.Fep>();
        for(int i = 0; i < pairs.length; i += 2)
            m.put((String)pairs[i], new Recipe.Fep(((Number)pairs[i + 1]).doubleValue(), 1.0));
        return m;
    }
}
