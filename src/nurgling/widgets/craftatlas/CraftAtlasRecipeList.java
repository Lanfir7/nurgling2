package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.Tex;
import haven.Text;
import haven.UI;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Virtualized recipe list; it only paints and caches visible rows. */
public class CraftAtlasRecipeList extends Widget {
    private final CraftAtlasController controller;
    private List<CraftAtlasEntry> entries = new ArrayList<>();
    private final Map<String, Tex> labels = new HashMap<>();
    private long revision = Long.MIN_VALUE;
    private int scroll;
    private String selected;
    private final int rowHeight = UI.scale(38);

    public CraftAtlasRecipeList(Coord size, CraftAtlasController controller) { super(size); this.controller = controller; }

    public void setState(CraftAtlasController.ViewState state) {
        if(state.snapshot.revision != revision) { clearTextures(); revision = state.snapshot.revision; }
        entries = state.results;
        selected = state.selected == null ? null : state.selected.recipeResource;
        clampScroll();
    }

    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() * rowHeight - sz.y))); }

    @Override public void resize(Coord size) { super.resize(size); clampScroll(); }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(19, 23, 27, 235)); g.frect(Coord.z, sz); g.chcolor();
        int[] range = CraftAtlasLayout.visibleRows(scroll, sz.y, rowHeight, entries.size());
        for(int i = range[0]; i <= range[1]; i++) {
            CraftAtlasEntry entry = entries.get(i);
            int y = i * rowHeight - scroll;
            if(entry.recipeResource.equals(selected)) { g.chcolor(new Color(55, 91, 98, 220)); g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1)); g.chcolor(); }
            else if((i & 1) == 1) { g.chcolor(new Color(28, 33, 38, 150)); g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1)); g.chcolor(); }
            Tex label = labels.get(entry.recipeResource);
            if(label == null) { label = Text.render(entry.displayName).tex(); labels.put(entry.recipeResource, label); }
            g.image(label, Coord.of(UI.scale(10), y + (rowHeight - label.sz().y) / 2));
            String status = entry.availability == CraftAtlasEntry.Availability.OPEN ? "●" : "○";
            g.text(status, Coord.of(sz.x - UI.scale(18), y + UI.scale(22)));
        }
        super.draw(g);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 1) {
            int index = (ev.c.y + scroll) / rowHeight;
            if(index >= 0 && index < entries.size()) { controller.select(entries.get(index).recipeResource); return true; }
        }
        return super.mousedown(ev);
    }

    @Override public boolean mousewheel(MouseWheelEvent ev) {
        scroll += ev.a * rowHeight * 2;
        clampScroll();
        return true;
    }

    private void clearTextures() { for(Tex value : labels.values()) value.dispose(); labels.clear(); }
    @Override public void dispose() { clearTextures(); super.dispose(); }
}
