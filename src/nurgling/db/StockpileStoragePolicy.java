package nurgling.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure rules for detecting stockpiles and attributing inventory
 * deltas to a pile gob. No UI, no DB.
 */
public final class StockpileStoragePolicy {
    private StockpileStoragePolicy() {}

    public static boolean isStockpileRes(String name) {
        return name != null && name.contains("stockpile");
    }

    public static final class Item {
        public final String name;
        public final double quality;

        public Item(String name, double quality) {
            this.name = name;
            this.quality = quality;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Item)) {
                return false;
            }
            Item item = (Item) o;
            return Double.compare(item.quality, quality) == 0 && Objects.equals(name, item.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, quality);
        }

        @Override
        public String toString() {
            return name + " q" + quality;
        }
    }

    public static final class FetchSplit {
        public final List<Item> keep;
        public final List<Item> restock;

        public FetchSplit(List<Item> keep, List<Item> restock) {
            this.keep = keep;
            this.restock = restock;
        }
    }

    public enum ProbeAction { KEEP_ONE, DUMP_MAX }

    public enum TransferDirection { INTO_PILE, OUT_OF_PILE }

    public static TransferDirection directionFromPileCounts(int oldCount, int newCount) {
        if (newCount > oldCount) {
            return TransferDirection.INTO_PILE;
        }
        if (newCount < oldCount) {
            return TransferDirection.OUT_OF_PILE;
        }
        return null;
    }

    public static int confirmedPileDelta(int before, int after) {
        return Math.abs(after - before);
    }

    public static List<Item> disappeared(List<Item> before, List<Item> after) {
        return unmatched(before, after);
    }

    public static List<Item> appeared(List<Item> before, List<Item> after) {
        return unmatched(after, before);
    }

    /**
     * Attribute only the inventory change that an explicit stockpile transfer can cause.
     * A non-negative confirmedCount is the server-observed pile delta and caps partial transfers.
     */
    public static List<Item> attributedTransfer(List<Item> before, List<Item> after,
                                                String expectedName, TransferDirection direction,
                                                int confirmedCount) {
        if (expectedName == null || direction == null || confirmedCount == 0) {
            return List.of();
        }
        List<Item> gone = disappeared(before, after);
        List<Item> gained = appeared(before, after);
        if (isStackResolution(gone, gained)) {
            return List.of();
        }
        List<Item> changed = direction == TransferDirection.INTO_PILE ? gone : gained;
        List<Item> matching = new ArrayList<>();
        for (Item item : changed) {
            if (expectedName.equals(item.name)) {
                matching.add(item);
            }
        }
        if (confirmedCount >= 0) {
            if (changed.size() == confirmedCount) {
                return changed;
            }
            if (matching.size() > confirmedCount) {
                return new ArrayList<>(matching.subList(0, confirmedCount));
            }
        }
        return matching;
    }

    public static int confirmedInventoryTransitionCount(List<Item> before, List<Item> after,
                                                        TransferDirection direction) {
        if (before == null || after == null || direction == null) {
            return 0;
        }
        List<Item> gone = disappeared(before, after);
        List<Item> gained = appeared(before, after);
        if (isStackResolution(gone, gained)) {
            return 0;
        }
        return direction == TransferDirection.INTO_PILE ? gone.size() : gained.size();
    }

    public static boolean isMatchingInventoryTransition(List<Item> before, List<Item> after,
                                                        String expectedName,
                                                        TransferDirection direction) {
        return matchingInventoryTransitionCount(before, after, expectedName, direction) > 0;
    }

    public static int matchingInventoryTransitionCount(List<Item> before, List<Item> after,
                                                       String expectedName,
                                                       TransferDirection direction) {
        return attributedTransfer(before, after, expectedName, direction, -1).size();
    }

    public static boolean isWithdrawalRecordMatch(Item actual, Item stored) {
        if (actual == null || stored == null || !Objects.equals(actual.name, stored.name)) {
            return false;
        }
        return Double.compare(actual.quality, stored.quality) == 0
                || (actual.quality > 0 && stored.quality <= 0);
    }

    public static int withdrawalRecordIndex(Item actual, List<Item> stored) {
        if (actual == null || stored == null) {
            return -1;
        }
        for (int i = 0; i < stored.size(); i++) {
            Item candidate = stored.get(i);
            if (candidate != null && Objects.equals(actual.name, candidate.name)
                    && Double.compare(actual.quality, candidate.quality) == 0) {
                return i;
            }
        }
        if (actual.quality > 0) {
            for (int i = 0; i < stored.size(); i++) {
                Item candidate = stored.get(i);
                if (candidate != null && Objects.equals(actual.name, candidate.name)
                        && candidate.quality <= 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static FetchSplit splitForFetch(List<Item> dumped, String name, double minQ, double maxQ, int count) {
        List<Item> keep = new ArrayList<>();
        List<Item> restock = new ArrayList<>();
        int kept = 0;
        for (Item item : dumped) {
            if (kept < count && name.equals(item.name) && item.quality >= minQ && item.quality <= maxQ) {
                keep.add(item);
                kept++;
            } else {
                restock.add(item);
            }
        }
        return new FetchSplit(keep, restock);
    }

    public static FetchSplit splitForFetch(List<Item> dumped, List<Item> needed) {
        List<Item> remainingNeeded = new ArrayList<>(needed);
        List<Item> keep = new ArrayList<>();
        List<Item> restock = new ArrayList<>();
        for (Item item : dumped) {
            int idx = remainingNeeded.indexOf(item);
            if (idx >= 0) {
                remainingNeeded.remove(idx);
                keep.add(item);
            } else {
                restock.add(item);
            }
        }
        return new FetchSplit(keep, restock);
    }

    /**
     * Turn one inventory slot into the items it actually holds.
     * Stack contents win over the shell; otherwise Amount is repeated.
     */
    public static List<Item> expandSlot(String name, double quality, int amount, List<Item> contents) {
        if (contents != null && !contents.isEmpty()) {
            return new ArrayList<>(contents);
        }
        if (name == null) {
            return List.of();
        }
        int n = Math.max(1, amount);
        List<Item> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Item(name, quality));
        }
        return out;
    }

    private static List<Item> unmatched(List<Item> source, List<Item> subtract) {
        List<Item> remaining = new ArrayList<>(subtract);
        List<Item> extra = new ArrayList<>();
        for (Item item : source) {
            int idx = remaining.indexOf(item);
            if (idx >= 0) {
                remaining.remove(idx);
            } else {
                extra.add(item);
            }
        }
        return extra;
    }

    /**
     * Stack contents finishing load looks like q0 items left and quality items arrived.
     * That is not a put/take against a stockpile.
     */
    public static boolean isStackResolution(List<Item> gone, List<Item> gained) {
        if (gone == null || gained == null || gone.isEmpty() || gained.isEmpty()) {
            return false;
        }
        if (gone.size() != gained.size()) {
            return false;
        }
        for (Item item : gone) {
            if (item.quality > 0) {
                return false;
            }
        }
        for (Item item : gained) {
            if (item.quality <= 0) {
                return false;
            }
        }
        List<String> goneNames = new ArrayList<>();
        List<String> gainedNames = new ArrayList<>();
        for (Item item : gone) {
            goneNames.add(item.name);
        }
        for (Item item : gained) {
            gainedNames.add(item.name);
        }
        goneNames.sort(String::compareTo);
        gainedNames.sort(String::compareTo);
        return goneNames.equals(gainedNames);
    }

    /**
     * Leftovers after a dump-fetch go back to the original pile point.
     * Never a nearby free cell: an empty gob still occupies the tile,
     * so a free-place search puts a new pile beside it.
     */
    public static RestockPlan restockPlan(double originalX, double originalY, boolean pileStillAtOriginal) {
        RestockPlan.Mode mode = pileStillAtOriginal
                ? RestockPlan.Mode.DROP_ON_EXISTING
                : RestockPlan.Mode.PLACE_AT_ORIGINAL;
        return new RestockPlan(mode, originalX, originalY);
    }

    public static final double TILE = 11;

    public static boolean sameWorldTile(double x1, double y1, double x2, double y2) {
        return Math.floor(x1 / TILE) == Math.floor(x2 / TILE)
                && Math.floor(y1 / TILE) == Math.floor(y2 / TILE);
    }

    /**
     * A newly placed pile gob sits on the same tile as the ghost, not
     * necessarily within 0.5 of the click point.
     */
    public static boolean isPlacedPileAt(String resName, double placeX, double placeY,
                                         double gobX, double gobY) {
        return isStockpileRes(resName) && sameWorldTile(placeX, placeY, gobX, gobY);
    }

    /** A placement can only bind to a stockpile gob that did not exist before the click. */
    public static boolean isNewPlacedPileAt(String resName, long gobId, Set<Long> existingIds,
                                            double placeX, double placeY,
                                            double gobX, double gobY) {
        return (existingIds == null || !existingIds.contains(gobId))
                && isPlacedPileAt(resName, placeX, placeY, gobX, gobY);
    }

    public static boolean isExpectedNewPlacedPileAt(String resName, String expectedResName,
                                                    long gobId, Set<Long> existingIds,
                                                    double placeX, double placeY,
                                                    double gobX, double gobY) {
        return expectedResName != null && expectedResName.equals(resName)
                && isNewPlacedPileAt(resName, gobId, existingIds,
                placeX, placeY, gobX, gobY);
    }

    /**
     * Neighbor piles must not be treated as the original.
     * Same hash only — never a nearby tile with a different gob.
     */
    public static boolean isOriginalPile(String originalHash, String foundHash,
                                         double originalX, double originalY,
                                         double foundX, double foundY) {
        return originalHash != null && originalHash.equals(foundHash);
    }

    /**
     * Ground itemact hits a gob within this radius. Player standing
     * between two piles is ~5–6 units from the neighbor.
     */
    public static final double PILE_HIT_RADIUS = 8;

    public static boolean clickHitsForeignPile(double clickX, double clickY,
                                              double targetX, double targetY,
                                              double pileX, double pileY) {
        if (sameWorldTile(pileX, pileY, targetX, targetY)) {
            return false;
        }
        double dx = clickX - pileX;
        double dy = clickY - pileY;
        return dx * dx + dy * dy <= PILE_HIT_RADIUS * PILE_HIT_RADIUS;
    }

    /**
     * Keep-qualities often sit at the front of a mixed ore stack.
     * Restock must pick a matching leftover, not always leaf 0.
     */
    public static int indexOfRestockLeaf(List<Item> leaves, List<Item> restock) {
        if (leaves == null || restock == null) {
            return -1;
        }
        for (int i = 0; i < leaves.size(); i++) {
            if (restock.contains(leaves.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static final class RestockPlan {
        public enum Mode { DROP_ON_EXISTING, PLACE_AT_ORIGINAL }

        public final Mode mode;
        public final double x;
        public final double y;

        public RestockPlan(Mode mode, double x, double y) {
            this.mode = mode;
            this.x = x;
            this.y = y;
        }
    }

    /** A stack shell cannot be dropped into a stockpile; only inner items can. */
    public static boolean isPuttableInStockpile(boolean stackShell) {
        return !stackShell;
    }

    /** Stack window or Amount>1 without taking a leaf — cannot go into a pile. */
    public static boolean isStackLike(boolean itemStackContents, int amount) {
        return itemStackContents || amount > 1;
    }

    /**
     * While a stockpile ghost is being placed the cursor item is gone from vhand.
     * Keep those items in the snapshot so the place delta still sees them.
     */
    public static List<Item> withPlacingHeld(List<Item> invAndHand, List<Item> placingHeld,
                                            boolean vhandPresent, boolean placingActive) {
        if (vhandPresent || !placingActive || placingHeld == null || placingHeld.isEmpty()) {
            return invAndHand;
        }
        List<Item> out = new ArrayList<>(invAndHand);
        out.addAll(placingHeld);
        return out;
    }

    /**
     * Seed items used to create the stockpile ghost must stay in the place
     * snapshot even after they have already left inventory/hand.
     */
    public static List<Item> mergePendingPlace(List<Item> snapshot, List<Item> pendingSeed) {
        List<Item> base = snapshot == null ? new ArrayList<>() : new ArrayList<>(snapshot);
        if (pendingSeed == null || pendingSeed.isEmpty()) {
            return base;
        }
        List<Item> remaining = new ArrayList<>(base);
        for (Item seed : pendingSeed) {
            int idx = remaining.indexOf(seed);
            if (idx >= 0) {
                remaining.remove(idx);
            } else {
                base.add(seed);
            }
        }
        return base;
    }

    /**
     * At the placement click the seed has already been consumed from the cursor.
     * It must therefore be added even when inventory still contains an equal item.
     */
    public static List<Item> mergeConsumedPlacementSeed(List<Item> snapshot, List<Item> seed) {
        List<Item> out = snapshot == null ? new ArrayList<>() : new ArrayList<>(snapshot);
        if (seed != null) {
            out.addAll(seed);
        }
        return out;
    }

    /**
     * Rebinding the pile gob must not replace the snapshot with post-consume
     * inventory while the seed has already left the player's items.
     */
    public static boolean keepSnapshotOnRebind(List<Item> pendingSeed, List<Item> currentInv) {
        return keepSnapshotOnRebind(pendingSeed, currentInv, true);
    }

    /**
     * Leftover frozen-hand state must not lock the inventory snapshot
     * when the player is just putting into an existing pile.
     */
    public static boolean keepSnapshotOnRebind(List<Item> pendingSeed, List<Item> currentInv,
                                              boolean pendingNewPile) {
        if (!pendingNewPile) {
            return false;
        }
        if (pendingSeed == null || pendingSeed.isEmpty()) {
            return false;
        }
        List<Item> missing = unmatched(pendingSeed, currentInv == null ? List.of() : currentInv);
        return !missing.isEmpty();
    }

    /**
     * Take one item first. Keep it when it matches a needed fingerprint;
     * otherwise dump as many as inventory can hold.
     */
    public static ProbeAction probeThenDump(Item first, List<Item> needed) {
        if (first != null && needed != null && needed.contains(first)) {
            return ProbeAction.KEEP_ONE;
        }
        return ProbeAction.DUMP_MAX;
    }

    /**
     * Snapshot of the cursor while it still exists. Empty captures must not
     * wipe it: once the stockpile ghost starts, vhand is already gone.
     */
    public static List<Item> keepLastHand(List<Item> previous, List<Item> captured) {
        if (captured == null || captured.isEmpty()) {
            return previous == null ? List.of() : previous;
        }
        if (previous != null && !previous.isEmpty()
                && allZeroQuality(captured) && !allZeroQuality(previous)
                && sameNameCounts(previous, captured)) {
            return previous;
        }
        return new ArrayList<>(captured);
    }

    /** Current cursor contents take precedence; otherwise keep the last captured placement seed. */
    public static List<Item> placementSeed(List<Item> currentHand, List<Item> lastHand) {
        if (currentHand != null && !currentHand.isEmpty()) {
            return new ArrayList<>(currentHand);
        }
        if (lastHand != null && !lastHand.isEmpty()) {
            return new ArrayList<>(lastHand);
        }
        return List.of();
    }

    /** A consumed cursor item is safe to reuse only within the same short itemact sequence. */
    public static List<Item> placementSeed(List<Item> currentHand, List<Item> armedHand,
                                           long capturedAtMs, long nowMs, long maxAgeMs) {
        if (currentHand != null && !currentHand.isEmpty()) {
            return new ArrayList<>(currentHand);
        }
        if (capturedAtMs <= 0 || nowMs < capturedAtMs || nowMs - capturedAtMs > maxAgeMs) {
            return List.of();
        }
        return placementSeed(List.of(), armedHand);
    }

    /** A not-yet-loaded ghost may be a stockpile only when backed by a fresh itemact seed. */
    public static List<Item> placementSeedForResource(String resName,
                                                      List<Item> currentHand, List<Item> armedHand,
                                                      long capturedAtMs, long nowMs, long maxAgeMs) {
        if (resName != null && !isStockpileRes(resName)) {
            return List.of();
        }
        return placementSeed(currentHand, armedHand, capturedAtMs, nowMs, maxAgeMs);
    }

    /** Recover the itemact seed at the guaranteed place event if ghost-start was bypassed. */
    public static List<Item> placementSeedAtPlace(String resName,
                                                  List<Item> frozenSeed, List<Item> armedHand,
                                                  long capturedAtMs, long nowMs, long maxAgeMs) {
        if (frozenSeed != null && !frozenSeed.isEmpty()) {
            return new ArrayList<>(frozenSeed);
        }
        return placementSeedForResource(
                resName, List.of(), armedHand, capturedAtMs, nowMs, maxAgeMs);
    }

    public static boolean placementDeadlineActive(long deadlineMs, long nowMs) {
        return deadlineMs > 0 && nowMs <= deadlineMs;
    }

    public static boolean canReplacePlacementSession(long deadlineMs, long nowMs) {
        return !placementDeadlineActive(deadlineMs, nowMs);
    }

    /**
     * Each new ghost overwrites the items that will be written when that
     * pile is placed. One item or a whole stack.
     */
    public static List<Item> freezeHandForGhost(List<Item> lastHand) {
        return freezeHandForGhost(lastHand, List.of());
    }

    /** Empty lastHand must not wipe a freeze already taken for this ghost. */
    public static List<Item> freezeHandForGhost(List<Item> lastHand, List<Item> previousFrozen) {
        if (lastHand == null || lastHand.isEmpty()) {
            return previousFrozen == null ? List.of() : new ArrayList<>(previousFrozen);
        }
        return new ArrayList<>(lastHand);
    }

    /** Frozen hand items go straight into storageitems for a newly placed pile. */
    public static List<Item> itemsToInsertOnNewPile(List<Item> frozenHand) {
        return itemsToInsertOnNewPile(frozenHand, true);
    }

    /** Frozen hand is written only for a newly placed pile, never an existing one. */
    public static List<Item> itemsToInsertOnNewPile(List<Item> frozenHand, boolean pendingNewPile) {
        if (!pendingNewPile || frozenHand == null || frozenHand.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(frozenHand);
    }

    /** How many items to dump from a pile that may be larger than inventory. */
    public static int takeCount(int pileCount, int freeSlots, int stackSize) {
        if (pileCount <= 0 || freeSlots <= 0) {
            return 0;
        }
        int cap = freeSlots * Math.max(1, stackSize);
        return Math.min(pileCount, cap);
    }

    /** Dump only what was asked, capped by how much inventory can hold. */
    public static int takeCount(int pileCount, int freeSlots, int stackSize, int requested) {
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, takeCount(pileCount, freeSlots, stackSize));
    }

    private static boolean allZeroQuality(List<Item> items) {
        for (Item item : items) {
            if (item.quality > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameNameCounts(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) {
            return false;
        }
        List<String> namesA = new ArrayList<>();
        List<String> namesB = new ArrayList<>();
        for (Item item : a) {
            namesA.add(item.name == null ? "" : item.name);
        }
        for (Item item : b) {
            namesB.add(item.name == null ? "" : item.name);
        }
        namesA.sort(String::compareTo);
        namesB.sort(String::compareTo);
        return namesA.equals(namesB);
    }
}
