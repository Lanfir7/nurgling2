package nurgling.widgets;

import haven.*;
import haven.Button;
import haven.Label;
import haven.Window;
import nurgling.*;
import nurgling.actions.bots.FetchStorageItemBot;
import nurgling.db.FetchStorageDbSync;
import nurgling.db.StorageItemsCompress;
import nurgling.db.StorageOrphanPolicy;
import nurgling.db.StorageTableInfo;
import nurgling.db.dao.ContainerDao;
import nurgling.db.dao.StorageItemDao;
import nurgling.db.service.ContainerService;
import nurgling.db.service.StorageItemService;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;

import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Widget for displaying all storage items from the database.
 * Features:
 * - Sorting by clicking on column headers (Name, Quality, Count)
 * - Grouping modes: None, Quality, Q1, Q5, Q10 (like NInventory)
 * - Min quality filter
 * - Pagination for large item sets
 */
public class NStorageItemsWidget extends Window {

    private static final int PAGE_SIZE = 25;
    private static final int WINDOW_WIDTH = 720;
    private static final int WINDOW_HEIGHT = 500;

    // Column positions
    private static final int COL_NAME = UI.scale(10);
    private static final int COL_QUALITY = UI.scale(250);
    private static final int COL_COUNT = UI.scale(340);
    private static final int COL_DIST = UI.scale(410);
    private static final int COL_STORAGE = UI.scale(490);

    private int currentPage = 0;
    private List<GroupedItem> allItems = new ArrayList<>();
    private List<GroupedItem> displayedItems = new ArrayList<>();
    
    // Sorting
    private SortColumn currentSortColumn = SortColumn.QUALITY;
    private boolean sortDescending = true;
    
    // Grouping modes (like NInventory)
    public enum Grouping {
        NONE("Type"),
        Q("Quality"),
        Q5("Q 5"),
        Q10("Q 10");
        
        public final String displayName;
        
        Grouping(String displayName) {
            this.displayName = displayName;
        }
    }
    
    public enum SortColumn {
        NAME, QUALITY, COUNT, DIST, STORAGE
    }

    private StorageItemsList itemsList;
    private Label pageLabel;
    private Label totalLabel;
    private Dropbox<Grouping> groupingDropbox;
    private TextEntry searchField;
    private TextEntry qualityFilterEntry;
    private TextEntry qualityMaxFilterEntry;
    private CheckBox compressBox;
    private String searchText = "";
    private Double minQualityFilter = null;
    private Double maxQualityFilter = null;
    private boolean compressByType = false;
    private Grouping currentGrouping = Grouping.Q;
    private boolean isLoading = false;
    
    // Clickable column headers
    private Label nameHeaderLabel;
    private Label qualityHeaderLabel;
    private Label countHeaderLabel;
    private Label distHeaderLabel;
    private Label storageHeaderLabel;

    /**
     * Grouped item representation for display
     */
    public static class GroupedItem {
        public final String name;
        public final double quality; // -1 for type-only grouping (will show range)
        public final double minQuality;
        public final double maxQuality;
        public final int count;
        public final int distanceTiles;
        public final String storageName;
        public final List<StorageItemDao.StorageItemData> items;

        public GroupedItem(String name, double quality, int count, List<StorageItemDao.StorageItemData> items,
                           int distanceTiles, String storageName) {
            this.name = name;
            this.quality = quality;
            this.minQuality = items.stream().mapToDouble(StorageItemDao.StorageItemData::getQuality).min().orElse(0);
            this.maxQuality = items.stream().mapToDouble(StorageItemDao.StorageItemData::getQuality).max().orElse(0);
            this.count = count;
            this.items = items;
            this.distanceTiles = distanceTiles;
            this.storageName = storageName != null ? storageName : "—";
        }

        public String getQualityDisplay() {
            if (quality >= 0) {
                return Utils.odformat2(quality, 2);
            } else {
                // Range display for type-only grouping
                if (minQuality == maxQuality) {
                    return Utils.odformat2(minQuality, 2);
                }
                return Utils.odformat2(minQuality, 2) + "-" + Utils.odformat2(maxQuality, 2);
            }
        }
    }

    public NStorageItemsWidget() {
        super(UI.scale(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT)), L10n.get("storage.window_title"));

        int y = UI.scale(5);
        int margin = UI.scale(10);

        // Row 1: Search field + Grouping dropdown
        prev = add(new Label(L10n.get("storage.search")), new Coord(margin, y + UI.scale(3)));
        searchField = add(new TextEntry(UI.scale(120), "") {
            @Override
            public boolean keydown(KeyDownEvent e) {
                boolean res = super.keydown(e);
                searchText = text().toLowerCase();
                applyFiltersAndSort();
                return res;
            }
        }, new Coord(UI.scale(60), y));

        // Grouping dropdown
        int groupingX = UI.scale(195);
        groupingDropbox = add(new Dropbox<Grouping>(UI.scale(80), Grouping.values().length, UI.scale(16)) {
            @Override
            protected Grouping listitem(int i) {
                return Grouping.values()[i];
            }

            @Override
            protected int listitems() {
                return Grouping.values().length;
            }

            @Override
            protected void drawitem(GOut g, Grouping item, int i) {
                g.text(item.displayName, Coord.z);
            }

            @Override
            public void change(Grouping item) {
                super.change(item);
                if (item == null)
                    return;
                currentGrouping = item;
                processItems();
            }
        }, new Coord(groupingX, y));
        // Don't call change() here - it triggers processItems before all widgets are created
        // Set selection directly instead
        groupingDropbox.sel = currentGrouping;
        groupingDropbox.settip(L10n.get("storage.grouping_tip"));

        // Quality filter
        int qualityX = UI.scale(290);
        add(new Label("Q>="), new Coord(qualityX, y + UI.scale(3)));
        qualityFilterEntry = add(new TextEntry(UI.scale(40), "") {
            @Override
            public void changed() {
                super.changed();
                parseQualityFilter();
                applyFiltersAndSort();
            }
        }, new Coord(qualityX + UI.scale(28), y));
        qualityFilterEntry.settip(L10n.get("storage.quality_filter_tip"));

        int maxQualityX = qualityX + UI.scale(74);
        add(new Label("Q<"), new Coord(maxQualityX, y + UI.scale(3)));
        qualityMaxFilterEntry = add(new TextEntry(UI.scale(40), "") {
            @Override
            public void changed() {
                super.changed();
                parseQualityFilter();
                applyFiltersAndSort();
            }
        }, new Coord(maxQualityX + UI.scale(18), y));
        qualityMaxFilterEntry.settip(L10n.get("storage.quality_max_filter_tip"));

        compressBox = add(new CheckBox(L10n.get("storage.compress")),
                new Coord(maxQualityX + UI.scale(64), y));
        compressBox.changed(val -> {
            compressByType = val;
            applyFiltersAndSort();
        });
        compressBox.settip(L10n.get("storage.compress_tip"));

        // Refresh button
        add(new Button(UI.scale(70), L10n.get("storage.refresh")) {
            @Override
            public void click() {
                loadItems();
            }
        }, new Coord(UI.scale(WINDOW_WIDTH - 90), y));

        y += UI.scale(30);

        // Column headers (clickable for sorting)
        int headerY = y;
        nameHeaderLabel = add(new Label(L10n.get("storage.col_name") + " ▼") {
            @Override
            public boolean mousedown(MouseDownEvent ev) {
                if (ev.b == 1) {
                    toggleSort(SortColumn.NAME);
                    return true;
                }
                return super.mousedown(ev);
            }
        }, new Coord(COL_NAME, headerY));
        
        qualityHeaderLabel = add(new Label(L10n.get("storage.col_quality") + " ▼") {
            @Override
            public boolean mousedown(MouseDownEvent ev) {
                if (ev.b == 1) {
                    toggleSort(SortColumn.QUALITY);
                    return true;
                }
                return super.mousedown(ev);
            }
        }, new Coord(COL_QUALITY, headerY));
        
        countHeaderLabel = add(new Label(L10n.get("storage.col_count") + " ▼") {
            @Override
            public boolean mousedown(MouseDownEvent ev) {
                if (ev.b == 1) {
                    toggleSort(SortColumn.COUNT);
                    return true;
                }
                return super.mousedown(ev);
            }
        }, new Coord(COL_COUNT, headerY));

        distHeaderLabel = add(new Label(L10n.get("storage.col_dist") + " ▼") {
            @Override
            public boolean mousedown(MouseDownEvent ev) {
                if (ev.b == 1) {
                    toggleSort(SortColumn.DIST);
                    return true;
                }
                return super.mousedown(ev);
            }
        }, new Coord(COL_DIST, headerY));

        storageHeaderLabel = add(new Label(L10n.get("storage.col_storage") + " ▼") {
            @Override
            public boolean mousedown(MouseDownEvent ev) {
                if (ev.b == 1) {
                    toggleSort(SortColumn.STORAGE);
                    return true;
                }
                return super.mousedown(ev);
            }
        }, new Coord(COL_STORAGE, headerY));
        
        updateHeaderLabels();

        y += UI.scale(20);

        // Items list
        itemsList = add(new StorageItemsList(UI.scale(new Coord(WINDOW_WIDTH - 20, WINDOW_HEIGHT - 120))),
                new Coord(UI.scale(5), y));

        // Pagination controls at bottom
        int bottomY = UI.scale(WINDOW_HEIGHT - 45);

        prev = add(new Button(UI.scale(50), "<<") {
            @Override
            public void click() {
                if (currentPage > 0) {
                    currentPage--;
                    updateDisplayedItems();
                }
            }
        }, new Coord(UI.scale(WINDOW_WIDTH / 2 - 80), bottomY));

        pageLabel = add(new Label(""), new Coord(UI.scale(WINDOW_WIDTH / 2 - 20), bottomY + UI.scale(5)));

        add(new Button(UI.scale(50), ">>") {
            @Override
            public void click() {
                int maxPage = getMaxPage();
                if (currentPage < maxPage) {
                    currentPage++;
                    updateDisplayedItems();
                }
            }
        }, new Coord(UI.scale(WINDOW_WIDTH / 2 + 30), bottomY));

        totalLabel = add(new Label(""), new Coord(UI.scale(10), bottomY + UI.scale(5)));

        pack();
    }
    
    private void toggleSort(SortColumn column) {
        if (currentSortColumn == column) {
            sortDescending = !sortDescending;
        } else {
            currentSortColumn = column;
            sortDescending = true;
        }
        updateHeaderLabels();
        applyFiltersAndSort();
    }
    
    private void updateHeaderLabels() {
        if (nameHeaderLabel == null || qualityHeaderLabel == null || countHeaderLabel == null
                || distHeaderLabel == null || storageHeaderLabel == null) {
            return; // Not yet initialized
        }
        
        String nameText = L10n.get("storage.col_name");
        String qualityText = L10n.get("storage.col_quality");
        String countText = L10n.get("storage.col_count");
        String distText = L10n.get("storage.col_dist");
        String storageText = L10n.get("storage.col_storage");
        
        String arrow = sortDescending ? " ▼" : " ▲";
        
        nameHeaderLabel.settext(nameText + (currentSortColumn == SortColumn.NAME ? arrow : ""));
        qualityHeaderLabel.settext(qualityText + (currentSortColumn == SortColumn.QUALITY ? arrow : ""));
        countHeaderLabel.settext(countText + (currentSortColumn == SortColumn.COUNT ? arrow : ""));
        distHeaderLabel.settext(distText + (currentSortColumn == SortColumn.DIST ? arrow : ""));
        storageHeaderLabel.settext(storageText + (currentSortColumn == SortColumn.STORAGE ? arrow : ""));
    }
    
    private void parseQualityFilter() {
        minQualityFilter = parseQualityValue(qualityFilterEntry);
        maxQualityFilter = parseQualityValue(qualityMaxFilterEntry);
    }

    private static Double parseQualityValue(TextEntry entry) {
        if (entry == null) {
            return null;
        }
        String text = entry.text().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int getMaxPage() {
        return Math.max(0, (displayedItems.size() + PAGE_SIZE - 1) / PAGE_SIZE - 1);
    }

    private void updatePageLabel() {
        if (pageLabel == null || totalLabel == null) {
            return; // Not yet initialized
        }
        int maxPage = getMaxPage();
        pageLabel.settext((currentPage + 1) + " / " + (maxPage + 1));
        totalLabel.settext(L10n.get("storage.total_items", displayedItems.size()));
    }

    @Override
    public boolean show(boolean show) {
        if (show && (Boolean) NConfig.get(NConfig.Key.ndbenable) &&
                ui != null && ui.core != null && ui.core.databaseManager != null &&
                ui.core.databaseManager.isReady()) {
            loadItems();
        }
        return super.show(show);
    }

    private void loadItems() {
        if (isLoading) return;
        if (ui == null || ui.core == null || ui.core.databaseManager == null ||
                !ui.core.databaseManager.isReady()) {
            NUtils.getGameUI().msg(L10n.get("storage.db_not_ready"), Color.RED);
            return;
        }

        isLoading = true;
        StorageItemService storageService = new StorageItemService(ui.core.databaseManager);
        ContainerService containerService = new ContainerService(ui.core.databaseManager);

        // Use a dedicated Thread instead of ForkJoinPool.commonPool() (via CompletableFuture)
        // to avoid ClassLoader issues — ForkJoinPool threads may use a different ClassLoader
        // that cannot find application classes from hafen.jar
        Thread loader = new Thread(() -> {
            try {
                List<StorageItemDao.StorageItemData> items = storageService.loadAllStorageItems();
                Map<String, ContainerDao.ContainerData> containers = new HashMap<>();
                for (ContainerDao.ContainerData container : containerService.loadAllContainers()) {
                    containers.put(container.getHash(), container);
                }
                // Filter out items with negative quality (shouldn't be in DB, but just in case)
                List<StorageItemDao.StorageItemData> validItems = items.stream()
                    .filter(item -> item.getQuality() >= 0)
                    .collect(Collectors.toList());
                processLoadedItems(validItems, containers);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoading = false;
            }
        }, "StorageItemLoader");
        loader.setDaemon(true);
        loader.start();
    }

    private void processLoadedItems(List<StorageItemDao.StorageItemData> items,
                                    Map<String, ContainerDao.ContainerData> containers) {
        this.rawItems = items;
        this.containersByHash = containers != null ? containers : new HashMap<>();
        processItems();
    }

    private List<StorageItemDao.StorageItemData> rawItems = new ArrayList<>();
    private Map<String, ContainerDao.ContainerData> containersByHash = new HashMap<>();

    private void processItems() {
        if (rawItems == null || rawItems.isEmpty()) {
            allItems = new ArrayList<>();
            applyFiltersAndSort();
            return;
        }

        Map<String, List<StorageItemDao.StorageItemData>> grouped;

        switch (currentGrouping) {
            case NONE:
                // Group only by name
                grouped = rawItems.stream()
                        .collect(Collectors.groupingBy(StorageItemDao.StorageItemData::getName));
                break;
            case Q:
                // Group by name + exact quality (rounded to 2 decimals)
                grouped = rawItems.stream()
                        .collect(Collectors.groupingBy(item ->
                                item.getName() + "|" + String.format("%.2f", item.getQuality())));
                break;
            case Q5:
                // Group by name + quality rounded to 5
                grouped = rawItems.stream()
                        .collect(Collectors.groupingBy(item ->
                                item.getName() + "|" + ((int) Math.floor(item.getQuality() / 5) * 5)));
                break;
            case Q10:
                // Group by name + quality rounded to 10
                grouped = rawItems.stream()
                        .collect(Collectors.groupingBy(item ->
                                item.getName() + "|" + ((int) Math.floor(item.getQuality() / 10) * 10)));
                break;
            default:
                grouped = rawItems.stream()
                        .collect(Collectors.groupingBy(StorageItemDao.StorageItemData::getName));
        }

        List<GroupedItem> result = new ArrayList<>();

        for (Map.Entry<String, List<StorageItemDao.StorageItemData>> entry : grouped.entrySet()) {
            List<StorageItemDao.StorageItemData> itemGroup = entry.getValue();
            if (itemGroup.isEmpty()) continue;

            StorageItemDao.StorageItemData first = itemGroup.get(0);
            
            // For non-exact grouping, use -1 to indicate range display
            double quality;
            if (currentGrouping == Grouping.NONE) {
                quality = -1;
            } else if (currentGrouping == Grouping.Q) {
                quality = first.getQuality();
            } else {
                // For Q1, Q5, Q10 - show range
                quality = -1;
            }

            StorageLocation location = resolveGroupLocation(itemGroup);

            result.add(new GroupedItem(
                    first.getName(),
                    quality,
                    itemGroup.size(),
                    itemGroup,
                    location.distanceTiles,
                    location.storageName
            ));
        }

        allItems = result;
        applyFiltersAndSort();
    }

    private static List<StorageItemsCompress.Row> toCompressRows(List<GroupedItem> rows) {
        List<StorageItemsCompress.Row> out = new ArrayList<>();
        for (GroupedItem row : rows) {
            out.add(new StorageItemsCompress.Row(
                    row.name, row.quality, row.items, row.distanceTiles, row.storageName));
        }
        return out;
    }

    private static List<GroupedItem> fromCompressRows(List<StorageItemsCompress.Row> rows) {
        List<GroupedItem> out = new ArrayList<>();
        for (StorageItemsCompress.Row row : rows) {
            out.add(new GroupedItem(
                    row.name, row.quality, row.items.size(), row.items,
                    row.distanceTiles, row.storageName));
        }
        return out;
    }

    private void applyFiltersAndSort() {
        // Apply search filter
        List<GroupedItem> filtered = allItems;
        
        if (searchText != null && !searchText.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> item.name.toLowerCase().contains(searchText))
                    .collect(Collectors.toList());
        }

        List<StorageItemsCompress.Row> rows = toCompressRows(filtered);
        if (minQualityFilter != null || maxQualityFilter != null) {
            rows = StorageItemsCompress.keepQualityRange(rows, minQualityFilter, maxQualityFilter);
        }
        if (compressByType) {
            rows = StorageItemsCompress.byType(rows);
        }
        filtered = fromCompressRows(rows);
        
        displayedItems = new ArrayList<>(filtered);

        // Sort
        Comparator<GroupedItem> comparator;
        boolean reverse = sortDescending;
        switch (currentSortColumn) {
            case NAME:
                comparator = Comparator.comparing(a -> a.name.toLowerCase());
                break;
            case COUNT:
                comparator = Comparator.comparingInt(a -> a.count);
                break;
            case DIST:
                if (sortDescending) {
                    comparator = Comparator
                            .comparingInt((GroupedItem a) -> a.distanceTiles < 0 ? 1 : 0)
                            .thenComparing(Comparator.comparingInt((GroupedItem a) -> a.distanceTiles).reversed());
                } else {
                    comparator = Comparator
                            .comparingInt((GroupedItem a) -> a.distanceTiles < 0 ? 1 : 0)
                            .thenComparingInt(a -> a.distanceTiles);
                }
                reverse = false;
                break;
            case STORAGE:
                comparator = Comparator.comparing(a -> a.storageName.toLowerCase());
                break;
            case QUALITY:
            default:
                comparator = Comparator.comparingDouble(a -> a.quality >= 0 ? a.quality : a.maxQuality);
                break;
        }
        
        if (reverse) {
            comparator = comparator.reversed();
        }
        
        displayedItems.sort(comparator);

        currentPage = 0;
        updateDisplayedItems();
    }

    private void updateDisplayedItems() {
        if (itemWidgets == null) {
            return; // Not yet initialized
        }
        
        int startIdx = currentPage * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, displayedItems.size());

        itemWidgets.clear();
        for (int i = startIdx; i < endIdx; i++) {
            itemWidgets.add(new StorageItemWidget(displayedItems.get(i)));
        }

        updatePageLabel();
    }

    private static final class StorageLocation {
        final int distanceTiles;
        final String storageName;

        StorageLocation(int distanceTiles, String storageName) {
            this.distanceTiles = distanceTiles;
            this.storageName = storageName;
        }
    }

    private StorageLocation resolveGroupLocation(List<StorageItemDao.StorageItemData> itemGroup) {
        Map<String, String> liveNames = liveStorageNames();
        int bestDist = StorageTableInfo.UNKNOWN_DIST;
        String nearestName = "—";
        List<String> names = new ArrayList<>();
        for (StorageItemDao.StorageItemData item : itemGroup) {
            String hash = item.getContainer();
            String name = liveNames.getOrDefault(hash, "—");
            names.add(name);
            int dist = distanceTiles(hash);
            if (dist >= 0 && (bestDist < 0 || dist < bestDist)) {
                bestDist = dist;
                nearestName = name;
            }
        }
        return new StorageLocation(bestDist, StorageTableInfo.storageLabel(nearestName, names));
    }

    private Map<String, String> liveStorageNames() {
        Map<String, String> names = new HashMap<>();
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return names;
        }
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.ngob == null || gob.ngob.hash == null || gob.ngob.name == null) {
                    continue;
                }
                names.put(gob.ngob.hash, StorageTableInfo.containerTitle(gob.ngob.name));
            }
        }
        return names;
    }

    private int distanceTiles(String containerHash) {
        if (containerHash == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        ContainerDao.ContainerData data = containersByHash.get(containerHash);
        Gob player = NUtils.player();
        NGameUI gui = NUtils.getGameUI();
        if (data == null || player == null || player.rc == null || gui == null
                || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null
                || gui.ui.sess.glob.map == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        Coord stored = StorageOrphanPolicy.parseGcoord(data.getCoord());
        if (stored == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        MCache map = gui.ui.sess.glob.map;
        try {
            Coord pltc = new Coord2d(player.rc.x / MCache.tilesz.x, player.rc.y / MCache.tilesz.y).floor();
            MCache.Grid playerGrid;
            synchronized (map.grids) {
                if (!map.grids.containsKey(pltc.div(MCache.cmaps))) {
                    return StorageTableInfo.UNKNOWN_DIST;
                }
                playerGrid = map.getgridt(pltc);
            }
            if (playerGrid == null) {
                return StorageTableInfo.UNKNOWN_DIST;
            }
            if (playerGrid.id == data.getGridId()) {
                Coord playerGcoord = player.rc.sub(playerGrid.ul.mul(Coord2d.of(11, 11))).floor(OCache.posres);
                return StorageTableInfo.tilesBetween(playerGcoord, stored);
            }
            MCache.Grid containerGrid = findGridById(map, data.getGridId());
            if (containerGrid == null) {
                return StorageTableInfo.UNKNOWN_DIST;
            }
            Coord2d containerRc = Coord2d.of(containerGrid.ul).mul(MCache.tilesz)
                    .add(Coord2d.of(stored).mul(OCache.posres));
            return (int) Math.round(player.rc.dist(containerRc) / MCache.tilesz.x);
        } catch (Loading e) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
    }

    private static MCache.Grid findGridById(MCache map, long gridId) {
        synchronized (map.grids) {
            for (MCache.Grid grid : map.grids.values()) {
                if (grid != null && grid.id == gridId) {
                    return grid;
                }
            }
        }
        return null;
    }

    private final ArrayList<StorageItemWidget> itemWidgets = new ArrayList<>();

    /**
     * Widget for displaying a single grouped item
     */
    public class StorageItemWidget extends Widget {
        private final GroupedItem item;

        public StorageItemWidget(GroupedItem item) {
            this.item = item;
            sz = UI.scale(new Coord(WINDOW_WIDTH - 40, 22));

            add(new Label(truncateName(item.name, 28)), new Coord(COL_NAME, 0));
            add(new Label(item.getQualityDisplay()), new Coord(COL_QUALITY, 0));
            add(new Label(String.valueOf(item.count)), new Coord(COL_COUNT, 0));
            add(new Label(StorageTableInfo.distanceLabel(item.distanceTiles)), new Coord(COL_DIST, 0));
            add(new Label(truncateName(item.storageName, 18)), new Coord(COL_STORAGE, 0));
        }

        private String truncateName(String name, int maxLen) {
            if (name == null) return "";
            if (name.length() <= maxLen) return name;
            return name.substring(0, maxLen - 3) + "...";
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1) {
                showItemDetails();
                return true;
            } else if (ev.b == 3) {
                showQuantitySelector();
                return true;
            }
            return super.mousedown(ev);
        }

        private void showItemDetails() {
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
                this::startFetchBot,
                this::deleteGroupedItem
            );
            GameUI gui = NUtils.getGameUI();
            gui.add(selector, gui.sz.div(2).sub(selector.sz.div(2)));
            selector.raise();
        }
        
        private void startFetchBot(int count) {
            // Get all raw items for this group
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
            NUtils.getGameUI().msg(L10n.get("storage.deleted_items").replace("{0}", String.valueOf(deleted)));
            loadItems();
        }
    }

    /**
     * List widget for displaying storage items
     */
    public class StorageItemsList extends SListBox<StorageItemWidget, Widget> {
        private final Color bg = new Color(30, 40, 40, 160);

        public StorageItemsList(Coord sz) {
            super(sz, UI.scale(22));
        }

        @Override
        protected List<StorageItemWidget> items() {
            synchronized (itemWidgets) {
                return itemWidgets;
            }
        }

        @Override
        protected Widget makeitem(StorageItemWidget item, int idx, Coord sz) {
            return new ItemWidget<StorageItemWidget>(this, sz, item) {
                {
                    item.resize(sz);
                    add(item);
                }
            };
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            hide();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}
