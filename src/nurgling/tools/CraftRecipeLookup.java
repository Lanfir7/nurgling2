package nurgling.tools;

import haven.*;
import nurgling.NUtils;

import java.util.*;

/**
 * Opens a recipe list for an item name: recipes that consume it and recipes that produce it.
 */
public class CraftRecipeLookup {

    public static boolean show(Widget origin, Coord click, Collection<String> names) {
        return show(origin, click, names, null);
    }

    public static boolean show(Widget origin, Coord click, Collection<String> names, Collection<String> resourcePaths) {
        if(origin == null || names == null || names.isEmpty())
            return false;
        RecipeIngredientCache.loadFromDatabase();
        List<String> keys = new ArrayList<>();
        for(String name : names) {
            if(name != null && !name.isEmpty() && !keys.contains(name))
                keys.add(name);
        }
        if(keys.isEmpty())
            return false;

        Set<RecipeIngredientCache.RecipeEntry> recipes = RecipeIngredientCache.findInputRecipes(keys, resourcePaths);
        recipes.addAll(RecipeIngredientCache.findOutputRecipes(keys));
        if(recipes.isEmpty())
            return false;

        GameUI gui = origin.getparent(GameUI.class);
        if(gui == null || gui.menu == null)
            return false;

        List<MenuGrid.Pagina> paginae = new ArrayList<>();
        for(RecipeIngredientCache.RecipeEntry entry : recipes) {
            MenuGrid.Pagina pag = paginaIfReady(gui, entry.paginaResource);
            if(pag != null && !paginae.contains(pag))
                paginae.add(pag);
        }
        if(paginae.isEmpty())
            return false;

        SListMenu.of(UI.scale(250, 120), paginae,
                     pag -> pag.button().name(),
                     pag -> pag.button().img(),
                     pag -> pag.button().use(new MenuGrid.Interaction(1, origin.ui.modflags())))
            .addat(origin, click.add(UI.scale(5, 5)));
        return true;
    }

    public static boolean show(Widget origin, Coord click, String name) {
        if(name == null)
            return false;
        return show(origin, click, Collections.singletonList(name));
    }

    /** Item name plus cached craft inputs, or just the name if the recipe is unknown. */
    public static String ingredientTooltip(String itemName) {
        if(itemName == null || itemName.isEmpty())
            return "";
        RecipeIngredientCache.RecipeEntry recipe = pickOutputRecipe(itemName);
        if(recipe == null)
            return itemName;
        List<RecipeIngredientCache.IngredientSpec> specs = RecipeIngredientCache.getRecipeSpecs(recipe.paginaResource);
        if(specs.isEmpty())
            return itemName;
        String title = (recipe.recipeName != null && !recipe.recipeName.isEmpty())
            ? recipe.recipeName : itemName;
        StringBuilder sb = new StringBuilder(title);
        for(RecipeIngredientCache.IngredientSpec spec : specs) {
            if(spec == null || spec.name == null || spec.name.isEmpty())
                continue;
            sb.append("\n- ").append(spec.name);
            if(spec.count > 1)
                sb.append(" x").append(spec.count);
        }
        return sb.toString();
    }

    private static RecipeIngredientCache.RecipeEntry pickOutputRecipe(String itemName) {
        Set<RecipeIngredientCache.RecipeEntry> recipes = RecipeIngredientCache.findOutputRecipesForItem(itemName);
        RecipeIngredientCache.RecipeEntry chosen = null;
        for(RecipeIngredientCache.RecipeEntry e : recipes) {
            if(itemName.equals(e.recipeName))
                return e;
            if(chosen == null || (e.paginaResource != null && chosen.paginaResource != null
                    && e.paginaResource.compareTo(chosen.paginaResource) < 0))
                chosen = e;
        }
        return chosen;
    }

    /** Open the craft recipe that produces this item; no-op if none are known. */
    public static boolean showProducing(Widget origin, Coord click, String itemName) {
        if(origin == null || itemName == null || itemName.isEmpty())
            return false;
        RecipeIngredientCache.loadFromDatabase();
        Set<RecipeIngredientCache.RecipeEntry> recipes = RecipeIngredientCache.findOutputRecipesForItem(itemName);
        if(recipes.isEmpty())
            return false;

        GameUI gui = origin.getparent(GameUI.class);
        if(gui == null || gui.menu == null)
            return false;

        List<MenuGrid.Pagina> paginae = new ArrayList<>();
        for(RecipeIngredientCache.RecipeEntry entry : recipes) {
            MenuGrid.Pagina pag = paginaIfReady(gui, entry.paginaResource);
            if(pag != null && !paginae.contains(pag))
                paginae.add(pag);
        }
        if(paginae.isEmpty())
            return false;
        if(paginae.size() == 1) {
            try {
                paginae.get(0).button().use(new MenuGrid.Interaction(1, origin.ui.modflags()));
            } catch(Loading l) {
                return false;
            } catch(Resource.BadResourceException e) {
                return false;
            }
            return true;
        }
        SListMenu.of(UI.scale(250, 120), paginae,
                     pag -> pag.button().name(),
                     pag -> pag.button().img(),
                     pag -> pag.button().use(new MenuGrid.Interaction(1, origin.ui.modflags())))
            .addat(origin, click.add(UI.scale(5, 5)));
        return true;
    }

    static MenuGrid.Pagina paginaIfReady(GameUI gui, String paginaResource) {
        if(gui == null || gui.menu == null || paginaResource == null || paginaResource.isEmpty())
            return null;
        try {
            MenuGrid.Pagina pag = gui.menu.paginafor(Resource.remote().load(paginaResource));
            if(pag == null)
                return null;
            pag.button();
            return pag;
        } catch(Loading l) {
            return null;
        } catch(Resource.BadResourceException e) {
            return null;
        }
    }
}
