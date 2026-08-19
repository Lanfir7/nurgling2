package monitoring;

import haven.ClickData;
import haven.Coord;
import haven.Coord2d;
import haven.GItem;
import haven.Gob;
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
import nurgling.db.dao.StorageItemDao;
import nurgling.tools.ClaimLand;
import nurgling.tools.Finder;
import nurgling.tools.NSearchItem;

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
            if (sessionGob != null && sessionGob != gob) {
                commitUnlocked();
            }
            if (sessionGob != gob) {
                sessionGob = gob;
                snapshot = captureInventory();
                lastSeen = snapshot;
                lastChangeMs = System.currentTimeMillis();
            }
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.ui != null && gui.ui.core != null) {
            gui.ui.core.writeContainerInfo(gob);
        }
    }

    public static void onPlace(Gob placing) {
        if (!enabled() || placing == null || placing.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(placing.ngob.name)) {
            return;
        }
        synchronized (lock) {
            commitUnlocked();
            sessionGob = null;
            pendingPlaceRc = placing.rc;
            snapshot = captureInventory();
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
                    Gob found = Finder.findGob(pendingPlaceRc);
                    if (found != null && found.ngob != null
                            && StockpileStoragePolicy.isStockpileRes(found.ngob.name)) {
                        sessionGob = found;
                        pendingPlaceRc = null;
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
                return;
            }
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
        }
        items.sort(Comparator.comparing((StockpileStoragePolicy.Item i) -> i.name == null ? "" : i.name)
                .thenComparingDouble(i -> i.quality));
        return items;
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

    private static void commitUnlocked() {
        if (sessionGob == null || snapshot == null) {
            pendingPlaceRc = null;
            sessionGob = null;
            snapshot = null;
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
        snapshot = now;
        lastSeen = now;
        lastChangeMs = System.currentTimeMillis();
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
        db.submitTask(() -> applyDelta(db, hash, gone, gained));
    }

    private static void applyDelta(DatabaseManager db, String containerHash,
                                   List<StockpileStoragePolicy.Item> gone,
                                   List<StockpileStoragePolicy.Item> gained) {
        try {
            db.executeOperation(adapter -> {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
