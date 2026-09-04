package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.Tex;
import haven.Text;
import haven.UI;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.i18n.L10n;

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
    private final CraftAtlasIconCache icons = new CraftAtlasIconCache();
    private long revision = Long.MIN_VALUE;
    private int scroll;
    private String selected;
    private final int rowHeight = UI.scale(54);

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
            boolean active = entry.recipeResource.equals(selected);
            if(active) { g.chcolor(new Color(55, 91, 98, 220)); g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1)); g.chcolor(); }
            else if((i & 1) == 1) { g.chcolor(new Color(28, 33, 38, 150)); g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1)); g.chcolor(); }
            if(active) { g.chcolor(new Color(214, 165, 75, 255)); g.frect(Coord.of(0, y), Coord.of(UI.scale(3), rowHeight - 1)); g.chcolor(); }
            int iconBox = UI.scale(38);
            Coord iconAt = Coord.of(UI.scale(8), y + (rowHeight - iconBox) / 2);
            g.chcolor(new Color(9, 13, 16, 205)); g.frect(iconAt, Coord.of(iconBox, iconBox)); g.chcolor();
            Tex icon = icons.recipe(entry.outputResource, entry.recipeResource, entry.displayName);
            CraftAtlasIconCache.draw(g, icon, iconAt.add(UI.scale(2), UI.scale(2)), iconBox - UI.scale(4));
            Tex label = labels.get(entry.recipeResource);
            if(label == null) { label = Text.render(entry.displayName).tex(); labels.put(entry.recipeResource, label); }
            g.image(label, Coord.of(UI.scale(56), y + UI.scale(11)));
            String category = entry.categories.contains("equipment") ? L10n.get("craft_atlas.section.equipment") :
                    entry.categories.contains("gildings") ? L10n.get("craft_atlas.section.gildings") :
                            entry.categories.contains("foods") ? L10n.get("craft_atlas.section.foods") : "";
            String statusText = L10n.get(CraftAtlasDetails.statusKey(entry.availability));
            g.chcolor(new Color(174, 181, 181, 220));
            g.atext(category.isEmpty() ? statusText : category + "  \u00b7  " + statusText,
                    Coord.of(UI.scale(56), y + UI.scale(39)), 0, 0.5);
            g.chcolor();
            Color statusColor = entry.availability == CraftAtlasEntry.Availability.OPEN ? new Color(103, 201, 129) :
                    entry.availability == CraftAtlasEntry.Availability.REFERENCE_ONLY ? new Color(186, 153, 88) : new Color(135, 143, 145);
            g.chcolor(statusColor); g.fellipse(Coord.of(sz.x - UI.scale(13), y + rowHeight / 2), UI.scale(3, 3)); g.chcolor();
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
    @Override public void dispose() { clearTextures(); icons.dispose(); super.dispose(); }
}
