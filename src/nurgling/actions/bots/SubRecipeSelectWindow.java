package nurgling.actions.bots;

import haven.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modal dialog for selecting which recipe to use when sub-crafting an ingredient.
 * Shown when multiple recipes can produce the same item.
 */
public class SubRecipeSelectWindow extends Window {

    private final Consumer<MenuGrid.Pagina> onSelect;
    private final Runnable onCancel;

    public SubRecipeSelectWindow(String itemName, List<MenuGrid.Pagina> recipes,
                                  Consumer<MenuGrid.Pagina> onSelect, Runnable onCancel) {
        super(UI.scale(new Coord(280, 40 + recipes.size() * 28)), "Sub-craft: " + itemName);
        this.onSelect = onSelect;
        this.onCancel = onCancel;
        buildUI(recipes);
    }

    private void buildUI(List<MenuGrid.Pagina> recipes) {
        int y = 5;
        add(new Label("Select recipe to craft:"), UI.scale(10, y));
        y += 22;

        for (MenuGrid.Pagina pag : recipes) {
            String name;
            try {
                name = pag.button().name();
            } catch (Loading e) {
                name = "...";
            }
            final MenuGrid.Pagina selected = pag;
            add(new Button(UI.scale(250), name) {
                @Override
                public void click() {
                    onSelect.accept(selected);
                    SubRecipeSelectWindow.this.reqdestroy();
                }
            }, UI.scale(10, y));
            y += 28;
        }

        pack();
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if ("close".equals(msg)) {
            onCancel.run();
            reqdestroy();
            return;
        }
        super.wdgmsg(msg, args);
    }
}
