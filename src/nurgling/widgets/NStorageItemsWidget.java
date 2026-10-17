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
import nurgling.db.StorageVillageFilter;
import nurgling.db.dao.ContainerDao;
import nurgling.db.dao.StorageItemDao;
import nurgling.db.service.ContainerService;
import nurgling.db.service.StorageItemService;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.widgets.storage.StashView;
import nurgling.widgets.storage.VillageStorage;

import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Widget for displaying all storage items from the database.
 * Two views: tabs of item slots by category ({@link StashView}, the default) and this list.
 * List features:
 * - Sorting by clicking on column headers (Name, Quality, Count)
 * - Grouping modes: None, Quality, Q1, Q5, Q10 (like NInventory)
 * - Min quality filter
 * - Pagination for large item sets
 * - Only items in your village (see {@link nurgling.navigation.ChunkNavGraph#pieceWithMostAreas}), so storage
 *   seen in another village or at a bot's spot stays out
 */
public class NStorageItemsWidget extends Window {
    private final StorageItemsRefreshSignal refreshSignal = new StorageItemsRefreshSignal();

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
    /** UI-thread state; workers only publish immutable results below. */
    private boolean isLoading = false;
    private long dataGeneration = 0;
    private volatile LoadResult completedLoad;
    private volatile CleanupResult completedCleanup;

    private static final class LoadResult {
        final long generation;
        final List<StorageItemDao.StorageItemData> items;
        final Map<String, ContainerDao.ContainerData> containers;
        final Set<Long> village;
        final Exception error;

        LoadResult(long generation, List<StorageItemDao.StorageItemData> items,
                   Map<String, ContainerDao.ContainerData> containers, Set<Long> village, Exception error) {
            this.generation = generation;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.containers = Collections.unmodifiableMap(new HashMap<>(containers));
            this.village = Collections.unmodifiableSet(new HashSet<>(village));
            this.error = error;
        }
    }

    private static final class CleanupResult {
        final Set<String> deletedHashes;
        final int deletedCount;

        CleanupResult(Set<String> deletedHashes) {
            this.deletedHashes = Collections.unmodifiableSet(new HashSet<>(deletedHashes));
            this.deletedCount = deletedHashes.size();
        }
    }
    /** May be called from the database worker after tracked storage contents change. */
    public void requestRefresh() {
        refreshSignal.request();
    }

    private CheckBox onlyVillageBox;
    private Label hiddenLabel;
    /** Hides rows whose container isn't in the village, or has no location at all. */
    private boolean onlyVillage = true;
    /** Recorded chunks of the village, worked out on each load; empty when there's nothing to go by. */
    private volatile Set<Long> villageChunks = Collections.emptySet();
    
    // Clickable column headers
    private Label nameHeaderLabel;
    private Label qualityHeaderLabel;
    private Label countHeaderLabel;
    private Label distHeaderLabel;
    private Label storageHeaderLabel;

    // Tab view and the list-only widgets it replaces
    private StashView stashView;
    private Button modeButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private boolean tabsMode = true;
    /** The search was filled in by clicking a slot, so going back to the tabs clears it. */
    private boolean searchFromSlot = false;
    private Runnable pendingMenu;

    /**
     * Grouped item representation for display
     */
    public static class GroupedItem {
        public final String name;
        public final double quality; // -1 for type-only grouping (will show range), 0 for unknown
        public final double minQuality; // over the items whose quality is known
        public final double maxQuality;
        public final boolean hasUnknownQuality; // stockpile items show no quality
        public final String bulkUnit; // "l", "seeds"... for barrel/cistern groups, null for items
        public final double bulkAmount;
        public final int count;
        public final int distanceTiles;
        public final String storageName;
        public final List<StorageItemDao.StorageItemData> items;

        public GroupedItem(String name, double quality, int count, List<StorageItemDao.StorageItemData> items,
                           int distanceTiles, String storageName) {
            this.name = name;
            this.quality = quality;
            this.minQuality = items.stream().filter(StorageItemDao.StorageItemData::hasQuality)
                    .mapToDouble(StorageItemDao.StorageItemData::getQuality).min().orElse(0);
            this.maxQuality = items.stream().filter(StorageItemDao.StorageItemData::hasQuality)
                    .mapToDouble(StorageItemDao.StorageItemData::getQuality).max().orElse(0);
            this.hasUnknownQuality = items.stream().anyMatch(i -> !i.hasQuality());
            this.bulkUnit = items.isEmpty() ? null : items.get(0).getBulkUnit();
            this.bulkAmount = items.stream().mapToDouble(StorageItemDao.StorageItemData::getBulkAmount).sum();
            this.count = count;
            this.items = items;
            this.distanceTiles = distanceTiles;
            this.storageName = storageName != null ? storageName : "—";
        }

        public GroupedItem(String name, double quality, int count, List<StorageItemDao.StorageItemData> items) {
            this(name, quality, count, items, StorageTableInfo.UNKNOWN_DIST, "—");
        }

        /** Whether a stored item belongs to this group. */
        public boolean matches(StorageItemDao.StorageItemData data) {
            if (!data.getName().equals(name) || !java.util.Objects.equals(data.getBulkUnit(), bulkUnit))
                return false;
            if (!data.hasQuality())
                return hasUnknownQuality;
            return data.getQuality() >= minQuality && data.getQuality() <= maxQuality;
        }

        public String getQualityDisplay() {
            if (quality > 0) {
                return Utils.odformat2(quality, 2);
            } else if (quality == 0 || maxQuality <= 0) {
                return "?";
            } else {
                // Range display for type-only grouping
                String range = (minQuality == maxQuality) ? Utils.odformat2(minQuality, 2)
                        : Utils.odformat2(minQuality, 2) + "-" + Utils.odformat2(maxQuality, 2);
                return hasUnknownQuality ? range + ", ?" : range;
            }
        }

        public String getCountDisplay() {
            return (bulkUnit != null) ? Utils.odformat2(bulkAmount, 2) + " " + bulkUnit : String.valueOf(count);
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
                searchFromSlot = false;
                applyFiltersAndSort();
                if (stashView != null)
                    stashView.setSearch(searchText);
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
                if (stashView != null)
                    stashView.setMinQ(minQualityFilter);
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

        compressBox = add(new CheckBox(L10n.get("storage.compress")), Coord.z);
        compressBox.changed(val -> {
            compressByType = val;
            applyFiltersAndSort();
        });
        compressBox.settip(L10n.get("storage.compress_tip"));

        add(new Button(UI.scale(70), L10n.get("storage.clear")) {
            @Override
            public void click() {
                clearUnavailableItems();
            }
        }, new Coord(UI.scale(WINDOW_WIDTH - 170), y));

        // Refresh button
        add(new Button(UI.scale(70), L10n.get("storage.refresh")) {
            @Override
            public void click() {
                loadItems();
            }
        }, new Coord(UI.scale(WINDOW_WIDTH - 90), y));

        modeButton = add(new Button(UI.scale(80), L10n.get("storage.view_list")) {
            @Override
            public void click() {
                setTabsMode(!tabsMode);
            }
        }, Coord.z);

        y += UI.scale(30);

        onlyVillage = VillageStorage.onlyVillage();
        onlyVillageBox = add(new CheckBox(L10n.get("storage.only_village")) {
            @Override
            public void changed(boolean val) {
                onlyVillage = val;
                NConfig.set(NConfig.Key.storageOnlyVillage, val);
                applyAreaFilter();
            }
        }, new Coord(margin, y));
        onlyVillageBox.a = onlyVillage;
        onlyVillageBox.settip(L10n.get("storage.only_village_tip"));
        hiddenLabel = add(new Label(""), onlyVillageBox.pos("ur").adds(UI.scale(10), 0));
        placeViewControls();

        y = Math.max(onlyVillageBox.c.y + onlyVillageBox.sz.y,
                Math.max(modeButton.c.y + modeButton.sz.y, compressBox.c.y + compressBox.sz.y)) + UI.scale(6);

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
        itemsList = add(new StorageItemsList(UI.scale(new Coord(WINDOW_WIDTH - 20, WINDOW_HEIGHT - 142))),
                new Coord(UI.scale(5), y));

        // Pagination controls at bottom
        int bottomY = UI.scale(WINDOW_HEIGHT - 45);

        prevPageButton = add(new Button(UI.scale(50), "<<") {
            @Override
            public void click() {
                if (currentPage > 0) {
                    currentPage--;
                    updateDisplayedItems();
                }
            }
        }, new Coord(UI.scale(WINDOW_WIDTH / 2 - 80), bottomY));

        pageLabel = add(new Label(""), new Coord(UI.scale(WINDOW_WIDTH / 2 - 20), bottomY + UI.scale(5)));

        nextPageButton = add(new Button(UI.scale(50), ">>") {
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

        int stashY = headerY;
        stashView = add(new StashView(new Coord(UI.scale(WINDOW_WIDTH - 10), UI.scale(WINDOW_HEIGHT - 5) - stashY), this::openInList),
                new Coord(UI.scale(5), stashY));
        stashView.setSearch(searchText);
        stashView.setMinQ(minQualityFilter);
        setTabsMode(true);

        pack();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        LoadResult loaded = completedLoad;
        if (loaded != null) {
            completedLoad = null;
            if (loaded.error != null) {
                loaded.error.printStackTrace();
            } else if (loaded.generation == dataGeneration) {
                villageChunks = loaded.village;
                processLoadedItems(loaded.items, loaded.containers);
            } else {
                requestRefresh();
            }
            isLoading = false;
        }
        CleanupResult cleaned = completedCleanup;
        if (cleaned != null) {
            completedCleanup = null;
            removeLoadedHashes(cleaned.deletedHashes);
            NUtils.getGameUI().msg(L10n.get("storage.cleared_items", cleaned.deletedCount));
            isLoading = false;
            requestRefresh();
        }
        if (refreshSignal.take(visible(), isLoading))
            loadItems();
        if (pendingMenu != null) {
            Runnable r = pendingMenu;
            pendingMenu = null;
            r.run();
        }
    }

    /** Список and Сжать sit on the village row, clear of the quality fields and the icon tabs. */
    private void placeViewControls() {
        int right = sz.x - UI.scale(10);
        int rowY = onlyVillageBox.c.y;
        compressBox.move(new Coord(right - compressBox.sz.x, rowY));
        modeButton.move(new Coord(compressBox.c.x - UI.scale(8) - modeButton.sz.x, rowY));
    }

    private void setTabsMode(boolean tabs) {
        tabsMode = tabs;
        if (tabs && searchFromSlot) {
            searchFromSlot = false;
            searchField.settext("");
            searchText = "";
            applyFiltersAndSort();
            stashView.setSearch(searchText);
        }
        stashView.show(tabs);
        for (Widget w : new Widget[] {groupingDropbox, nameHeaderLabel, qualityHeaderLabel, countHeaderLabel, distHeaderLabel, storageHeaderLabel,
                itemsList, prevPageButton, nextPageButton, pageLabel, totalLabel})
            w.show(!tabs);
        modeButton.change(L10n.get(tabs ? "storage.view_list" : "storage.view_tabs"));
    }

    /** A slot was clicked in the tab view: show that item's quality groups in the list. */
    private void openInList(String name) {
        searchField.settext(name);
        searchText = name.toLowerCase();
        searchFromSlot = true;
        stashView.setSearch(searchText);
        applyFiltersAndSort();
        setTabsMode(false);
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
        refreshStashItems();
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

    public void showWithSearch(String query) {
        String q = query == null ? "" : query;
        searchText = q.toLowerCase();
        if (searchField != null)
            searchField.settext(q);
        show(true);
        raise();
        applyFiltersAndSort();
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
        long generation = dataGeneration;
        NGameUI gui = ui.gui;
        // Read on the UI thread; the village is worked out with the items, off it
        VillageStorage storage = VillageStorage.snapshot(gui);
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
                List<StorageItemDao.StorageItemData> validItems = VillageStorage.valid(items);
                Set<Long> village = storage.village();
                completedLoad = new LoadResult(generation, validItems, containers, village, null);
            } catch (Exception e) {
                completedLoad = new LoadResult(generation, Collections.emptyList(),
                        Collections.emptyMap(), Collections.emptySet(), e);
            }
        }, "StorageItemLoader");
        loader.setDaemon(true);
        loader.start();
    }

    private void clearUnavailableItems() {
        if (isLoading) return;
        if (ui == null || ui.core == null || ui.core.databaseManager == null ||
                !ui.core.databaseManager.isReady()) {
            NUtils.getGameUI().msg(L10n.get("storage.db_not_ready"), Color.RED);
            return;
        }

        isLoading = true;
        StorageItemService storageService = new StorageItemService(ui.core.databaseManager);
        List<String> hashes = unavailableItemHashes(new ArrayList<>(allItems));

        Thread cleaner = new Thread(() -> {
            Set<String> deleted = new HashSet<>();
            try {
                for (String hash : hashes) {
                    try {
                        storageService.deleteStorageItem(hash);
                        deleted.add(hash);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                completedCleanup = new CleanupResult(deleted);
            }
        }, "StorageItemCleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    static List<String> unavailableItemHashes(Iterable<GroupedItem> rows) {
        List<StorageItemDao.StorageItemData> unavailable = new ArrayList<>();
        if (rows != null) {
            for (GroupedItem row : rows) {
                if (row != null && StorageTableInfo.isUnavailable(
                        row.distanceTiles, row.storageName)) {
                    unavailable.addAll(row.items);
                }
            }
        }
        return FetchStorageDbSync.hashesToDelete(unavailable);
    }

    private void processLoadedItems(List<StorageItemDao.StorageItemData> items,
                                    Map<String, ContainerDao.ContainerData> containers) {
        this.containersByHash = containers != null ? containers : new HashMap<>();
        this.loadedItems = items;
        applyAreaFilter();
    }

    /** Everything the database returned; rawItems is what the filter lets through. */
    private List<StorageItemDao.StorageItemData> loadedItems = new ArrayList<>();
    private void removeLoadedHashes(Set<String> hashes) {
        if (hashes.isEmpty()) return;
        dataGeneration++;
        loadedItems = StorageVillageFilter.withoutHashes(loadedItems, hashes);
        applyAreaFilter();
    }
    private List<StorageItemDao.StorageItemData> rawItems = new ArrayList<>();
    private void refreshStashItems() {
        if(stashView == null) return;
        stashView.setItems(rawItems.stream().filter(row ->
                (minQualityFilter == null || row.getQuality() >= minQualityFilter) &&
                (maxQualityFilter == null || row.getQuality() < maxQualityFilter))
                .collect(Collectors.toList()));
        stashView.setMinQ(minQualityFilter);
    }
    private Map<String, ContainerDao.ContainerData> containersByHash = new HashMap<>();

    private void applyAreaFilter() {
        List<StorageItemDao.StorageItemData> visible = StorageVillageFilter.apply(loadedItems, onlyVillage, villageChunks);
        int hiddenCount = loadedItems.size() - visible.size();
        if (hiddenLabel != null)
            hiddenLabel.settext(hiddenCount > 0 ? L10n.get("storage.hidden_items", hiddenCount) : "");
        this.rawItems = visible;
        refreshStashItems();
        processItems();
    }

    private void processItems() {
        if (rawItems == null || rawItems.isEmpty()) {
            allItems = new ArrayList<>();
            applyFiltersAndSort();
            return;
        }

        java.util.function.Function<StorageItemDao.StorageItemData, String> key;

        switch (currentGrouping) {
            case Q:
                // Group by name + exact quality (rounded to 2 decimals)
                key = item -> item.getName() + "|" + String.format("%.2f", item.getQuality());
                break;
            case Q5:
                // Group by name + quality rounded to 5
                key = item -> item.getName() + "|" + ((int) Math.floor(item.getQuality() / 5) * 5);
                break;
            case Q10:
                // Group by name + quality rounded to 10
                key = item -> item.getName() + "|" + ((int) Math.floor(item.getQuality() / 10) * 10);
                break;
            case NONE:
            default:
                // Group only by name
                key = StorageItemDao.StorageItemData::getName;
        }

        // Keep barrel/cistern amounts apart from countable items of the same name
        java.util.function.Function<StorageItemDao.StorageItemData, String> baseKey = key;
        Map<String, List<StorageItemDao.StorageItemData>> grouped = rawItems.stream()
                .collect(Collectors.groupingBy(item -> (item.isBulk() ? "#" + item.getBulkUnit() + "|" : "") + baseKey.apply(item)));

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
            add(new Label(item.getCountDisplay()), new Coord(COL_COUNT, 0));
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
            sb.append(L10n.get("storage.count")).append(": ").append(item.getCountDisplay()).append("\n");
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
            if(item.bulkUnit != null) {
                NUtils.getGameUI().msg(L10n.get("storage.fetch_bulk_unsupported"), Color.YELLOW);
                return;
            }
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
            Set<String> deletedHashes = new HashSet<>();
            for (String hash : hashes) {
                try {
                    storageService.deleteStorageItem(hash);
                    deleted++;
                    deletedHashes.add(hash);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            removeLoadedHashes(deletedHashes);
            NUtils.getGameUI().msg(L10n.get("storage.deleted_items").replace("{0}", String.valueOf(deleted)));
            requestRefresh();
        }
    }

    /**
     * Asks how many of a group to take, then sends the fetch bot.
     * @param candidates stored rows to fetch from; only those the group matches are used
     */
    public static void requestTake(GroupedItem item, List<StorageItemDao.StorageItemData> candidates) {
        if (item.bulkUnit != null) {
            // A liquid needs a container in hand; the fetch bot only carries items
            NUtils.getGameUI().msg(L10n.get("storage.fetch_bulk_unsupported"), Color.YELLOW);
            return;
        }
        if (item.count <= 1) {
            // Only one item, fetch directly
            startFetchBot(item, 1, candidates);
        } else {
            // Show quantity selection dialog
            NQuantitySelector selector = new NQuantitySelector(
                L10n.get("storage.select_quantity"),
                item.count,
                (selectedCount) -> startFetchBot(item, selectedCount, candidates)
            );
            NUtils.getGameUI().add(selector, NUtils.getGameUI().sz.div(2).sub(selector.sz.div(2)));
        }
    }

    private static void startFetchBot(GroupedItem item, int count, List<StorageItemDao.StorageItemData> candidates) {
        List<StorageItemDao.StorageItemData> matchingItems = candidates.stream()
            .filter(item::matches)
            .collect(Collectors.toList());

        FetchStorageItemBot bot = new FetchStorageItemBot(item, count, matchingItems);
        BotExecutor.runAsync("FetchStorageItemBot", bot);
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
