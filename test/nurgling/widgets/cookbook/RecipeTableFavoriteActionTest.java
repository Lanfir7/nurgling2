package nurgling.widgets.cookbook;

import nurgling.cookbook.CookbookModel;
import nurgling.cookbook.CookbookRow;
import nurgling.cookbook.Recipe;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTableFavoriteActionTest {
    @Test
    void starToggleGoesThroughActionsThenGuardedModelPersist() {
        Recipe recipe = recipe("h1");
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        AtomicInteger persisted = new AtomicInteger();
        model.setFavoritePort((hash, desired) -> {
            assertEquals("h1", hash);
            assertEquals(Boolean.TRUE, Boolean.valueOf(desired));
            persisted.incrementAndGet();
        });
        CookbookRow row = model.view().get(0);
        AtomicInteger actions = new AtomicInteger();

        RecipeTable.starToggle(recording(r -> {
            actions.incrementAndGet();
            assertSame(row, r);
            model.toggleFavorite(r);
        }), row);

        assertEquals(1, actions.get());
        assertEquals(1, persisted.get());
        assertTrue(row.recipe.isFavorite());
    }

    @Test
    void starToggleDoesNotTouchModelWhenActionsGuardRejects() {
        Recipe recipe = recipe("h2");
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        AtomicInteger persisted = new AtomicInteger();
        model.setFavoritePort((hash, desired) -> persisted.incrementAndGet());
        CookbookRow row = model.view().get(0);

        RecipeTable.starToggle(recording(r -> {
            /* NCookBook.toggleFavorite returns when !dbReady() */
        }), row);

        assertFalse(row.recipe.isFavorite());
        assertEquals(0, persisted.get());
    }

    @Test
    void starToggleIgnoresNullActions() {
        Recipe recipe = recipe("h3");
        CookbookRow row = new CookbookRow(recipe, null);
        RecipeTable.starToggle(null, row);
        assertFalse(recipe.isFavorite());
    }

    private static Recipe recipe(String hash) {
        return new Recipe(hash, "Pie", "gfx/invobjs/food", 1.0, 50,
                Collections.<String, Recipe.IngredientInfo>emptyMap(),
                Collections.<String, Recipe.Fep>emptyMap());
    }

    private static RecipeTable.Actions recording(final Consumer<CookbookRow> onToggle) {
        return new RecipeTable.Actions() {
            @Override
            public void craft(CookbookRow row) {
            }

            @Override
            public void toggleFavorite(CookbookRow row) {
                onToggle.accept(row);
            }

            @Override
            public void rowMenu(CookbookRow row, haven.Widget at) {
            }

            @Override
            public void clearFilters() {
            }
        };
    }
}
