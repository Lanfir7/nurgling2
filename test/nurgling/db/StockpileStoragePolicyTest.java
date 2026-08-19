package nurgling.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockpileStoragePolicyTest {
    @Test
    void detectsStockpileResourceNames() {
        assertTrue(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/stockpile-board"));
        assertTrue(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/stockpile-pipeleaves"));
        assertFalse(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/chest"));
        assertFalse(StockpileStoragePolicy.isStockpileRes(null));
    }

    @Test
    void disappearedItemsAreInventoryMinusRemainder() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Stone", 12),
                item("Stone", 8),
                item("Stone", 12),
                item("Branch", 10)
        );
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Stone", 8),
                item("Branch", 10)
        );

        List<StockpileStoragePolicy.Item> gone = StockpileStoragePolicy.disappeared(before, after);
        assertEquals(List.of(item("Stone", 12), item("Stone", 12)), gone);
    }

    @Test
    void appearedItemsAreNewInventoryEntries() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Stone", 8));
        List<StockpileStoragePolicy.Item> after = List.of(item("Stone", 8), item("Stone", 12));

        List<StockpileStoragePolicy.Item> gained = StockpileStoragePolicy.appeared(before, after);
        assertEquals(List.of(item("Stone", 12)), gained);
    }

    @Test
    void fetchKeepsMatchingQualityAndRestocksTheRest() {
        List<StockpileStoragePolicy.Item> dumped = List.of(
                item("Stone", 5),
                item("Stone", 12),
                item("Stone", 9),
                item("Branch", 10)
        );

        StockpileStoragePolicy.FetchSplit split =
                StockpileStoragePolicy.splitForFetch(dumped, "Stone", 12, 12, 1);

        assertEquals(List.of(item("Stone", 12)), split.keep);
        assertEquals(List.of(item("Stone", 5), item("Stone", 9), item("Branch", 10)), split.restock);
    }

    @Test
    void stackContentsReplaceTheShell() {
        List<StockpileStoragePolicy.Item> expanded = StockpileStoragePolicy.expandSlot(
                "Lead Glance", 0, 10,
                List.of(item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18)));
        assertEquals(3, expanded.size());
        assertEquals(List.of(item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18)), expanded);
    }

    @Test
    void amountWithoutContentsCountsEachItem() {
        List<StockpileStoragePolicy.Item> expanded =
                StockpileStoragePolicy.expandSlot("Lead Glance", 0, 34, List.of());
        assertEquals(34, expanded.size());
        assertTrue(expanded.stream().allMatch(i -> i.name.equals("Lead Glance")));
    }

    @Test
    void stackResolutionIsNotAPutOrTake() {
        List<StockpileStoragePolicy.Item> gone = List.of(
                item("Lead Glance", 0), item("Lead Glance", 0), item("Lead Glance", 0));
        List<StockpileStoragePolicy.Item> gained = List.of(
                item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18));
        assertTrue(StockpileStoragePolicy.isStackResolution(gone, gained));
        assertFalse(StockpileStoragePolicy.isStackResolution(
                List.of(item("Lead Glance", 18)), List.of()));
        assertFalse(StockpileStoragePolicy.isStackResolution(
                List.of(), List.of(item("Lead Glance", 18))));
    }

    @Test
    void restockDropsOnExistingPileWhenGobRemains() {
        StockpileStoragePolicy.RestockPlan plan = StockpileStoragePolicy.restockPlan(16.5, 32.5, true);
        assertEquals(StockpileStoragePolicy.RestockPlan.Mode.DROP_ON_EXISTING, plan.mode);
        assertEquals(16.5, plan.x, 0);
        assertEquals(32.5, plan.y, 0);
    }

    @Test
    void restockPlacesAtOriginalPointWhenGobGone() {
        StockpileStoragePolicy.RestockPlan plan = StockpileStoragePolicy.restockPlan(16.5, 32.5, false);
        assertEquals(StockpileStoragePolicy.RestockPlan.Mode.PLACE_AT_ORIGINAL, plan.mode);
        assertEquals(16.5, plan.x, 0);
        assertEquals(32.5, plan.y, 0);
    }

    @Test
    void adjacentTilesAreDifferentPiles() {
        assertTrue(StockpileStoragePolicy.sameWorldTile(5, 5, 10, 10));
        assertFalse(StockpileStoragePolicy.sameWorldTile(5, 5, 16, 5));
        assertTrue(StockpileStoragePolicy.isOriginalPile("h1", "h1", 5, 5, 100, 100));
        assertFalse(StockpileStoragePolicy.isOriginalPile("h1", "h2", 5, 5, 16, 5));
        assertFalse(StockpileStoragePolicy.isOriginalPile("h1", "h2", 5, 5, 8, 8));
    }

    @Test
    void groundClickBetweenPilesHitsTheNeighbor() {
        // pile A at (5.5, 5.5), pile B at (16.5, 5.5), player between them
        assertTrue(StockpileStoragePolicy.clickHitsForeignPile(11, 5.5, 5.5, 5.5, 16.5, 5.5));
        assertFalse(StockpileStoragePolicy.clickHitsForeignPile(5.5, 5.5, 5.5, 5.5, 16.5, 5.5));
    }

    @Test
    void restockPicksMatchingLeafInsideMixedStack() {
        List<StockpileStoragePolicy.Item> leaves = List.of(
                item("Lead Glance", 33.02),
                item("Lead Glance", 20),
                item("Lead Glance", 32.52));
        List<StockpileStoragePolicy.Item> restock = List.of(item("Lead Glance", 20));
        assertEquals(1, StockpileStoragePolicy.indexOfRestockLeaf(leaves, restock));
        assertEquals(-1, StockpileStoragePolicy.indexOfRestockLeaf(leaves, List.of(item("Iron Ore", 40))));
    }

    @Test
    void stackShellIsNotPuttableInStockpile() {
        assertFalse(StockpileStoragePolicy.isPuttableInStockpile(true));
        assertTrue(StockpileStoragePolicy.isPuttableInStockpile(false));
    }

    @Test
    void placingHeldCountsWhenCursorIsEmpty() {
        List<StockpileStoragePolicy.Item> inv = List.of(item("Branch", 10));
        List<StockpileStoragePolicy.Item> held = List.of(item("Kyanite", 12), item("Kyanite", 11));
        List<StockpileStoragePolicy.Item> snap = StockpileStoragePolicy.withPlacingHeld(inv, held, false, true);
        assertEquals(List.of(item("Branch", 10), item("Kyanite", 12), item("Kyanite", 11)), snap);
    }

    @Test
    void placingHeldIgnoredWhenNotPlacingOrHandVisible() {
        List<StockpileStoragePolicy.Item> inv = List.of(item("Branch", 10));
        List<StockpileStoragePolicy.Item> held = List.of(item("Kyanite", 12));
        assertEquals(inv, StockpileStoragePolicy.withPlacingHeld(inv, held, true, true));
        assertEquals(inv, StockpileStoragePolicy.withPlacingHeld(inv, held, false, false));
        assertEquals(inv, StockpileStoragePolicy.withPlacingHeld(inv, null, false, true));
    }

    @Test
    void fetchTakesWhatFitsWithStacks() {
        assertEquals(40, StockpileStoragePolicy.takeCount(200, 10, 4));
        assertEquals(5, StockpileStoragePolicy.takeCount(5, 10, 4));
        assertEquals(0, StockpileStoragePolicy.takeCount(200, 0, 4));
        assertEquals(10, StockpileStoragePolicy.takeCount(200, 10, 1));
    }

    @Test
    void fetchDoesNotDumpWholePileWhenOnlyAFewAreRequested() {
        assertEquals(1, StockpileStoragePolicy.takeCount(200, 10, 4, 1));
        assertEquals(5, StockpileStoragePolicy.takeCount(200, 10, 4, 5));
        assertEquals(40, StockpileStoragePolicy.takeCount(200, 10, 4, 100));
        assertEquals(0, StockpileStoragePolicy.takeCount(200, 10, 4, 0));
    }

    @Test
    void unexpandedAmountIsAStackShell() {
        assertTrue(StockpileStoragePolicy.isStackLike(true, 1));
        assertTrue(StockpileStoragePolicy.isStackLike(false, 12));
        assertFalse(StockpileStoragePolicy.isStackLike(false, 1));
        assertFalse(StockpileStoragePolicy.isPuttableInStockpile(StockpileStoragePolicy.isStackLike(false, 12)));
    }

    @Test
    void mergePendingPlaceAddsMissingSeed() {
        List<StockpileStoragePolicy.Item> snap = List.of(item("Branch", 10));
        List<StockpileStoragePolicy.Item> seed = List.of(item("Kyanite", 12));
        assertEquals(
                List.of(item("Branch", 10), item("Kyanite", 12)),
                StockpileStoragePolicy.mergePendingPlace(snap, seed));
    }

    @Test
    void mergePendingPlaceDoesNotDuplicateSeedAlreadyInSnapshot() {
        List<StockpileStoragePolicy.Item> snap = List.of(item("Branch", 10), item("Kyanite", 12));
        List<StockpileStoragePolicy.Item> seed = List.of(item("Kyanite", 12));
        assertEquals(
                List.of(item("Branch", 10), item("Kyanite", 12)),
                StockpileStoragePolicy.mergePendingPlace(snap, seed));
    }

    @Test
    void keepSnapshotWhenSeedAlreadyLeftInventory() {
        List<StockpileStoragePolicy.Item> seed = List.of(item("Kyanite", 12));
        List<StockpileStoragePolicy.Item> now = List.of(item("Branch", 10));
        assertTrue(StockpileStoragePolicy.keepSnapshotOnRebind(seed, now));
    }

    @Test
    void allowSnapshotResetWhenNoPendingSeed() {
        assertFalse(StockpileStoragePolicy.keepSnapshotOnRebind(List.of(), List.of(item("Branch", 10))));
        assertFalse(StockpileStoragePolicy.keepSnapshotOnRebind(null, List.of()));
    }

    @Test
    void allowSnapshotResetWhenSeedStillInInventory() {
        List<StockpileStoragePolicy.Item> seed = List.of(item("Kyanite", 12));
        List<StockpileStoragePolicy.Item> now = List.of(item("Branch", 10), item("Kyanite", 12));
        assertFalse(StockpileStoragePolicy.keepSnapshotOnRebind(seed, now));
    }

    @Test
    void leftoverHandDoesNotKeepSnapshotUnlessPlacingNewPile() {
        List<StockpileStoragePolicy.Item> seed = List.of(item("Kyanite", 12));
        List<StockpileStoragePolicy.Item> now = List.of(item("Branch", 10));
        assertFalse(StockpileStoragePolicy.keepSnapshotOnRebind(seed, now, false));
        assertTrue(StockpileStoragePolicy.keepSnapshotOnRebind(seed, now, true));
    }

    @Test
    void onlyNewPileConsumesFrozenHand() {
        List<StockpileStoragePolicy.Item> frozen = List.of(item("Kyanite", 12));
        assertEquals(List.of(), StockpileStoragePolicy.itemsToInsertOnNewPile(frozen, false));
        assertEquals(frozen, StockpileStoragePolicy.itemsToInsertOnNewPile(frozen, true));
    }

    @Test
    void probeKeepsWhenFirstMatchesNeeded() {
        assertEquals(
                StockpileStoragePolicy.ProbeAction.KEEP_ONE,
                StockpileStoragePolicy.probeThenDump(
                        item("Stone", 12), List.of(item("Stone", 12), item("Stone", 8))));
    }

    @Test
    void probeDumpsWhenFirstDoesNotMatch() {
        assertEquals(
                StockpileStoragePolicy.ProbeAction.DUMP_MAX,
                StockpileStoragePolicy.probeThenDump(item("Stone", 5), List.of(item("Stone", 12))));
        assertEquals(
                StockpileStoragePolicy.ProbeAction.DUMP_MAX,
                StockpileStoragePolicy.probeThenDump(null, List.of(item("Stone", 12))));
    }

    @Test
    void lastHandKeepsPreviousWhenNewCaptureIsEmpty() {
        List<StockpileStoragePolicy.Item> prev = List.of(item("Kyanite", 12), item("Kyanite", 11));
        assertEquals(prev, StockpileStoragePolicy.keepLastHand(prev, List.of()));
        assertEquals(prev, StockpileStoragePolicy.keepLastHand(prev, null));
    }

    @Test
    void lastHandKeepsQualityWhenNewCaptureIsSameItemsAtQ0() {
        List<StockpileStoragePolicy.Item> prev = List.of(item("Kyanite", 12), item("Kyanite", 11));
        List<StockpileStoragePolicy.Item> raw = List.of(item("Kyanite", 0), item("Kyanite", 0));
        assertEquals(prev, StockpileStoragePolicy.keepLastHand(prev, raw));
    }

    @Test
    void freezeHandKeepsPreviousWhenNewHandIsEmpty() {
        List<StockpileStoragePolicy.Item> frozen = List.of(item("Kyanite", 12));
        assertEquals(frozen, StockpileStoragePolicy.freezeHandForGhost(List.of(), frozen));
        assertEquals(frozen, StockpileStoragePolicy.freezeHandForGhost(null, frozen));
    }

    @Test
    void placedPileMatchesSameTileNotExactPoint() {
        assertTrue(StockpileStoragePolicy.isPlacedPileAt(
                "gfx/terobjs/stockpile-ore", 5.1, 5.2, 8.9, 9.0));
        assertFalse(StockpileStoragePolicy.isPlacedPileAt(
                "gfx/terobjs/stockpile-ore", 5.1, 5.2, 16.5, 5.5));
        assertFalse(StockpileStoragePolicy.isPlacedPileAt("gfx/terobjs/chest", 5, 5, 5, 5));
    }

    @Test
    void lastHandOverwritesWhenNewCaptureHasItems() {
        List<StockpileStoragePolicy.Item> prev = List.of(item("Kyanite", 12));
        List<StockpileStoragePolicy.Item> next = List.of(item("Branch", 8), item("Branch", 7));
        assertEquals(next, StockpileStoragePolicy.keepLastHand(prev, next));
    }

    @Test
    void ghostFreezeOverwritesPreviousHandEachTime() {
        List<StockpileStoragePolicy.Item> first = StockpileStoragePolicy.freezeHandForGhost(
                List.of(item("Kyanite", 12)));
        List<StockpileStoragePolicy.Item> second = StockpileStoragePolicy.freezeHandForGhost(
                List.of(item("Branch", 8), item("Branch", 7)));
        assertEquals(List.of(item("Kyanite", 12)), first);
        assertEquals(List.of(item("Branch", 8), item("Branch", 7)), second);
    }

    @Test
    void placeInsertsFrozenHandEvenWhenInventoryDeltaIsEmpty() {
        List<StockpileStoragePolicy.Item> frozen = List.of(item("Kyanite", 12), item("Kyanite", 11));
        assertEquals(frozen, StockpileStoragePolicy.itemsToInsertOnNewPile(frozen));
        assertEquals(List.of(), StockpileStoragePolicy.itemsToInsertOnNewPile(List.of()));
        assertEquals(List.of(), StockpileStoragePolicy.itemsToInsertOnNewPile(null));
    }

    private static StockpileStoragePolicy.Item item(String name, double quality) {
        return new StockpileStoragePolicy.Item(name, quality);
    }
}
