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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Virtualized, sortable recipe table; it only paints and caches visible rows. */
public class CraftAtlasRecipeList extends Widget {
    private final CraftAtlasController controller;
    private List<CraftAtlasEntry> sourceEntries = new ArrayList<>();
    private List<CraftAtlasEntry> entries = new ArrayList<>();
    private List<CraftAtlasListTable.Column> columns = Collections.emptyList();
    private final Map<String, Tex> labels = new HashMap<>();
    private final CraftAtlasIconCache icons = new CraftAtlasIconCache();
    private long revision = Long.MIN_VALUE;
    private int scroll;
    private String selected;
    private String section = "all";
    private String sortId;
    private boolean descending;
    private int columnOffset;
    private boolean preserveSourceOrder;
    private final int rowHeight = UI.scale(54);

    public CraftAtlasRecipeList(Coord size, CraftAtlasController controller) {
        super(size);
        this.controller = controller;
    }

    public void setSection(String value) {
        String next = value == null ? "all" : value;
        if(next.equals(section)) return;
        section = next;
        sortId = !preserveSourceOrder && "curiosities".equals(section) ? "curiosity:lp-hour" : null;
        descending = sortId != null;
        columnOffset = 0;
        scroll = 0;
        rebuildTable();
    }

    public void setPreserveSourceOrder(boolean value) {
        if(preserveSourceOrder == value) return;
        preserveSourceOrder = value;
        sortId = !value && "curiosities".equals(section) ? "curiosity:lp-hour" : null;
        descending = sortId != null;
        rebuildTable();
    }

    public void setState(CraftAtlasController.ViewState state) {
        if(state.snapshot.revision != revision) { clearTextures(); revision = state.snapshot.revision; }
        sourceEntries = new ArrayList<>(state.results);
        selected = state.selected == null ? null : state.selected.recipeResource;
        rebuildTable();
    }

    private void rebuildTable() {
        columns = CraftAtlasListTable.columnsFor(section, sourceEntries);
        CraftAtlasListTable.Column sortColumn = column(sortId);
        if(sortId != null && !"name".equals(sortId) && sortColumn == null) sortId = null;
        if(preserveSourceOrder) {
            entries = new ArrayList<>(sourceEntries);
        } else if("name".equals(sortId)) {
            entries = new ArrayList<>(sourceEntries);
            Comparator<CraftAtlasEntry> order = Comparator.comparing(
                    entry -> entry.displayName, String.CASE_INSENSITIVE_ORDER);
            entries.sort(descending ? order.reversed() : order);
        } else if(sortColumn != null) {
            entries = CraftAtlasListTable.sort(sourceEntries, sortColumn, descending);
        } else {
            entries = new ArrayList<>(sourceEntries);
        }
        clampColumnOffset();
        clampScroll();
    }

    private CraftAtlasListTable.Column column(String id) {
        if(id != null) for(CraftAtlasListTable.Column column : columns)
            if(id.equals(column.id)) return column;
        return null;
    }

    private boolean tableVisible() { return CraftAtlasSections.hasMetricTable(section); }
    private int headerHeight() { return tableVisible() ? UI.scale(34) : 0; }
    private int viewportHeight() { return Math.max(0, sz.y - headerHeight()); }
    private int nameWidth() { return Math.min(UI.scale(230), Math.max(UI.scale(170), sz.x / 2)); }
    private int columnWidth() { return UI.scale("curiosities".equals(section) ? 96 : 42); }
    private int navigationWidth() { return UI.scale(22); }

    private boolean pagedColumns() {
        return columns.size() * columnWidth() > Math.max(0, sz.x - nameWidth());
    }

    private int visibleColumnCount() {
        int width = Math.max(0, sz.x - nameWidth() - (pagedColumns() ? navigationWidth() * 2 : 0));
        return Math.max(1, width / Math.max(1, columnWidth()));
    }

    private int columnsX() { return nameWidth() + (pagedColumns() ? navigationWidth() : 0); }
    private int maxColumnOffset() { return Math.max(0, columns.size() - visibleColumnCount()); }
    private void clampColumnOffset() { columnOffset = Math.max(0, Math.min(columnOffset, maxColumnOffset())); }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, entries.size() * rowHeight - viewportHeight())));
    }

    @Override public void resize(Coord size) {
        super.resize(size);
        clampColumnOffset();
        clampScroll();
    }

    @Override public void draw(GOut g) {
        g.chcolor(new Color(19, 23, 27, 235));
        g.frect(Coord.z, sz);
        g.chcolor();
        if(tableVisible()) drawHeader(g);
        int[] range = CraftAtlasLayout.visibleRows(scroll, viewportHeight(), rowHeight, entries.size());
        for(int i = range[0]; i <= range[1]; i++) drawRow(g, entries.get(i), i,
                headerHeight() + i * rowHeight - scroll);
        super.draw(g);
    }

    private void drawHeader(GOut g) {
        int height = headerHeight();
        g.chcolor(new Color(31, 38, 42, 252));
        g.frect(Coord.z, Coord.of(sz.x, height));
        g.chcolor(new Color(205, 159, 70, 210));
        g.frect(Coord.of(0, height - 1), Coord.of(sz.x, 1));
        g.chcolor();
        drawHeaderText(g, L10n.get("craft_atlas.table.name") + sortArrow("name"),
                UI.scale(12), nameWidth() - UI.scale(18), height);

        if(pagedColumns()) {
            drawHeaderText(g, columnOffset > 0 ? "‹" : "", nameWidth(), navigationWidth(), height);
            drawHeaderText(g, columnOffset < maxColumnOffset() ? "›" : "",
                    sz.x - navigationWidth(), navigationWidth(), height);
        }
        int count = Math.min(visibleColumnCount(), Math.max(0, columns.size() - columnOffset));
        for(int visible = 0; visible < count; visible++) {
            CraftAtlasListTable.Column column = columns.get(columnOffset + visible);
            int x = columnsX() + visible * columnWidth();
            if(x >= sz.x - (pagedColumns() ? navigationWidth() : 0)) break;
            g.chcolor(new Color(74, 83, 87, 150));
            g.frect(Coord.of(x, 0), Coord.of(1, height));
            g.chcolor();
            Tex icon = column.iconResource == null ? null : icons.icon(column.iconResource, column.tooltip);
            if(icon == null) {
                drawHeaderText(g, column.label + sortArrow(column.id), x, columnWidth(), height);
            } else {
                int iconBox = UI.scale(22);
                CraftAtlasIconCache.draw(g, icon,
                        Coord.of(x + (columnWidth() - iconBox) / 2, (height - iconBox) / 2), iconBox);
                String arrow = sortArrow(column.id).trim();
                if(!arrow.isEmpty())
                    drawHeaderText(g, arrow, x + columnWidth() - UI.scale(18), UI.scale(16), height);
            }
        }
    }

    private void drawHeaderText(GOut g, String text, int x, int width, int height) {
        g.chcolor(new Color(220, 225, 222));
        g.atext(text, Coord.of(x + width / 2, height / 2), 0.5, 0.5);
        g.chcolor();
    }

    private String sortArrow(String id) {
        return id.equals(sortId) ? (descending ? "  ↓" : "  ↑") : "";
    }

    private void drawRow(GOut g, CraftAtlasEntry entry, int index, int y) {
        boolean active = entry.recipeResource.equals(selected);
        if(active) {
            g.chcolor(new Color(55, 91, 98, 220));
            g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1));
            g.chcolor();
        } else if((index & 1) == 1) {
            g.chcolor(new Color(28, 33, 38, 150));
            g.frect(Coord.of(0, y), Coord.of(sz.x, rowHeight - 1));
            g.chcolor();
        }
        if(active) {
            g.chcolor(new Color(214, 165, 75, 255));
            g.frect(Coord.of(0, y), Coord.of(UI.scale(3), rowHeight - 1));
            g.chcolor();
        }

        int fixedWidth = tableVisible() ? nameWidth() : sz.x;
        GOut name = g.reclip(Coord.of(0, y), Coord.of(fixedWidth, rowHeight));
        int iconBox = UI.scale(38);
        Coord iconAt = Coord.of(UI.scale(8), (rowHeight - iconBox) / 2);
        name.chcolor(new Color(9, 13, 16, 205));
        name.frect(iconAt, Coord.of(iconBox, iconBox));
        name.chcolor();
        Tex icon = icons.recipe(entry.outputResource, entry.recipeResource, entry.displayName);
        CraftAtlasIconCache.draw(name, icon, iconAt.add(UI.scale(2), UI.scale(2)), iconBox - UI.scale(4));
        Tex label = labels.get(entry.recipeResource);
        if(label == null) { label = Text.render(entry.displayName).tex(); labels.put(entry.recipeResource, label); }
        name.image(label, Coord.of(UI.scale(56), UI.scale(11)));
        String category = category(entry);
        String statusText = L10n.get(CraftAtlasDetails.statusKey(entry.availability));
        name.chcolor(new Color(174, 181, 181, 220));
        name.atext(category.isEmpty() ? statusText : category + "  ·  " + statusText,
                Coord.of(UI.scale(56), UI.scale(39)), 0, 0.5);
        name.chcolor();
        Color statusColor = entry.availability == CraftAtlasEntry.Availability.OPEN ? new Color(103, 201, 129) :
                entry.availability == CraftAtlasEntry.Availability.REFERENCE_ONLY ? new Color(186, 153, 88) :
                        new Color(135, 143, 145);
        name.chcolor(statusColor);
        name.fellipse(Coord.of(fixedWidth - UI.scale(12), rowHeight / 2), UI.scale(3, 3));
        name.chcolor();

        if(!tableVisible()) return;
        int count = Math.min(visibleColumnCount(), Math.max(0, columns.size() - columnOffset));
        for(int visible = 0; visible < count; visible++) {
            CraftAtlasListTable.Column column = columns.get(columnOffset + visible);
            int x = columnsX() + visible * columnWidth();
            if(x >= sz.x - (pagedColumns() ? navigationWidth() : 0)) break;
            if((visible & 1) == 1) {
                g.chcolor(new Color(8, 12, 14, 42));
                g.frect(Coord.of(x, y), Coord.of(columnWidth(), rowHeight - 1));
                g.chcolor();
            }
            g.chcolor(new Color(71, 80, 83, 115));
            g.frect(Coord.of(x, y), Coord.of(1, rowHeight - 1));
            double value = column.value(entry);
            g.chcolor(Double.isFinite(value) ? new Color(132, 220, 159) : new Color(113, 121, 124));
            g.atext(formatMetric(value), Coord.of(x + columnWidth() / 2, y + rowHeight / 2), 0.5, 0.5);
            g.chcolor();
        }
    }

    private String category(CraftAtlasEntry entry) {
        if(entry.categories.contains("equipment")) return L10n.get("craft_atlas.section.equipment");
        if(entry.categories.contains("gildings")) return L10n.get("craft_atlas.section.gildings");
        if(entry.categories.contains("foods")) return L10n.get("craft_atlas.section.foods");
        if(entry.categories.contains("curiosities")) return L10n.get("craft_atlas.section.curiosities");
        return "";
    }

    private static String formatMetric(double value) {
        if(!Double.isFinite(value)) return "—";
        double absolute = Math.abs(value);
        if(absolute >= 10000) return String.format(Locale.ROOT, "%.0fk", value / 1000.0);
        if(value == Math.rint(value)) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, absolute >= 100 ? "%.0f" : "%.1f", value);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 1 && tableVisible() && ev.c.y < headerHeight()) {
            headerClick(ev.c.x);
            return true;
        }
        if(ev.b == 1) {
            int contentY = ev.c.y - headerHeight();
            if(contentY < 0) return true;
            int index = (contentY + scroll) / rowHeight;
            if(index >= 0 && index < entries.size()) {
                controller.select(entries.get(index).recipeResource);
                return true;
            }
        }
        return super.mousedown(ev);
    }

    private void headerClick(int x) {
        if(x < nameWidth()) {
            if(!preserveSourceOrder) changeSort("name", false);
            return;
        }
        if(pagedColumns()) {
            if(x < nameWidth() + navigationWidth()) {
                columnOffset = Math.max(0, columnOffset - visibleColumnCount());
                return;
            }
            if(x >= sz.x - navigationWidth()) {
                columnOffset = Math.min(maxColumnOffset(), columnOffset + visibleColumnCount());
                return;
            }
        }
        if(preserveSourceOrder) return;
        int visible = (x - columnsX()) / columnWidth();
        int index = columnOffset + visible;
        if(visible >= 0 && index >= 0 && index < columns.size()) changeSort(columns.get(index).id, true);
    }

    private void changeSort(String id, boolean firstDescending) {
        if(id.equals(sortId)) descending = !descending;
        else { sortId = id; descending = firstDescending; }
        rebuildTable();
    }

    @Override public Object tooltip(Coord c, Widget prev) {
        if(tableVisible() && c.y < headerHeight()) {
            if(c.x < nameWidth()) return L10n.get("craft_atlas.table.name");
            if(pagedColumns() && (c.x < nameWidth() + navigationWidth() || c.x >= sz.x - navigationWidth()))
                return L10n.get("craft_atlas.table.more_columns");
            int index = columnOffset + (c.x - columnsX()) / columnWidth();
            if(index >= 0 && index < columns.size()) return columns.get(index).tooltip;
        }
        return super.tooltip(c, prev);
    }

    @Override public boolean mousewheel(MouseWheelEvent ev) {
        scroll += ev.a * rowHeight * 2;
        clampScroll();
        return true;
    }

    private void clearTextures() {
        for(Tex value : labels.values()) value.dispose();
        labels.clear();
    }

    @Override public void dispose() {
        clearTextures();
        icons.dispose();
        super.dispose();
    }
}
