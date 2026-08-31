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
import nurgling.NISBox;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.db.StockpileStoragePolicy;
import nurgling.db.dao.ContainerDao;
import nurgling.db.dao.StorageItemDao;
import nurgling.tools.ClaimLand;
import nurgling.tools.NSearchItem;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attributes inventory deltas to a stockpile gob after itemact/place/ISBox
 * interaction, then writes known items into storageitems.
 */
public final class StockpileStorageTracker {
    private static final long SETTLE_MS = 400;
    private static final long SESSION_IDLE_MS = 2000;
    private static final long PLACEMENT_HAND_MAX_AGE_MS = 30_000;
    private static final long PLACEMENT_TIMEOUT_MS = 15_000;
    private static final long PASSIVE_HISTORY_MS = 2000;
    private static final int MAX_PASSIVE_TRANSITIONS = 32;

    private static final Object lock = new Object();
    private static Gob sessionGob;
    private static List<StockpileStoragePolicy.Item> snapshot;
    private static List<StockpileStoragePolicy.Item> lastSeen;
    private static long lastChangeMs;
    private static Coord2d pendingPlaceRc;
    private static Set<Long> preexistingPlacePileIds = Set.of();
    private static String pendingPlaceResName;
    private static long placementDeadlineMs;
    private static final AtomicInteger seq = new AtomicInteger();
    private static List<StockpileStoragePolicy.Item> lastVhand = List.of();
    private static List<StockpileStoragePolicy.Item> armedPlacementHand = List.of();
    private static long armedPlacementHandAtMs;
    private static List<StockpileStoragePolicy.Item> placingHeld = List.of();
    private static List<StockpileStoragePolicy.Item> pendingSeed = List.of();
    /** Hand contents frozen at ghost start; written to DB when the new pile appears. */
    private static List<StockpileStoragePolicy.Item> frozenHand = List.of();
    /** True from ghost/place until frozen hand is written to the NEW pile only. */
    private static boolean awaitingPlaceInsert;
    private static StockpileStoragePolicy.TransferDirection transferDirection;
    private static String expectedItemName;
    private static Integer pileCountBefore;
    private static Integer pileCountAfter;
    private static boolean pileCountObserved;
    private static final Deque<InventoryTransition> passiveTransitions = new ArrayDeque<>();

    private static final class InventoryTransition {
        final List<StockpileStoragePolicy.Item> before;
        final List<StockpileStoragePolicy.Item> after;
        final long changedAtMs;

        InventoryTransition(List<StockpileStoragePolicy.Item> before,
                            List<StockpileStoragePolicy.Item> after,
                            long changedAtMs) {
            this.before = before;
            this.after = after;
            this.changedAtMs = changedAtMs;
        }
    }

    private StockpileStorageTracker() {}

    public static void onClickData(ClickData inf) {
        Gob gob = gobFromClick(inf);
        NGameUI gui = NUtils.getGameUI();
        if (gob != null && gui != null) {
            onGobItemAct(gob, gui.vhand);
        }
    }

    /** Explicit itemact on a pile. Shift-itemact is covered because the snapshot includes inventory. */
    public static void onGobItemAct(Gob gob, WItem hand) {
        List<StockpileStoragePolicy.Item> held = new ArrayList<>();
        addCaptured(held, hand);
        if (held.isEmpty()) {
            return;
        }
        beginTransfer(gob, held.get(0).name,
                StockpileStoragePolicy.TransferDirection.INTO_PILE, null);
    }

    public static void onGob(Gob gob) {
        if (!enabled() || gob == null || gob.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(gob.ngob.name)) {
            return;
        }
        if (!ClaimLand.isOnClaimOrVillage(gob)) {
            return;
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
        synchronized (lock) {
            if (snapshot != null && gob != null && gob == sessionGob) {
                lastChangeMs = System.currentTimeMillis();
            }
        }
    }

    public static void beginTransfer(Gob gob, String itemName,
                                     StockpileStoragePolicy.TransferDirection direction,
                                     Integer currentPileCount) {
        if (!enabled() || gob == null || gob.ngob == null || itemName == null || direction == null
                || !StockpileStoragePolicy.isStockpileRes(gob.ngob.name)
                || !ClaimLand.isOnClaimOrVillage(gob)) {
            return;
        }
        synchronized (lock) {
            long nowMs = System.currentTimeMillis();
            if (!StockpileStoragePolicy.canReplacePlacementSession(placementDeadlineMs, nowMs)) {
                return;
            }
            if (placementDeadlineMs > 0) {
                clearExpiredPlacementUnlocked();
            }
            if (snapshot != null && sessionGob == gob && direction == transferDirection
                    && itemName.equals(expectedItemName)) {
                if (pileCountBefore == null && currentPileCount != null) {
                    pileCountBefore = currentPileCount;
                    pileCountAfter = currentPileCount;
                }
                lastChangeMs = System.currentTimeMillis();
                return;
            }
            commitUnlocked();
            sessionGob = gob;
            snapshot = captureInventory();
            lastSeen = snapshot;
            transferDirection = direction;
            expectedItemName = itemName;
            pileCountBefore = currentPileCount;
            pileCountAfter = currentPileCount;
            pileCountObserved = false;
            lastChangeMs = System.currentTimeMillis();
        }
        onGob(gob);
    }

    /**
     * Keep a pre-transfer baseline while a stockpile window is open. Inventory-side transfers
     * (wheel, transfer shortcuts and macros) never call the ISBox destination, so chnum is the
     * first explicit signal for those operations.
     */
    public static void observeOpenPile(Gob gob, String itemName, int currentPileCount) {
        if (!enabled() || gob == null || gob.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(gob.ngob.name)
                || !ClaimLand.isOnClaimOrVillage(gob)) {
            return;
        }
        synchronized (lock) {
            long nowMs = System.currentTimeMillis();
            if (!StockpileStoragePolicy.canReplacePlacementSession(placementDeadlineMs, nowMs)) {
                return;
            }
            if (placementDeadlineMs > 0) {
                clearExpiredPlacementUnlocked();
            }
            if (pendingPlaceRc != null || awaitingPlaceInsert) {
                return;
            }
            if (snapshot != null && sessionGob == gob) {
                if (expectedItemName == null && itemName != null) {
                    expectedItemName = itemName;
                    selectCorrelatedBaselineUnlocked();
                    lastChangeMs = System.currentTimeMillis();
                }
                return;
            }
            if (sessionGob != null) {
                commitUnlocked();
            }
            sessionGob = gob;
            snapshot = captureInventory();
            lastSeen = snapshot;
            transferDirection = null;
            expectedItemName = itemName;
            pileCountBefore = currentPileCount;
            pileCountAfter = currentPileCount;
            pileCountObserved = false;
            passiveTransitions.clear();
            lastChangeMs = System.currentTimeMillis();
        }
        onGob(gob);
    }

    public static void onPileCountChanged(Gob gob, int oldCount, int newCount) {
        synchronized (lock) {
            if (snapshot == null || gob == null || gob != sessionGob) {
                return;
            }
            StockpileStoragePolicy.TransferDirection observedDirection =
                    StockpileStoragePolicy.directionFromPileCounts(oldCount, newCount);
            if (observedDirection == null) {
                pileCountAfter = newCount;
                return;
            }
            if (transferDirection == null) {
                snapshot = lastSeen;
                transferDirection = observedDirection;
                pileCountBefore = oldCount;
            }
            if (pileCountBefore == null) {
                pileCountBefore = oldCount;
            }
            pileCountAfter = newCount;
            pileCountObserved = true;
            if (transferDirection == observedDirection && pileCountBefore != null) {
                selectCorrelatedBaselineUnlocked();
            }
            lastChangeMs = System.currentTimeMillis();
        }
    }

    public static void rememberHand(WItem hand) {
        if (hand == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> items = new ArrayList<>();
        addCaptured(items, hand);
        synchronized (lock) {
            lastVhand = StockpileStoragePolicy.keepLastHand(lastVhand, items);
        }
    }

    /** Arms a cursor capture specifically for the itemact -> stockpile placement sequence. */
    public static void armPlacementHand(WItem hand) {
        if (hand == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> items = new ArrayList<>();
        addCaptured(items, hand);
        if (items.isEmpty()) {
            return;
        }
        synchronized (lock) {
            lastVhand = StockpileStoragePolicy.keepLastHand(lastVhand, items);
            armedPlacementHand = new ArrayList<>(lastVhand);
            armedPlacementHandAtMs = System.currentTimeMillis();
        }
    }

    public static void onPlacingStart(String resName) {
        if (!enabled()) {
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        List<StockpileStoragePolicy.Item> currentHand = new ArrayList<>();
        if (gui != null && gui.vhand != null) {
            addCaptured(currentHand, gui.vhand);
            rememberHand(gui.vhand);
        }
        synchronized (lock) {
            long nowMs = System.currentTimeMillis();
            if (!StockpileStoragePolicy.canReplacePlacementSession(placementDeadlineMs, nowMs)) {
                return;
            }
            if (placementDeadlineMs > 0) {
                clearExpiredPlacementUnlocked();
            }
            placingHeld = StockpileStoragePolicy.placementSeedForResource(
                    resName, currentHand, armedPlacementHand, armedPlacementHandAtMs,
                    nowMs, PLACEMENT_HAND_MAX_AGE_MS);
            armedPlacementHand = List.of();
            armedPlacementHandAtMs = 0;
            frozenHand = StockpileStoragePolicy.freezeHandForGhost(placingHeld, List.of());
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
                preexistingPlacePileIds = Set.of();
                pendingPlaceResName = null;
                placementDeadlineMs = 0;
                armedPlacementHand = List.of();
                armedPlacementHandAtMs = 0;
            }
        }
    }

    public static void onPlace(Gob placing) {
        if (!enabled() || placing == null || placing.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(placing.ngob.name)) {
            return;
        }
        synchronized (lock) {
            long nowMs = System.currentTimeMillis();
            if (!StockpileStoragePolicy.canReplacePlacementSession(placementDeadlineMs, nowMs)) {
                return;
            }
            if (placementDeadlineMs > 0) {
                clearExpiredPlacementUnlocked();
            }
            List<StockpileStoragePolicy.Item> placeSeed = StockpileStoragePolicy.placementSeedAtPlace(
                    placing.ngob.name,
                    StockpileStoragePolicy.freezeHandForGhost(placingHeld, frozenHand),
                    armedPlacementHand, armedPlacementHandAtMs,
                    nowMs, PLACEMENT_HAND_MAX_AGE_MS);
            if (sessionGob != null) {
                commitUnlocked();
            }
            sessionGob = null;
            pendingPlaceRc = placing.rc;
            pendingPlaceResName = placing.ngob.name;
            placementDeadlineMs = nowMs + PLACEMENT_TIMEOUT_MS;
            preexistingPlacePileIds = stockpileIdsAt(placing.rc);
            frozenHand = placeSeed;
            pendingSeed = frozenHand;
            awaitingPlaceInsert = !frozenHand.isEmpty();
            snapshot = StockpileStoragePolicy.mergeConsumedPlacementSeed(captureInventory(), frozenHand);
            lastSeen = snapshot;
            lastChangeMs = System.currentTimeMillis();
        }
    }

    public static void tick(DatabaseManager databaseManager) {
        if (!enabled() || databaseManager == null || !databaseManager.isReady()) {
            return;
        }
        NGameUI currentGui = NUtils.getGameUI();
        NISBox openPile = currentGui == null ? null : currentGui.getStockpile();
        if (openPile != null && openPile.parentGob != null) {
            observeOpenPile(openPile.parentGob, openPile.stockpileItemName(), openPile.stockpileCount());
        }
        synchronized (lock) {
            long nowMs = System.currentTimeMillis();
            if (placementDeadlineMs > 0
                    && !StockpileStoragePolicy.placementDeadlineActive(placementDeadlineMs, nowMs)) {
                clearExpiredPlacementUnlocked();
                return;
            }
            if (pendingPlaceRc != null) {
                try {
                    Gob found = findPlacedPile(pendingPlaceRc);
                    if (found != null && found.ngob != null
                            && StockpileStoragePolicy.isStockpileRes(found.ngob.name)) {
                        sessionGob = found;
                        pendingPlaceRc = null;
                        if (openPile != null) {
                            openPile.parentGob = found;
                        }
                        if (awaitingPlaceInsert && !frozenHand.isEmpty()) {
                            expectedItemName = frozenHand.get(0).name;
                            transferDirection = StockpileStoragePolicy.TransferDirection.INTO_PILE;
                            pileCountBefore = null;
                            pileCountAfter = null;
                            pileCountObserved = false;
                            frozenHand = List.of();
                            pendingSeed = List.of();
                            awaitingPlaceInsert = false;
                            lastVhand = List.of();
                            placingHeld = List.of();
                            armedPlacementHand = List.of();
                            armedPlacementHandAtMs = 0;
                        } else {
                            clearExpiredPlacementUnlocked();
                        }
                        preexistingPlacePileIds = Set.of();
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
            boolean placementMetadataPending = placementDeadlineMs > 0
                    && (sessionGob.ngob == null || sessionGob.ngob.hash == null
                    || sessionGob.ngob.gcoord == null);
            if (placementMetadataPending) {
                return;
            }
            List<StockpileStoragePolicy.Item> now = captureInventory();
            nowMs = System.currentTimeMillis();
            if (lastSeen == null || !now.equals(lastSeen)) {
                if (transferDirection == null && lastSeen != null) {
                    rememberPassiveTransitionUnlocked(lastSeen, now, nowMs);
                }
                lastChangeMs = nowMs;
                lastSeen = now;
            }
            prunePassiveTransitionsUnlocked(nowMs);
            long idleMs = nowMs - lastChangeMs;
            if (transferDirection == null) {
                // Keep the pre-change history until chnum tells us whether the pile grew or shrank.
            } else {
                boolean countReady = pileCountBefore == null || pileCountObserved;
                if (expectedItemName != null && !now.equals(snapshot)
                        && countReady && idleMs >= SETTLE_MS) {
                    commitUnlocked();
                } else if (placementDeadlineMs == 0
                        && expectedItemName != null && idleMs >= SESSION_IDLE_MS) {
                    clearTransferUnlocked();
                }
            }
            NGameUI gui = currentGui;
            boolean pileUiOpen = gui != null && gui.getStockpile() != null;
            if (!pileUiOpen && pendingPlaceRc == null
                    && placementDeadlineMs == 0
                    && pendingSeed.isEmpty() && frozenHand.isEmpty() && !awaitingPlaceInsert
                    && System.currentTimeMillis() - lastChangeMs >= SESSION_IDLE_MS) {
                clearTransferUnlocked();
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
                if (StockpileStoragePolicy.isExpectedNewPlacedPileAt(
                        gob.ngob.name, pendingPlaceResName, gob.id, preexistingPlacePileIds,
                        rc.x, rc.y, gob.rc.x, gob.rc.y)) {
                    return gob;
                }
            }
        }
        return null;
    }

    private static Set<Long> stockpileIdsAt(Coord2d rc) {
        Set<Long> ids = new HashSet<>();
        if (rc == null) {
            return ids;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return ids;
        }
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob == null || gob.rc == null || gob instanceof MapView.Plob || gob.id <= 0) {
                    continue;
                }
                if (StockpileStoragePolicy.sameWorldTile(rc.x, rc.y, gob.rc.x, gob.rc.y)) {
                    ids.add(gob.id);
                }
            }
        }
        return ids;
    }

    private static Gob gobFromClick(ClickData inf) {
        return inf == null ? null : Gob.from(inf.ci);
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
            return;
        }
        if (sessionGob.ngob == null || sessionGob.ngob.hash == null
                || sessionGob.ngob.gcoord == null) {
            return;
        }
        if (transferDirection == null || expectedItemName == null) {
            clearTransferUnlocked();
            return;
        }
        List<StockpileStoragePolicy.Item> now = captureInventory();
        List<StockpileStoragePolicy.Item> rawGone = StockpileStoragePolicy.disappeared(snapshot, now);
        List<StockpileStoragePolicy.Item> rawGained = StockpileStoragePolicy.appeared(snapshot, now);
        if (StockpileStoragePolicy.isStackResolution(rawGone, rawGained)) {
            snapshot = now;
            lastSeen = now;
            lastChangeMs = System.currentTimeMillis();
            return;
        }
        int confirmedCount = -1;
        if (pileCountObserved && pileCountBefore != null && pileCountAfter != null) {
            confirmedCount = transferDirection == StockpileStoragePolicy.TransferDirection.INTO_PILE
                    ? Math.max(0, pileCountAfter - pileCountBefore)
                    : Math.max(0, pileCountBefore - pileCountAfter);
        }
        List<StockpileStoragePolicy.Item> attributed = StockpileStoragePolicy.attributedTransfer(
                snapshot, now, expectedItemName, transferDirection, confirmedCount);
        List<StockpileStoragePolicy.Item> gone =
                transferDirection == StockpileStoragePolicy.TransferDirection.INTO_PILE
                        ? attributed : List.of();
        List<StockpileStoragePolicy.Item> gained =
                transferDirection == StockpileStoragePolicy.TransferDirection.OUT_OF_PILE
                        ? attributed : List.of();
        Gob gob = sessionGob;
        String hash = gob.ngob.hash;
        long gridId = gob.ngob.grid_id;
        Coord gcoord = gob.ngob.gcoord;
        clearTransferUnlocked();
        if (gone.isEmpty() && gained.isEmpty()) {
            return;
        }
        DatabaseManager db = nurgling.NCore.databaseManager;
        if (db == null || !db.isReady()) {
            return;
        }
        db.submitTask(() -> applyDelta(db, hash, gridId, gcoord, gone, gained));
    }

    private static void clearTransferUnlocked() {
        sessionGob = null;
        snapshot = null;
        lastSeen = null;
        transferDirection = null;
        expectedItemName = null;
        pileCountBefore = null;
        pileCountAfter = null;
        pileCountObserved = false;
        preexistingPlacePileIds = Set.of();
        pendingPlaceResName = null;
        placementDeadlineMs = 0;
        armedPlacementHand = List.of();
        armedPlacementHandAtMs = 0;
        passiveTransitions.clear();
    }

    private static void clearExpiredPlacementUnlocked() {
        sessionGob = null;
        snapshot = null;
        lastSeen = null;
        pendingPlaceRc = null;
        preexistingPlacePileIds = Set.of();
        pendingPlaceResName = null;
        placementDeadlineMs = 0;
        placingHeld = List.of();
        pendingSeed = List.of();
        frozenHand = List.of();
        awaitingPlaceInsert = false;
        armedPlacementHand = List.of();
        armedPlacementHandAtMs = 0;
        transferDirection = null;
        expectedItemName = null;
        pileCountBefore = null;
        pileCountAfter = null;
        pileCountObserved = false;
        passiveTransitions.clear();
    }

    private static void rememberPassiveTransitionUnlocked(
            List<StockpileStoragePolicy.Item> before,
            List<StockpileStoragePolicy.Item> after, long changedAtMs) {
        passiveTransitions.addLast(new InventoryTransition(before, after, changedAtMs));
        while (passiveTransitions.size() > MAX_PASSIVE_TRANSITIONS) {
            passiveTransitions.removeFirst();
        }
    }

    private static void prunePassiveTransitionsUnlocked(long nowMs) {
        while (!passiveTransitions.isEmpty()
                && nowMs - passiveTransitions.peekFirst().changedAtMs > PASSIVE_HISTORY_MS) {
            passiveTransitions.removeFirst();
        }
    }

    private static void selectCorrelatedBaselineUnlocked() {
        if (expectedItemName == null || transferDirection == null) {
            return;
        }
        List<StockpileStoragePolicy.Item> earliest = null;
        Iterator<InventoryTransition> it = passiveTransitions.descendingIterator();
        while (it.hasNext()) {
            InventoryTransition transition = it.next();
            int matching = StockpileStoragePolicy.confirmedInventoryTransitionCount(
                    transition.before, transition.after, transferDirection);
            if (matching > 0) {
                earliest = transition.before;
            }
        }
        if (earliest != null) {
            snapshot = earliest;
        }
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
                        List<StockpileStoragePolicy.Item> storedItems = new ArrayList<>();
                        for (StorageItemDao.StorageItemData row : existing) {
                            storedItems.add(new StockpileStoragePolicy.Item(
                                    row.getName(), row.getQuality()));
                        }
                        int matchIndex = StockpileStoragePolicy.withdrawalRecordIndex(item, storedItems);
                        if (matchIndex >= 0) {
                            StorageItemDao.StorageItemData match = existing.get(matchIndex);
                            dao.deleteStorageItem(adapter, match.getItemHash());
                            existing.remove(matchIndex);
                        }
                    }
                }
                return null;
            });
            ItemWatcher.invalidateContainerCache(containerHash);
            NGlobalSearchItems.clearQueryCache();
            NSearchItem.notifyContainerDataChanged();
            NGameUI gui = NUtils.getGameUI();
            if (gui != null && gui.storageItemsWidget != null) {
                gui.storageItemsWidget.requestRefresh();
            }
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
