package monitoring;

import haven.ClickData;
import haven.Coord;
import haven.Coord2d;
import haven.GItem;
import haven.Gob;
import haven.MapView;
import haven.Utils;
import haven.WItem;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.db.StockpileStoragePolicy;
import nurgling.db.dao.ContainerDao;
import nurgling.db.dao.StorageItemDao;
import nurgling.tools.ClaimLand;
import nurgling.tools.NSearchItem;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attributes inventory deltas to a stockpile gob after itemact/place/ISBox
 * interaction, then writes known items into storageitems.
 */
public final class StockpileStorageTracker {
    private static final long SETTLE_MS = 400;
    private static final long SESSION_IDLE_MS = 2000;

    private static final Object lock = new Object();
    private static Gob sessionGob;
    private static List<StockpileStoragePolicy.Item> snapshot;
    private static List<StockpileStoragePolicy.Item> lastSeen;
    private static long lastChangeMs;
    private static Coord2d pendingPlaceRc;
    private static final AtomicInteger seq = new AtomicInteger();
    private static List<StockpileStoragePolicy.Item> lastVhand = List.of();
    private static List<StockpileStoragePolicy.Item> placingHeld = List.of();
    private static List<StockpileStoragePolicy.Item> pendingSeed = List.of();
    /** Hand contents frozen at ghost start; written to DB when the new pile appears. */
    private static List<StockpileStoragePolicy.Item> frozenHand = List.of();
    /** True from ghost/place until frozen hand is written to the NEW pile only. */
    private static boolean awaitingPlaceInsert;

    private StockpileStorageTracker() {}

    public static void onClickData(ClickData inf) {
        Gob gob = gobFromClick(inf);
        if (gob != null) {
            onGob(gob);
        }
    }

    public static void onGob(Gob gob) {
        if (!enabled() || gob == null || gob.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(gob.ngob.name)) {
            return;
        }
        if (!ClaimLand.isOnClaimOrVillage(gob)) {
            return;
        }
        synchronized (lock) {
            if (awaitingPlaceInsert || pendingPlaceRc != null) {
                return;
            }
            List<StockpileStoragePolicy.Item> now = captureInventory();
            if (sessionGob != null && sessionGob != gob) {
                commitUnlocked();
            }
            if (sessionGob != gob) {
                sessionGob = gob;
                snapshot = now;
                lastSeen = snapshot;
                lastChangeMs = System.currentTimeMillis();
            }
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.ui != null && gui.ui.core != null) {
            gui.ui.core.writeContainerInfo(gob);
        }
    }

    /**
     * Extend the current pile session without replacing the inventory snapshot.
     * Used for ISBox click/wheel/drop/chnum so a burst of transfers settles as one delta.
     */
    public static void touch(Gob gob) {
        if (!enabled()) {
            return;
        }
        if (gob != null && pendingPlaceRc == null && !awaitingPlaceInsert) {
            onGob(gob);
        }
        synchronized (lock) {
            lastChangeMs = System.currentTimeMillis();
        }
    }

    public static void rememberHand(WItem hand) {
        if (hand == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> items = new ArrayList<>();
        addCaptured(items, hand);
        lastVhand = StockpileStoragePolicy.keepLastHand(lastVhand, items);
    }

    public static void onPlacingStart(String resName) {
        if (!enabled()) {
            return;
        }
        if (resName != null && !StockpileStoragePolicy.isStockpileRes(resName)) {
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.vhand != null) {
            rememberHand(gui.vhand);
        }
        placingHeld = lastVhand;
        synchronized (lock) {
            frozenHand = StockpileStoragePolicy.freezeHandForGhost(lastVhand, frozenHand);
            pendingSeed = frozenHand;
            awaitingPlaceInsert = !frozenHand.isEmpty();
        }
    }

    public static void onPlacingCancel() {
        placingHeld = List.of();
        synchronized (lock) {
            if (pendingPlaceRc == null) {
                pendingSeed = List.of();
                frozenHand = List.of();
                awaitingPlaceInsert = false;
            }
        }
    }

    public static void onPlace(Gob placing) {
        if (!enabled() || placing == null || placing.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(placing.ngob.name)) {
            return;
        }
        synchronized (lock) {
            if (sessionGob != null) {
                commitUnlocked();
            }
            sessionGob = null;
            pendingPlaceRc = placing.rc;
            frozenHand = StockpileStoragePolicy.freezeHandForGhost(lastVhand, frozenHand);
            pendingSeed = frozenHand;
            awaitingPlaceInsert = !frozenHand.isEmpty();
            snapshot = StockpileStoragePolicy.mergePendingPlace(captureInventory(), frozenHand);
            lastSeen = snapshot;
            lastChangeMs = System.currentTimeMillis();
        }
    }

    public static void tick(DatabaseManager databaseManager) {
        if (!enabled() || databaseManager == null || !databaseManager.isReady()) {
            return;
        }
        synchronized (lock) {
            if (pendingPlaceRc != null) {
                try {
                    Gob found = findPlacedPile(pendingPlaceRc);
                    if (found != null && found.ngob != null
                            && StockpileStoragePolicy.isStockpileRes(found.ngob.name)) {
                        sessionGob = found;
                        pendingPlaceRc = null;
                        insertFrozenHandUnlocked();
                        if (!awaitingPlaceInsert || snapshot == null) {
                            snapshot = captureInventory();
                            lastSeen = snapshot;
                        }
                        lastChangeMs = System.currentTimeMillis();
                        NGameUI gui = NUtils.getGameUI();
                        if (gui != null && gui.ui != null && gui.ui.core != null) {
                            gui.ui.core.writeContainerInfo(found);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (sessionGob == null || snapshot == null) {
                insertFrozenHandUnlocked();
                return;
            }
            insertFrozenHandUnlocked();
            List<StockpileStoragePolicy.Item> now = captureInventory();
            if (lastSeen == null || !now.equals(lastSeen)) {
                lastChangeMs = System.currentTimeMillis();
                lastSeen = now;
            }
            if (!now.equals(snapshot) && System.currentTimeMillis() - lastChangeMs >= SETTLE_MS) {
                commitUnlocked();
            }
            NGameUI gui = NUtils.getGameUI();
            boolean pileUiOpen = gui != null && gui.getStockpile() != null;
            if (!pileUiOpen && pendingPlaceRc == null
                    && pendingSeed.isEmpty() && frozenHand.isEmpty() && !awaitingPlaceInsert
                    && System.currentTimeMillis() - lastChangeMs >= SESSION_IDLE_MS) {
                sessionGob = null;
                snapshot = null;
                lastSeen = null;
            }
        }
    }

    public static void flush() {
        synchronized (lock) {
            commitUnlocked();
        }
    }

    public static List<StockpileStoragePolicy.Item> captureInventory() {
        List<StockpileStoragePolicy.Item> items = new ArrayList<>();
        NGameUI gui = NUtils.getGameUI();
        if (gui == null) {
            return items;
        }
        NInventory inv = gui.getInventory();
        if (inv != null) {
            for (WItem w : inv.getTopLevelItems()) {
                addCaptured(items, w);
            }
        }
        if (gui.vhand != null) {
            addCaptured(items, gui.vhand);
            rememberHand(gui.vhand);
        }
        boolean placing = isPlacingStockpile(gui);
        if (frozenHand.isEmpty()) {
            items = new ArrayList<>(StockpileStoragePolicy.withPlacingHeld(
                    items, placingHeld, gui.vhand != null, placing));
        }
        items.sort(Comparator.comparing((StockpileStoragePolicy.Item i) -> i.name == null ? "" : i.name)
                .thenComparingDouble(i -> i.quality));
        return items;
    }

    private static boolean isPlacingStockpile(NGameUI gui) {
        if (gui == null || gui.map == null || gui.map.placing == null || !gui.map.placing.ready()) {
            return false;
        }
        try {
            Gob plob = gui.map.placing.get();
            return plob != null && plob.ngob != null
                    && StockpileStoragePolicy.isStockpileRes(plob.ngob.name);
        } catch (Exception e) {
            return false;
        }
    }

    private static void addCaptured(List<StockpileStoragePolicy.Item> items, WItem w) {
        if (w == null || !(w.item instanceof NGItem)) {
            return;
        }
        NGItem g = (NGItem) w.item;
        List<StockpileStoragePolicy.Item> contents = collectContents(g);
        int amount = 1;
        GItem.Amount amt = g.getInfo(GItem.Amount.class);
        if (amt != null && amt.itemnum() > 1) {
            amount = amt.itemnum();
        }
        items.addAll(StockpileStoragePolicy.expandSlot(g.name(), roundQ(g.quality), amount, contents));
    }

    private static List<StockpileStoragePolicy.Item> collectContents(NGItem stack) {
        List<StockpileStoragePolicy.Item> inner = new ArrayList<>();
        if (stack.contents != null) {
            collectWidgets(stack.contents.child, inner);
        }
        return inner;
    }

    private static void collectWidgets(Widget first, List<StockpileStoragePolicy.Item> out) {
        for (Widget widget = first; widget != null; widget = widget.next) {
            if (widget instanceof WItem) {
                addCaptured(out, (WItem) widget);
            }
            if (widget.child != null) {
                collectWidgets(widget.child, out);
            }
        }
    }

    private static Gob findPlacedPile(Coord2d rc) {
        if (rc == null) {
            return null;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return null;
        }
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob == null || gob.rc == null || gob instanceof MapView.Plob || gob.id <= 0) {
                    continue;
                }
                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }
                if (StockpileStoragePolicy.isPlacedPileAt(
                        gob.ngob.name, rc.x, rc.y, gob.rc.x, gob.rc.y)) {
                    return gob;
                }
            }
        }
        return null;
    }

    private static Gob gobFromClick(ClickData inf) {
        if (inf == null || !(inf.ci instanceof Gob.GobClick)) {
            return null;
        }
        return ((Gob.GobClick) inf.ci).gob;
    }

    private static boolean enabled() {
        return Boolean.TRUE.equals(NConfig.get(NConfig.Key.ndbenable));
    }

    private static double roundQ(Float q) {
        if (q == null || q <= 0) {
            return 0;
        }
        return Double.parseDouble(Utils.odformat2(q, 2));
    }

    private static void insertFrozenHandUnlocked() {
        if (!awaitingPlaceInsert) {
            return;
        }
        if (sessionGob == null || sessionGob.ngob == null || sessionGob.ngob.hash == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> toInsert =
                StockpileStoragePolicy.itemsToInsertOnNewPile(frozenHand, true);
        if (toInsert.isEmpty()) {
            awaitingPlaceInsert = false;
            return;
        }
        String hash = sessionGob.ngob.hash;
        long gridId = sessionGob.ngob.grid_id;
        Coord gcoord = sessionGob.ngob.gcoord;
        DatabaseManager db = nurgling.NCore.databaseManager;
        if (db == null || !db.isReady()) {
            return;
        }
        frozenHand = List.of();
        pendingSeed = List.of();
        awaitingPlaceInsert = false;
        snapshot = captureInventory();
        lastSeen = snapshot;
        db.submitTask(() -> applyDelta(db, hash, gridId, gcoord, toInsert, List.of()));
    }

    private static void commitUnlocked() {
        if (sessionGob == null || snapshot == null) {
            return;
        }
        if (sessionGob.ngob == null || sessionGob.ngob.hash == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> now = captureInventory();
        List<StockpileStoragePolicy.Item> gone = StockpileStoragePolicy.disappeared(snapshot, now);
        List<StockpileStoragePolicy.Item> gained = StockpileStoragePolicy.appeared(snapshot, now);
        Gob gob = sessionGob;
        String hash = gob.ngob.hash;
        long gridId = gob.ngob.grid_id;
        Coord gcoord = gob.ngob.gcoord;
        snapshot = now;
        lastSeen = now;
        lastChangeMs = System.currentTimeMillis();
        if (!pendingSeed.isEmpty() && !gone.isEmpty()) {
            pendingSeed = StockpileStoragePolicy.disappeared(pendingSeed, gone);
        }
        if (gone.isEmpty() && gained.isEmpty()) {
            return;
        }
        if (StockpileStoragePolicy.isStackResolution(gone, gained)) {
            return;
        }
        DatabaseManager db = nurgling.NCore.databaseManager;
        if (db == null || !db.isReady()) {
            return;
        }
        db.submitTask(() -> applyDelta(db, hash, gridId, gcoord, gone, gained));
    }

    private static void applyDelta(DatabaseManager db, String containerHash, long gridId, Coord gcoord,
                                   List<StockpileStoragePolicy.Item> gone,
                                   List<StockpileStoragePolicy.Item> gained) {
        try {
            if (db == null || !db.isReady()) {
                return;
            }
            db.executeOperation(adapter -> {
                if (containerHash != null && gcoord != null) {
                    new ContainerDao().saveContainer(adapter, containerHash, gridId, gcoord.toString());
                }
                StorageItemDao dao = new StorageItemDao();
                for (StockpileStoragePolicy.Item item : gone) {
                    int n = seq.incrementAndGet();
                    String itemHash = NUtils.calculateSHA256(
                            item.name + item.quality + containerHash + "_" + n);
                    dao.saveStorageItem(adapter, itemHash, item.name, item.quality,
                            Coord.of(n, 0).toString(), containerHash);
                }
                if (!gained.isEmpty()) {
                    List<StorageItemDao.StorageItemData> existing =
                            dao.loadStorageItemsByContainer(adapter, containerHash);
                    for (StockpileStoragePolicy.Item item : gained) {
                        StorageItemDao.StorageItemData match = null;
                        for (StorageItemDao.StorageItemData row : existing) {
                            if (item.name.equals(row.getName())
                                    && Double.compare(row.getQuality(), item.quality) == 0) {
                                match = row;
                                break;
                            }
                        }
                        if (match != null) {
                            dao.deleteStorageItem(adapter, match.getItemHash());
                            existing.remove(match);
                        }
                    }
                }
                return null;
            });
            ItemWatcher.invalidateContainerCache(containerHash);
            NGlobalSearchItems.clearQueryCache();
            NSearchItem.notifyContainerDataChanged();
        } catch (SQLException e) {
            if (db != null && db.isReady()) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            if (db != null && db.isReady()) {
                e.printStackTrace();
            }
        }
    }
}
