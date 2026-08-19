package nurgling.actions.bots;

import haven.*;
import static haven.OCache.posres;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NContext;
import nurgling.db.FetchStorageDbSync;
import nurgling.db.StockpileStoragePolicy;
import nurgling.db.dao.StorageItemDao;
import nurgling.db.dao.ContainerDao;
import nurgling.i18n.L10n;
import nurgling.navigation.ChunkNavManager;
import nurgling.tasks.*;
import nurgling.tools.*;
import nurgling.tools.NSearchItem;
import monitoring.ItemWatcher;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bot that fetches items from storage containers based on database information.
 * Navigates to containers and collects specified items.
 */
public class FetchStorageItemBot implements Action {

    private final String itemName;
    private final double minQuality;
    private final double maxQuality;
    private final int targetCount;
    private final List<StorageItemDao.StorageItemData> itemsToFetch;

    public static final AtomicBoolean stop = new AtomicBoolean(false);

    /**
     * Create a bot to fetch items matching the given criteria
     * @param item The grouped item to fetch
     * @param count Number of items to fetch
     * @param allItems All items from database matching this group
     */
    public FetchStorageItemBot(GroupedItem item, int count, List<StorageItemDao.StorageItemData> allItems) {
        this.itemName = item.name;
        this.minQuality = item.minQuality;
        this.maxQuality = item.maxQuality;
        this.targetCount = count;

        // Filter items that match the group criteria
        this.itemsToFetch = new ArrayList<>();
        for (StorageItemDao.StorageItemData data : allItems) {
            if (data.getName().equals(itemName)) {
                double q = data.getQuality();
                if (q >= minQuality && q <= maxQuality) {
                    itemsToFetch.add(data);
                }
            }
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        stop.set(false);

        if (itemsToFetch.isEmpty()) {
            gui.msg(L10n.get("storage.no_containers"), java.awt.Color.RED);
            return Results.FAIL();
        }

        // Group items by container
        Map<String, List<StorageItemDao.StorageItemData>> itemsByContainer = new LinkedHashMap<>();
        for (StorageItemDao.StorageItemData item : itemsToFetch) {
            itemsByContainer.computeIfAbsent(item.getContainer(), k -> new ArrayList<>()).add(item);
        }

        int collected = 0;
        int remaining = targetCount;

        gui.msg(L10n.get("storage.fetching_items").replace("{0}", String.valueOf(targetCount)));

        // Count items in player inventory before fetching
        int beforeCount = countItemsInInventory(gui, itemName);

        for (Map.Entry<String, List<StorageItemDao.StorageItemData>> entry : itemsByContainer.entrySet()) {
            if (stop.get()) {
                gui.msg(L10n.get("storage.fetch_cancelled"), java.awt.Color.YELLOW);
                return Results.FAIL();
            }

            if (remaining <= 0) break;

            String containerHash = entry.getKey();
            List<StorageItemDao.StorageItemData> containerItems = entry.getValue();
            // Pass actual DB records (limited by remaining) so we can match exact quality
            List<StorageItemDao.StorageItemData> toFetch = containerItems.subList(0, Math.min(containerItems.size(), remaining));

            // Try to find and navigate to container
            int fetched = fetchFromContainer(gui, containerHash, toFetch);

            if (fetched > 0) {
                collected += fetched;
                remaining -= fetched;
            }
        }

        // Verify actual items collected
        int afterCount = countItemsInInventory(gui, itemName);
        int actualCollected = afterCount - beforeCount;

        if (actualCollected > 0) {
            gui.msg(L10n.get("storage.fetch_complete").replace("{0}", String.valueOf(actualCollected)), java.awt.Color.GREEN);
            return Results.SUCCESS();
        } else {
            gui.msg(L10n.get("storage.container_not_found"), java.awt.Color.RED);
            return Results.FAIL();
        }
    }

    /**
     * Count items with matching name in player inventory
     */
    private int countItemsInInventory(NGameUI gui, String name) {
        try {
            return gui.getInventory().getItems(name).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Navigate to container and fetch items matching exact quality from DB records.
     * @param requestedItems DB records with exact qualities to match
     * @return number of items actually fetched
     */
    private int fetchFromContainer(NGameUI gui, String containerHash, List<StorageItemDao.StorageItemData> requestedItems) throws InterruptedException {
        // Count items before
        int beforeCount = countItemsInInventory(gui, itemName);

        // First try to find the container Gob in visible area
        Gob containerGob = Finder.findGob(containerHash);

        if (containerGob == null) {
            // Container not visible - try to find its position from database and navigate
            ContainerDao.ContainerData containerData = loadContainerData(gui, containerHash);
            if (containerData == null) {
                gui.msg("[DBG] container data not in DB for hash=" + containerHash.substring(0, 8), java.awt.Color.ORANGE);
                return 0;
            }

            // Parse local coordinates and navigate using gridId
            Coord localCoord = parseLocalCoordinates(containerData.getCoord());
            if (localCoord == null) {
                gui.msg("[DBG] bad coords: " + containerData.getCoord(), java.awt.Color.ORANGE);
                return 0;
            }

            gui.msg("[DBG] grid=" + containerData.getGridId() + " coord=" + localCoord, java.awt.Color.CYAN);

            // Navigate to container position using chunk navigation with gridId
            if (!navigateToContainer(gui, containerData.getGridId(), localCoord)) {
                gui.msg("[DBG] navigation failed to grid=" + containerData.getGridId(), java.awt.Color.ORANGE);
                return 0;
            }

            // Try to find container again after navigation (gobs may take time to load)
            containerGob = Finder.findGob(containerHash);
            if (containerGob == null) {
                WaitForGobWithHash waitGob = new WaitForGobWithHash(containerHash);
                NUtils.addTask(waitGob);
                if (!waitGob.criticalExit) {
                    containerGob = Finder.findGob(containerHash);
                }
            }
            if (containerGob == null) {
                gui.msg("[DBG] gob not found after navigation", java.awt.Color.ORANGE);
                return 0;
            }
        }

        // Approach via PF. With a hitbox, hardMode+skipDN walks around baskets
        // to a free cell beside the gob. Map objects without a hitbox (until a
        // custom box exists) must not use that — PathFinder has no cells to approach.
        PathFinder pf = new PathFinder(containerGob);
        if (containerGob.ngob != null && containerGob.ngob.hitBox != null) {
            pf.isHardMode = true;
            pf.skipDN = true;
        }
        if (!pf.run(gui).IsSuccess()) {
            gui.msg("[DBG] pathfinder failed", java.awt.Color.ORANGE);
            return 0;
        }

        if (containerGob.ngob != null && StockpileStoragePolicy.isStockpileRes(containerGob.ngob.name)) {
            return fetchFromStockpile(gui, containerGob, requestedItems);
        }

        // Open container
        String containerName = getContainerWindowName(containerGob);
        if (containerName == null) {
            gui.msg("[DBG] no window name for " + (containerGob.ngob != null ? containerGob.ngob.name : "unknown"), java.awt.Color.ORANGE);
            return 0;
        }
        NUtils.getUI().core.setLastAction(containerGob);
        new OpenTargetContainer(containerName, containerGob).run(gui);

        NInventory containerInv = gui.getInventory(containerName);
        Window containerWindow = gui.getWindow(containerName);
        if (containerInv == null || containerWindow == null) {
            if (containerWindow != null) {
                containerWindow.wdgmsg("close");
            }
            return 0;
        }

        List<StorageItemDao.StorageItemData> taken = new ArrayList<>();
        for (StorageItemDao.StorageItemData dbItem : requestedItems) {
            if (stop.get()) break;
            if (gui.getInventory().calcFreeSpace() == 0) break;

            WItem match = findItemByQuality(containerInv, dbItem.getName(), dbItem.getQuality());
            if (match == null) {
                continue;
            }
            WItem leaf = firstLeaf(match);
            if (leaf == null) {
                waitStackContents(gui);
                match = findItemByQuality(containerInv, dbItem.getName(), dbItem.getQuality());
                leaf = firstLeaf(match);
            }
            if (leaf == null) {
                continue;
            }

            int moved = TransferToContainer.transfer(leaf, gui.getInventory(), 1);
            if (moved > 0) {
                taken.add(dbItem);
            }
        }

        containerWindow.wdgmsg("close");
        NUtils.addTask(new WindowIsClosed(containerWindow));
        removeTakenFromDb(taken);

        int afterCount = countItemsInInventory(gui, itemName);
        return afterCount - beforeCount;
    }

    private int fetchFromStockpile(NGameUI gui, Gob pileGob,
                                   List<StorageItemDao.StorageItemData> requestedItems) throws InterruptedException {
        boolean oldBundle = ((NInventory) gui.maininv).bundle.a;
        NUtils.stackSwitch(true);
        try {
            return fetchFromStockpileBatch(gui, pileGob, requestedItems);
        } finally {
            NUtils.stackSwitch(oldBundle);
        }
    }

    private int fetchFromStockpileBatch(NGameUI gui, Gob pileGob,
                                        List<StorageItemDao.StorageItemData> requestedItems)
            throws InterruptedException {
        monitoring.StockpileStorageTracker.onGob(pileGob);
        String oldHash = pileGob.ngob != null ? pileGob.ngob.hash : null;
        Coord2d pileRc = pileGob.rc;

        new OpenTargetContainer("Stockpile", pileGob).run(gui);
        NISBox pile = gui.getStockpile();
        if (pile == null) {
            return 0;
        }
        int count = pile.calcCount();
        if (count <= 0) {
            closeStockpile(gui);
            return 0;
        }
        int free = gui.getInventory().getFreeSpace();
        String stackName = requestedItems.isEmpty() ? itemName : requestedItems.get(0).getName();
        int stackSize = Math.max(1, StackSupporter.getFullStackSize(stackName));
        if (StockpileStoragePolicy.takeCount(count, free, stackSize, 1) <= 0) {
            closeStockpile(gui);
            return 0;
        }

        List<StockpileStoragePolicy.Item> needed = new ArrayList<>();
        for (StorageItemDao.StorageItemData data : requestedItems) {
            needed.add(new StockpileStoragePolicy.Item(data.getName(), data.getQuality()));
        }

        List<StockpileStoragePolicy.Item> before = monitoring.StockpileStorageTracker.captureInventory();
        new TakeItemsFromPile(pileGob, pile, 1).run(gui);
        waitStackContents(gui);
        List<StockpileStoragePolicy.Item> dumped = new ArrayList<>(StockpileStoragePolicy.appeared(
                before, monitoring.StockpileStorageTracker.captureInventory()));
        StockpileStoragePolicy.Item first = dumped.isEmpty() ? null : dumped.get(0);
        StockpileStoragePolicy.ProbeAction action = StockpileStoragePolicy.probeThenDump(first, needed);
        List<StockpileStoragePolicy.Item> remainingNeeded = new ArrayList<>(needed);
        if (action == StockpileStoragePolicy.ProbeAction.KEEP_ONE && first != null) {
            remainingNeeded.remove(first);
        }
        boolean dumpMore = action == StockpileStoragePolicy.ProbeAction.DUMP_MAX
                || !remainingNeeded.isEmpty();

        if (dumpMore && !stop.get()) {
            pile = reopenStockpile(gui, pileGob);
            if (pile != null) {
                count = pile.calcCount();
                free = gui.getInventory().getFreeSpace();
                int take = StockpileStoragePolicy.takeCount(count, free, stackSize);
                if (take > 0) {
                    List<StockpileStoragePolicy.Item> beforeDump =
                            monitoring.StockpileStorageTracker.captureInventory();
                    new TakeItemsFromPile(pileGob, pile, take).run(gui);
                    waitStackContents(gui);
                    dumped.addAll(StockpileStoragePolicy.appeared(
                            beforeDump, monitoring.StockpileStorageTracker.captureInventory()));
                }
            }
        }
        closeStockpile(gui);
        monitoring.StockpileStorageTracker.flush();

        StockpileStoragePolicy.FetchSplit split = StockpileStoragePolicy.splitForFetch(dumped, needed);

        if (oldHash != null && Finder.findGob(oldHash) == null && NCore.databaseManager != null
                && NCore.databaseManager.isReady()) {
            try {
                NCore.databaseManager.getContainerService().deleteContainer(oldHash);
            } catch (Exception ignored) {
            }
        }

        if (!split.restock.isEmpty()) {
            NUtils.stackSwitch(false);
            restockStockpile(gui, pileGob, pileRc, split.restock);
            monitoring.StockpileStorageTracker.flush();
        }

        List<StorageItemDao.StorageItemData> taken = new ArrayList<>();
        List<StockpileStoragePolicy.Item> keepLeft = new ArrayList<>(split.keep);
        for (StorageItemDao.StorageItemData data : requestedItems) {
            StockpileStoragePolicy.Item fp = new StockpileStoragePolicy.Item(data.getName(), data.getQuality());
            int idx = keepLeft.indexOf(fp);
            if (idx >= 0) {
                keepLeft.remove(idx);
                taken.add(data);
            }
        }
        removeTakenFromDb(taken);

        return split.keep.size();
    }

    private static void closeStockpile(NGameUI gui) throws InterruptedException {
        if (gui.getWindow("Stockpile") != null) {
            new CloseTargetContainer("Stockpile").run(gui);
        }
    }

    private static NISBox reopenStockpile(NGameUI gui, Gob pileGob) throws InterruptedException {
        if (gui.getStockpile() == null) {
            Gob live = pileGob;
            if (pileGob != null && pileGob.ngob != null && pileGob.ngob.hash != null) {
                Gob byHash = Finder.findGob(pileGob.ngob.hash);
                if (byHash != null) {
                    live = byHash;
                }
            }
            new OpenTargetContainer("Stockpile", live).run(gui);
        }
        return gui.getStockpile();
    }

    private void restockStockpile(NGameUI gui, Gob originalPile, Coord2d rc,
                                  List<StockpileStoragePolicy.Item> restock) throws InterruptedException {
        Gob existing = existingPileAt(originalPile, rc);
        StockpileStoragePolicy.RestockPlan plan = StockpileStoragePolicy.restockPlan(
                rc.x, rc.y, existing != null);
        List<StockpileStoragePolicy.Item> left = new ArrayList<>(restock);
        Gob pile;
        if (plan.mode == StockpileStoragePolicy.RestockPlan.Mode.DROP_ON_EXISTING) {
            pile = existing;
        } else {
            WItem first = findRestockLeaf(gui, left);
            StockpileStoragePolicy.Item firstFp = fingerprint(first);
            if (first == null || firstFp == null || !takeSingleToHand(gui, first)) {
                return;
            }
            String name = firstFp.name;
            Coord2d exact = new Coord2d(plan.x, plan.y);
            PileMaker maker = new PileMaker(exact, new NAlias(name), new NAlias("stockpile"));
            if (!maker.run(gui).IsSuccess()) {
                if (gui.vhand != null) {
                    NUtils.dropToInv();
                }
                return;
            }
            pile = maker.getPile();
            if (pile == null) {
                return;
            }
            left.remove(firstFp);
        }
        monitoring.StockpileStorageTracker.onGob(pile);
        dropOntoPile(gui, pile, left);
    }

    private Gob existingPileAt(Gob original, Coord2d rc) {
        if (original == null) {
            return null;
        }
        Gob byId = Finder.findGob(original.id);
        if (byId != null && byId.ngob != null
                && StockpileStoragePolicy.isStockpileRes(byId.ngob.name)) {
            return byId;
        }
        String originalHash = original.ngob != null ? original.ngob.hash : null;
        if (originalHash != null) {
            Gob byHash = Finder.findGob(originalHash);
            if (byHash != null) {
                return byHash;
            }
        }
        return null;
    }

    private void dropOntoPile(NGameUI gui, Gob pile, List<StockpileStoragePolicy.Item> restock)
            throws InterruptedException {
        new OpenTargetContainer("Stockpile", pile).run(gui);
        NISBox box = gui.getStockpile();
        List<StockpileStoragePolicy.Item> left = new ArrayList<>(restock);
        while (!left.isEmpty() && !stop.get()) {
            WItem leaf = findRestockLeaf(gui, left);
            StockpileStoragePolicy.Item fp = fingerprint(leaf);
            if (leaf == null || fp == null) {
                break;
            }
            if (!takeSingleToHand(gui, leaf)) {
                break;
            }
            if (box != null) {
                box.wdgmsg("drop");
            } else {
                NUtils.activateItem(pile, false);
            }
            NUtils.addTask(new WaitFreeHand());
            if (gui.vhand != null) {
                NUtils.dropToInv();
                break;
            }
            left.remove(fp);
        }
        if (gui.getWindow("Stockpile") != null) {
            new CloseTargetContainer("Stockpile").run(gui);
        }
    }

    private WItem findRestockLeaf(NGameUI gui, List<StockpileStoragePolicy.Item> left)
            throws InterruptedException {
        waitStackContents(gui);
        NInventory inv = gui.getInventory();
        if (inv == null) {
            return null;
        }
        List<WItem> leaves = new ArrayList<>();
        for (WItem w : inv.getTopLevelItems()) {
            collectLeaves(w, leaves);
        }
        List<StockpileStoragePolicy.Item> fps = new ArrayList<>();
        for (WItem leaf : leaves) {
            fps.add(fingerprint(leaf));
        }
        int idx = StockpileStoragePolicy.indexOfRestockLeaf(fps, left);
        if (idx >= 0) {
            return leaves.get(idx);
        }
        return null;
    }

    private void collectLeaves(WItem w, List<WItem> out) {
        if (w == null || w.item == null) {
            return;
        }
        if (w.item.contents instanceof haven.res.ui.stackinv.ItemStack) {
            haven.res.ui.stackinv.ItemStack stack =
                    (haven.res.ui.stackinv.ItemStack) w.item.contents;
            if (stack.wmap == null) {
                return;
            }
            for (WItem child : stack.wmap.values()) {
                collectLeaves(child, out);
            }
            return;
        }
        if (!isStackLike(w)) {
            out.add(w);
        }
    }

    private void waitStackContents(NGameUI gui) throws InterruptedException {
        NInventory inv = gui.getInventory();
        if (inv == null) {
            return;
        }
        NUtils.addTask(new NTask() {
            int ticks;

            @Override
            public boolean check() {
                if (ticks++ > 80) {
                    return true;
                }
                for (WItem w : inv.getTopLevelItems()) {
                    if (w == null || w.item == null) {
                        continue;
                    }
                    int amount = stackAmount(w);
                    if (amount > 1 && !(w.item.contents instanceof haven.res.ui.stackinv.ItemStack)) {
                        return false;
                    }
                    if (w.item.contents instanceof haven.res.ui.stackinv.ItemStack) {
                        haven.res.ui.stackinv.ItemStack stack =
                                (haven.res.ui.stackinv.ItemStack) w.item.contents;
                        if (stack.wmap == null || stack.wmap.isEmpty()) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
    }

    private boolean takeSingleToHand(NGameUI gui, WItem item) throws InterruptedException {
        if (gui.vhand != null) {
            NUtils.dropToInv();
        }
        WItem leaf = firstLeaf(item);
        if (leaf == null || leaf.parent == null
                || !StockpileStoragePolicy.isPuttableInStockpile(isStackLike(leaf))) {
            return false;
        }
        if (NUtils.takeItemToHand(leaf) == null) {
            return false;
        }
        if (gui.vhand != null && isStackLike(gui.vhand)) {
            NUtils.dropToInv();
            return false;
        }
        return gui.vhand != null;
    }

    private static WItem firstLeaf(WItem w) {
        if (w == null || w.item == null) {
            return null;
        }
        if (w.item.contents instanceof haven.res.ui.stackinv.ItemStack) {
            haven.res.ui.stackinv.ItemStack stack = (haven.res.ui.stackinv.ItemStack) w.item.contents;
            if (stack.wmap == null) {
                return null;
            }
            for (WItem child : stack.wmap.values()) {
                WItem inner = firstLeaf(child);
                if (inner != null) {
                    return inner;
                }
            }
            return null;
        }
        return isStackLike(w) ? null : w;
    }

    private static boolean isStackShell(WItem w) {
        return w != null && w.item != null && w.item.contents instanceof haven.res.ui.stackinv.ItemStack;
    }

    private static boolean isStackLike(WItem w) {
        return StockpileStoragePolicy.isStackLike(isStackShell(w), stackAmount(w));
    }

    private static int stackAmount(WItem w) {
        if (w == null || !(w.item instanceof NGItem)) {
            return 1;
        }
        GItem.Amount amt = ((NGItem) w.item).getInfo(GItem.Amount.class);
        return amt != null ? amt.itemnum() : 1;
    }

    private static StockpileStoragePolicy.Item fingerprint(WItem w) {
        if (w == null || !(w.item instanceof NGItem) || isStackLike(w)) {
            return null;
        }
        NGItem g = (NGItem) w.item;
        if (g.name() == null) {
            return null;
        }
        double q = g.quality == null || g.quality <= 0 ? 0
                : Double.parseDouble(Utils.odformat2(g.quality, 2));
        return new StockpileStoragePolicy.Item(g.name(), q);
    }

    /**
     * Find a WItem in the container inventory matching the given name and exact quality.
     * Quality is compared rounded to 2 decimal places (same precision as DB storage).
     * @return matching WItem or null if not found
     */
    private WItem findItemByQuality(NInventory containerInv, String name, double targetQuality) throws InterruptedException {
        ArrayList<WItem> items = containerInv.getItems(name);
        for (WItem item : items) {
            if (item.item instanceof NGItem) {
                Float q = ((NGItem) item.item).quality;
                if (q != null) {
                    double rounded = Double.parseDouble(Utils.odformat2(q, 2));
                    if (Double.compare(rounded, targetQuality) == 0) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Load container data from database
     */
    private ContainerDao.ContainerData loadContainerData(NGameUI gui, String containerHash) {
        if (NCore.databaseManager == null || !NCore.databaseManager.isReady()) {
            return null;
        }

        try {
            return NCore.databaseManager.executeOperation(adapter -> {
                ContainerDao dao = new ContainerDao();
                List<ContainerDao.ContainerData> containers = dao.loadAllContainers(adapter);
                for (ContainerDao.ContainerData c : containers) {
                    if (c.getHash().equals(containerHash)) {
                        return c;
                    }
                }
                return null;
            });
        } catch (Exception e) {
            return null;
        }
    }

    private void removeTakenFromDb(List<StorageItemDao.StorageItemData> taken) {
        List<String> hashes = FetchStorageDbSync.hashesToDelete(taken);
        if (hashes.isEmpty() || NCore.databaseManager == null || !NCore.databaseManager.isReady()) {
            return;
        }
        try {
            NCore.databaseManager.executeOperation(adapter -> {
                StorageItemDao dao = new StorageItemDao();
                for (String hash : hashes) {
                    dao.deleteStorageItem(adapter, hash);
                }
                return null;
            });
            String container = taken.get(0).getContainer();
            if (container != null) {
                ItemWatcher.invalidateContainerCache(container);
            }
            NSearchItem.notifyContainerDataChanged();
        } catch (Exception ignored) {
        }
    }

    /**
     * Parse local coordinates string "(x, y)" to Coord (in posres units, as stored by ContainerWatcher)
     */
    private Coord parseLocalCoordinates(String coords) {
        if (coords == null) return null;
        try {
            // Format: "(x, y)" or "(x,y)"
            String clean = coords.replace("(", "").replace(")", "").replace(" ", "");
            String[] parts = clean.split(",");
            if (parts.length == 2) {
                int x = (int) Double.parseDouble(parts[0]);
                int y = (int) Double.parseDouble(parts[1]);
                return new Coord(x, y);
            }
        } catch (Exception e) {
            // Parse error
        }
        return null;
    }

    /**
     * Navigate to a container using gridId + local coordinates via chunk navigation.
     * Uses planToGridCoord for cross-layer navigation.
     */
    private boolean navigateToContainer(NGameUI gui, long gridId, Coord localCoord) throws InterruptedException {
        ChunkNavManager chunkNav = ((NMapView) gui.map).getChunkNavManager();
        if (chunkNav == null || !chunkNav.isInitialized()) {
            return false;
        }

        // Check if the grid is currently loaded (container might be nearby on the same layer)
        boolean gridLoaded = false;
        try {
            MCache mcache = gui.map.glob.map;
            MCache.Grid grid = null;
            synchronized (mcache.grids) {
                for (MCache.Grid g : mcache.grids.values()) {
                    if (g.id == gridId) {
                        grid = g;
                        break;
                    }
                }
            }
            if (grid != null) {
                gridLoaded = true;
                // Grid is loaded - try local pathfinding first (works if same layer/instance)
                Coord2d gridOrigin = new Coord2d(grid.ul.x * MCache.tilesz.x, grid.ul.y * MCache.tilesz.y);
                Coord2d worldPos = gridOrigin.add(new Coord2d(localCoord.x * posres.x, localCoord.y * posres.y));
                if (new PathFinder(worldPos).run(gui).IsSuccess()) {
                    return true;
                }
                gui.msg("[DBG] grid loaded but PF failed, trying ChunkNav", java.awt.Color.YELLOW);
            }
        } catch (Exception e) {
            // Fall through to chunk navigation
        }

        // Use chunk navigation (handles cross-layer portal traversal)
        Coord2d offsetWorld = new Coord2d(localCoord.x * posres.x, localCoord.y * posres.y);
        Coord tileCoord = offsetWorld.floor(MCache.tilesz);

        // Check if ChunkNav even knows this grid
        boolean chunkExists = chunkNav.getGraph().getChunk(gridId) != null;
        gui.msg("[DBG] gridLoaded=" + gridLoaded + " chunkInGraph=" + chunkExists + " tile=" + tileCoord, java.awt.Color.CYAN);

        nurgling.navigation.ChunkPath path = chunkNav.planToGridCoord(gridId, tileCoord);
        if (path != null) {
            gui.msg("[DBG] path found, segments=" + path.segments.size(), java.awt.Color.GREEN);
            return chunkNav.navigateWithPath(path, null, gui).IsSuccess();
        }

        gui.msg("[DBG] planToGridCoord returned null", java.awt.Color.RED);
        return false;
    }

    /**
     * Get the window name for a container Gob using the canonical NContext.contcaps mapping.
     * Returns null if the container type is unknown.
     */
    private String getContainerWindowName(Gob gob) {
        if (gob.ngob == null || gob.ngob.name == null) {
            return null;
        }
        return NContext.contcaps.get(gob.ngob.name);
    }
}
