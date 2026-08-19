package nurgling.widgets;

import haven.*;
import nurgling.NUtils;
import nurgling.actions.bots.FetchStorageItemBot;
import nurgling.db.FetchStorageDbSync;
import nurgling.db.StorageTableInfo;
import nurgling.db.service.StorageItemService;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Right-side ingredient stock panel for craft search mode: chrome-like tabs per input,
 * compact StorageItems list, RMB take via FetchStorageItemBot.
 */
public class CraftIngredientSearchPanel extends Widget {
    private static final int WIDTH = UI.scale(340);
    private static final Coord ICON = Inventory.sqsz;
    private static final int ROW_H = UI.scale(22);
    private static final int ROWS = 5;
    private static final Color TAB_SEL = new Color(0, 255, 0, 180);
    private static final Color LIST_BG = new Color(30, 40, 40, 160);

    private static final int COL_NAME = UI.scale(4);
    private static final int COL_QUALITY = UI.scale(108);
    private static final int COL_COUNT = UI.scale(152);
    private static final int COL_DIST = UI.scale(186);
    private static final int COL_STORAGE = UI.scale(224);

    private final List<IngTab> tabs = new ArrayList<>();
    private final Map<Integer, List<GroupedItem>> results = new LinkedHashMap<>();
    private final ResultList list;
    private final MinQualityBox minQBox;
    private int selectedInput = -1;
    private List<RowWidget> rows = new ArrayList<>();
    private double minQuality = 0;

    public CraftIngredientSearchPanel() {
        super(new Coord(WIDTH, ICON.y + UI.scale(4) + ROW_H * ROWS));
        minQBox = add(new MinQualityBox(ICON), Coord.z);
        list = add(new ResultList(new Coord(WIDTH, ROW_H * ROWS)), new Coord(0, ICON.y + UI.scale(2)));
        layoutHeader();
    }

    public void syncTabs(List<NMakewindow.Spec> inputs) {
        for (IngTab tab : tabs) {
            tab.destroy();
        }
        tabs.clear();
        int idx = 0;
        for (NMakewindow.Spec spec : inputs) {
            final int inputIndex = idx++;
            if (spec.ing != null && spec.ing.isIgnored) {
                continue;
            }
            tabs.add(add(new IngTab(spec, inputIndex), Coord.z));
        }
        if (tabs.isEmpty()) {
            selectedInput = -1;
            layoutHeader();
            refreshRows();
            return;
        }
        boolean keep = false;
        for (IngTab tab : tabs) {
            if (tab.inputIndex == selectedInput) {
                keep = true;
                break;
            }
        }
        if (!keep) {
            selectedInput = tabs.get(0).inputIndex;
        }
        layoutHeader();
        refreshRows();
    }

    private void layoutHeader() {
        int x = 0;
        int gap = UI.scale(1);
        for (IngTab tab : tabs) {
            tab.move(new Coord(x, 0));
            x += ICON.x + gap;
        }
        int qx = x;
        if (qx + ICON.x > WIDTH) {
            qx = Math.max(0, WIDTH - ICON.x);
        }
        minQBox.move(new Coord(qx, 0));
        minQBox.raise();
    }

    public void setResults(Map<Integer, List<GroupedItem>> byInput) {
        results.clear();
        if (byInput != null) {
            results.putAll(byInput);
        }
        refreshRows();
    }

    private void selectTab(int inputIndex) {
        selectedInput = inputIndex;
        refreshRows();
    }

    private void applyMinQuality() {
        String raw = minQBox.text().trim().replace(',', '.');
        if (raw.isEmpty()) {
            minQuality = 0;
            refreshRows();
            return;
        }
        try {
            minQuality = Double.parseDouble(raw);
            refreshRows();
        } catch (NumberFormatException ignored) {
        }
    }

    private void refreshRows() {
        List<GroupedItem> items = new ArrayList<>(results.getOrDefault(selectedInput, Collections.emptyList()));
        items.removeIf(item -> item.quality < minQuality && item.maxQuality < minQuality);
        items.sort(java.util.Comparator.comparingDouble((GroupedItem a) -> a.quality).reversed()
                .thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
        List<RowWidget> next = new ArrayList<>();
        for (GroupedItem item : items) {
            next.add(new RowWidget(item));
        }
        rows = next;
    }

    private class MinQualityBox extends Widget {
        private final TextEntry entry;

        MinQualityBox(Coord sz) {
            super(sz);
            int w = Math.max(UI.scale(18), sz.x - UI.scale(6));
            entry = add(new TextEntry(w, "") {
                @Override
                protected void changed() {
                    super.changed();
                    applyMinQuality();
                }
            }, new Coord((sz.x - w) / 2, (sz.y - UI.scale(16)) / 2));
        }

        String text() {
            return entry.text();
        }

        @Override
        public void draw(GOut g) {
            g.image(Inventory.invsq, Coord.z, sz);
            super.draw(g);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            return L10n.get("storage.quality_filter_tip");
        }
    }

    private class IngTab extends Widget {
        final NMakewindow.Spec spec;
        final int inputIndex;

        IngTab(NMakewindow.Spec spec, int inputIndex) {
            super(new Coord(ICON));
            this.spec = spec;
            this.inputIndex = inputIndex;
        }

        @Override
        public void draw(GOut g) {
            g.image(Inventory.invsq, Coord.z, sz);
            GOut sg = g.reclip(Coord.z, sz);
            spec.draw(sg);
            if (inputIndex == selectedInput) {
                sg.chcolor(TAB_SEL);
                sg.rect(Coord.z, sz);
                sg.chcolor();
            }
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1) {
                selectTab(inputIndex);
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            return spec.name != null ? spec.name : null;
        }
    }

    private class ResultList extends SListBox<RowWidget, Widget> {
        ResultList(Coord sz) {
            super(sz, ROW_H);
        }

        @Override
        protected List<RowWidget> items() {
            return rows;
        }

        @Override
        protected Widget makeitem(RowWidget item, int idx, Coord sz) {
            return new ItemWidget<RowWidget>(this, sz, item) {
                {
                    item.resize(sz);
                    add(item);
                }
            };
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(LIST_BG);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }

    private class RowWidget extends Widget {
        private final GroupedItem item;

        RowWidget(GroupedItem item) {
            this.item = item;
            sz = new Coord(WIDTH - UI.scale(16), UI.scale(20));
            add(new Label(truncate(item.name, 14)), new Coord(COL_NAME, 0));
            add(new Label(item.getQualityDisplay()), new Coord(COL_QUALITY, 0));
            add(new Label(String.valueOf(item.count)), new Coord(COL_COUNT, 0));
            add(new Label(StorageTableInfo.distanceLabel(item.distanceTiles)), new Coord(COL_DIST, 0));
            add(new Label(truncate(item.storageName, 10)), new Coord(COL_STORAGE, 0));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1) {
                showDetails();
                return true;
            } else if (ev.b == 3) {
                showQuantitySelector();
                return true;
            }
            return super.mousedown(ev);
        }

        private void showDetails() {
            StringBuilder sb = new StringBuilder();
            sb.append(item.name).append("\n");
            sb.append(L10n.get("storage.quality")).append(": ").append(item.getQualityDisplay()).append("\n");
            sb.append(L10n.get("storage.count")).append(": ").append(item.count).append("\n");
            sb.append(L10n.get("storage.col_dist")).append(": ").append(StorageTableInfo.distanceLabel(item.distanceTiles)).append("\n");
            sb.append(L10n.get("storage.col_storage")).append(": ").append(item.storageName);
            NUtils.getGameUI().msg(sb.toString());
        }

        private void showQuantitySelector() {
            int max = Math.max(1, item.count);
            NQuantitySelector selector = new NQuantitySelector(
                    L10n.get("storage.select_quantity"),
                    max,
                    this::startFetch,
                    this::deleteGroupedItem
            );
            GameUI gui = NUtils.getGameUI();
            gui.add(selector, gui.sz.div(2).sub(selector.sz.div(2)));
            selector.raise();
        }

        private void startFetch(int count) {
            FetchStorageItemBot bot = new FetchStorageItemBot(item, count, item.items);
            BotExecutor.runAsync("FetchStorageItemBot", bot);
        }

        private void deleteGroupedItem() {
            if (ui == null || ui.core == null || ui.core.databaseManager == null
                    || !ui.core.databaseManager.isReady()) {
                NUtils.getGameUI().msg(L10n.get("storage.db_not_ready"), Color.RED);
                return;
            }
            List<String> hashes = FetchStorageDbSync.hashesToDelete(item.items);
            if (hashes.isEmpty()) {
                return;
            }
            StorageItemService storageService = new StorageItemService(ui.core.databaseManager);
            int deleted = 0;
            for (String hash : hashes) {
                try {
                    storageService.deleteStorageItem(hash);
                    deleted++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            List<GroupedItem> bucket = results.get(selectedInput);
            if (bucket != null) {
                bucket.remove(item);
            }
            NUtils.getGameUI().msg(L10n.get("storage.deleted_items").replace("{0}", String.valueOf(deleted)));
            refreshRows();
        }
    }

    private static String truncate(String name, int maxLen) {
        if (name == null) {
            return "";
        }
        if (name.length() <= maxLen) {
            return name;
        }
        return name.substring(0, maxLen - 3) + "...";
    }
}
