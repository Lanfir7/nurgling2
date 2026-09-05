package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Small overlay shown below the Atlas search field. */
final class CraftAtlasSearchHistory extends Widget {
    private static final int MAX_VISIBLE = 6;
    private final Consumer<String> selected;
    private List<String> items = Collections.emptyList();
    private final int headerHeight = UI.scale(26);
    private final int rowHeight = UI.scale(27);

    CraftAtlasSearchHistory(int width, Consumer<String> selected) {
        super(Coord.of(width, UI.scale(26)));
        this.selected = selected;
    }

    void setItems(List<String> value) {
        items = Collections.unmodifiableList(new ArrayList<>(value == null ? Collections.emptyList() : value));
        resize(Coord.of(sz.x, headerHeight + Math.min(MAX_VISIBLE, items.size()) * rowHeight));
        if(items.isEmpty()) hide();
    }

    void setWidth(int width) {
        resize(Coord.of(Math.max(1, width), headerHeight + Math.min(MAX_VISIBLE, items.size()) * rowHeight));
    }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(18, 24, 27, 250));
        g.frect(Coord.z, sz);
        g.chcolor(new Color(203, 157, 68, 230));
        g.frect(Coord.z, Coord.of(sz.x, UI.scale(1)));
        g.chcolor(new Color(181, 187, 184));
        g.atext(L10n.get("craft_atlas.search_history"), Coord.of(UI.scale(10), headerHeight / 2), 0, 0.5);
        for(int i = 0; i < Math.min(MAX_VISIBLE, items.size()); i++) {
            int y = headerHeight + i * rowHeight;
            if((i & 1) == 1) {
                g.chcolor(new Color(31, 38, 41, 220));
                g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight));
            }
            g.chcolor(new Color(224, 226, 221));
            g.atext(items.get(i), Coord.of(UI.scale(12), y + rowHeight / 2), 0, 0.5);
        }
        g.chcolor();
        super.draw(g);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 1 && ev.c.y >= headerHeight) {
            int index = (ev.c.y - headerHeight) / rowHeight;
            if(index >= 0 && index < Math.min(MAX_VISIBLE, items.size())) {
                selected.accept(items.get(index));
                return true;
            }
        }
        return super.mousedown(ev);
    }
}
