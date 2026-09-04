package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Bounded popup shown when one ingredient has multiple producer recipes. */
public class CraftAtlasRecipeChooser extends Widget {
    private final CraftAtlasController controller;
    private List<CraftAtlasEntry> choices = new ArrayList<>();
    private int selected;
    private final int rowHeight = UI.scale(30);

    public CraftAtlasRecipeChooser(Coord size, CraftAtlasController controller) {
        super(size); this.controller = controller; setcanfocus(true);
    }

    public void setChoices(List<CraftAtlasEntry> values) { choices = new ArrayList<>(values); selected = 0; visible = !choices.isEmpty(); }
    public void close() { choices.clear(); hide(); }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(10, 13, 16, 245)); g.frect(Coord.z, sz); g.chcolor();
        g.text(L10n.get("craft_atlas.choice"), UI.scale(12, 22));
        int max = Math.min(choices.size(), Math.max(1, (sz.y - UI.scale(34)) / rowHeight));
        for(int i = 0; i < max; i++) {
            int y = UI.scale(34) + i * rowHeight;
            if(i == selected) { g.chcolor(new Color(52, 86, 94, 220)); g.frect(Coord.of(UI.scale(6), y), Coord.of(sz.x - UI.scale(12), rowHeight - 1)); g.chcolor(); }
            g.text(choices.get(i).displayName, Coord.of(UI.scale(12), y + UI.scale(20)));
        }
        super.draw(g);
    }

    private void choose(int index) {
        if(index >= 0 && index < choices.size()) { controller.chooseProducer(choices.get(index).recipeResource); close(); }
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 1) { choose((ev.c.y - UI.scale(34)) / rowHeight); return true; }
        return super.mousedown(ev);
    }

    @Override public boolean keydown(KeyDownEvent ev) {
        if(ev.code == KeyEvent.VK_ESCAPE) { close(); return true; }
        if(ev.code == KeyEvent.VK_UP) { selected = Math.max(0, selected - 1); return true; }
        if(ev.code == KeyEvent.VK_DOWN) { selected = Math.min(choices.size() - 1, selected + 1); return true; }
        if(ev.code == KeyEvent.VK_ENTER) { choose(selected); return true; }
        return super.keydown(ev);
    }
}
