package nurgling.tools;

import haven.*;
import nurgling.NUtils;

import java.util.*;

/**
 * Opens a recipe list for an item name: recipes that consume it and recipes that produce it.
 */
public class CraftRecipeLookup {

    public static boolean show(Widget origin, Coord click, Collection<String> names) {
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

        Set<RecipeIngredientCache.RecipeEntry> recipes = RecipeIngredientCache.findInputRecipes(keys);
        recipes.addAll(RecipeIngredientCache.findOutputRecipes(keys));
        if(recipes.isEmpty())
            return false;

        GameUI gui = origin.getparent(GameUI.class);
        if(gui == null || gui.menu == null)
            return false;

        List<MenuGrid.Pagina> paginae = new ArrayList<>();
        for(RecipeIngredientCache.RecipeEntry entry : recipes) {
            try {
                MenuGrid.Pagina pag = gui.menu.paginafor(Resource.remote().load(entry.paginaResource));
                if(pag != null && !paginae.contains(pag))
                    paginae.add(pag);
            } catch(Loading l) {
                // Resource not ready; skip this entry
            }
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
}
