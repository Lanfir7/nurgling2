package nurgling.db;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class StockpileStoragePolicyTest {
    @Test
    void placeRecoversFreshSeedWhenGhostStartCallbackWasSkipped() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "placementSeedAtPlace", String.class, List.class, List.class,
                    long.class, long.class, long.class);
        } catch (NoSuchMethodException e) {
            fail("onPlace must recover the fresh itemact seed when NMapView skips ghost-start");
            return;
        }
        List<StockpileStoragePolicy.Item> rope = List.of(item("Rope", 28.02));
        List<StockpileStoragePolicy.Item> axe = List.of(item("Axe", 42));

        assertEquals(rope, method.invoke(null,
                "gfx/terobjs/stockpile-rope", List.of(), rope,
                1_000L, 1_500L, 30_000L));
        assertEquals(rope, method.invoke(null,
                "gfx/terobjs/stockpile-rope", rope, axe,
                1_000L, 40_000L, 30_000L));
        assertEquals(List.of(), method.invoke(null,
                "gfx/terobjs/stockpile-rope", List.of(), rope,
                1_000L, 40_001L, 30_000L));
    }

    @Test
    void unresolvedGhostResourceKeepsFreshItemactSeed() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "placementSeedForResource", String.class, List.class, List.class,
                    long.class, long.class, long.class);
        } catch (NoSuchMethodException e) {
            fail("A loading ghost resource must not discard the fresh itemact seed");
            return;
        }
        List<StockpileStoragePolicy.Item> rope = List.of(item("Rope", 16.68));

        assertEquals(rope, method.invoke(null,
                null, List.of(), rope, 1_000L, 1_100L, 30_000L));
        assertEquals(List.of(), method.invoke(null,
                "gfx/terobjs/tree", List.of(), rope, 1_000L, 1_100L, 30_000L));
        assertEquals(List.of(), method.invoke(null,
                null, List.of(), rope, 1_000L, 40_000L, 30_000L));
    }

    @Test
    void staleArmedHandCannotSeedAPlacement() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "placementSeed", List.class, List.class,
                    long.class, long.class, long.class);
        } catch (NoSuchMethodException e) {
            fail("Placement fallback must expire instead of reusing a stale hand item");
            return;
        }
        List<StockpileStoragePolicy.Item> axe = List.of(item("Axe", 42));
        List<StockpileStoragePolicy.Item> rope = List.of(item("Rope", 16.5));

        assertEquals(axe, method.invoke(null, List.of(), axe, 1_000L, 1_500L, 1_000L));
        assertEquals(List.of(), method.invoke(null, List.of(), axe, 1_000L, 2_001L, 1_000L));
        assertEquals(rope, method.invoke(null, rope, axe, 1_000L, 20_000L, 1_000L));
    }

    @Test
    void newPileCandidateMustMatchThePlacedResource() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "isExpectedNewPlacedPileAt", String.class, String.class, long.class, Set.class,
                    double.class, double.class, double.class, double.class);
        } catch (NoSuchMethodException e) {
            fail("A pending Rope placement must not bind to a different stockpile resource");
            return;
        }

        assertTrue((Boolean) method.invoke(null,
                "gfx/terobjs/stockpile-rope", "gfx/terobjs/stockpile-rope", 11L, Set.of(10L),
                100.0, 100.0, 100.0, 100.0));
        assertFalse((Boolean) method.invoke(null,
                "gfx/terobjs/stockpile-soil", "gfx/terobjs/stockpile-rope", 11L, Set.of(10L),
                100.0, 100.0, 100.0, 100.0));
    }

    @Test
    void placementDeadlineBoundsGobAndMetadataWaiting() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "placementDeadlineActive", long.class, long.class);
        } catch (NoSuchMethodException e) {
            fail("Pending placement and delayed gob metadata need a bounded lifetime");
            return;
        }

        assertTrue((Boolean) method.invoke(null, 10_000L, 9_999L));
        assertTrue((Boolean) method.invoke(null, 10_000L, 10_000L));
        assertFalse((Boolean) method.invoke(null, 10_000L, 10_001L));
        assertFalse((Boolean) method.invoke(null, 0L, 0L));
    }

    @Test
    void activePlacementSessionCannotBeReplaced() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "canReplacePlacementSession", long.class, long.class);
        } catch (NoSuchMethodException e) {
            fail("An active placement snapshot must survive unrelated pile events");
            return;
        }

        assertFalse((Boolean) method.invoke(null, 10_000L, 9_999L));
        assertTrue((Boolean) method.invoke(null, 10_000L, 10_001L));
        assertTrue((Boolean) method.invoke(null, 0L, 1L));
    }

    @Test
    void placementKeepsLastHandAfterServerConsumesTheCursorItem() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "placementSeed", List.class, List.class);
        } catch (NoSuchMethodException e) {
            fail("Placement must retain the last hand after vhand is consumed");
            return;
        }
        List<StockpileStoragePolicy.Item> rope = List.of(item("Rope", 16.5));
        List<StockpileStoragePolicy.Item> soil = List.of(item("Soil", 20));

        assertEquals(rope, method.invoke(null, List.of(), rope));
        assertEquals(soil, method.invoke(null, soil, rope));
    }

    @Test
    void placedPileCandidateMustNotBeAnOldPileNearThePlacementPoint() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "isNewPlacedPileAt", String.class, long.class, Set.class,
                    double.class, double.class, double.class, double.class);
        } catch (NoSuchMethodException e) {
            fail("New pile lookup must exclude gobs present before the placement click");
            return;
        }

        assertFalse((Boolean) method.invoke(null, "gfx/terobjs/stockpile-rope", 10L,
                Set.of(10L), 100.0, 100.0, 100.0, 100.0));
        assertTrue((Boolean) method.invoke(null, "gfx/terobjs/stockpile-rope", 11L,
                Set.of(10L), 100.0, 100.0, 100.0, 100.0));
        assertFalse((Boolean) method.invoke(null, "gfx/terobjs/chest", 11L,
                Set.of(10L), 100.0, 100.0, 100.0, 100.0));
    }

    @Test
    void consumedPlacementSeedIsAddedEvenWhenAnotherItemHasTheSameQuality() throws Exception {
        Method method;
        try {
            method = StockpileStoragePolicy.class.getDeclaredMethod(
                    "mergeConsumedPlacementSeed", List.class, List.class);
        } catch (NoSuchMethodException e) {
            fail("A consumed seed must not be confused with an equal item still in inventory");
            return;
        }
        StockpileStoragePolicy.Item rope = item("Rope", 16.5);

        assertEquals(List.of(rope, rope), method.invoke(null, List.of(rope), List.of(rope)));
    }
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

    @Test
    void shiftDepositAttributesEveryMatchingItemButNotOtherInventoryChanges() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Earth", 10), item("Axe", 42), item("Earth", 11), item("Earth", 12));
        List<StockpileStoragePolicy.Item> after = List.of(item("Axe", 42));

        assertEquals(
                List.of(item("Earth", 10), item("Earth", 11), item("Earth", 12)),
                StockpileStoragePolicy.attributedTransfer(
                        before, after, "Earth",
                        StockpileStoragePolicy.TransferDirection.INTO_PILE, -1));
    }

    @Test
    void confirmedPileIncreaseCapsPartiallyAcceptedShiftDeposit() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Earth", 10), item("Earth", 11), item("Earth", 12));

        assertEquals(
                List.of(item("Earth", 10), item("Earth", 11)),
                StockpileStoragePolicy.attributedTransfer(
                        before, List.of(), "Earth",
                        StockpileStoragePolicy.TransferDirection.INTO_PILE, 2));
    }

    @Test
    void rejectedDepositAndUnrelatedAxeDisappearanceRecordNothing() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Earth", 10), item("Axe", 42));

        assertEquals(List.of(), StockpileStoragePolicy.attributedTransfer(
                before, before, "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE, 0));
        assertEquals(List.of(), StockpileStoragePolicy.attributedTransfer(
                before, List.of(item("Earth", 10)), "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE, -1));
    }

    @Test
    void withdrawalAttributesOnlyTheConfirmedPileResource() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Axe", 42));
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Axe", 42), item("Earth", 10), item("Earth", 11), item("Branch", 7));

        assertEquals(
                List.of(item("Earth", 10)),
                StockpileStoragePolicy.attributedTransfer(
                        before, after, "Earth",
                        StockpileStoragePolicy.TransferDirection.OUT_OF_PILE, 1));
    }

    @Test
    void confirmedCategoryPileAttributesTheActualInventoryVariant() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Branch", 27.6));
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Branch", 27.6), item("Cat Gold", 29));

        assertEquals(List.of(item("Cat Gold", 29)),
                StockpileStoragePolicy.attributedTransfer(
                        before, after, "Stone",
                        StockpileStoragePolicy.TransferDirection.OUT_OF_PILE, 1));
    }

    @Test
    void confirmedCategoryPileKeepsExactAndVariantItemsInSameBurst() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Stone", 20), item("Cat Gold", 29));

        assertEquals(List.of(item("Stone", 20), item("Cat Gold", 29)),
                StockpileStoragePolicy.attributedTransfer(
                        before, List.of(), "Stone",
                        StockpileStoragePolicy.TransferDirection.INTO_PILE, 2));
    }

    @Test
    void confirmedCategoryPileFailsClosedWhenAnotherItemChanges() {
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Cat Gold", 29), item("Axe", 42));

        assertEquals(List.of(), StockpileStoragePolicy.attributedTransfer(
                List.of(), after, "Stone",
                StockpileStoragePolicy.TransferDirection.OUT_OF_PILE, 1));
    }

    @Test
    void confirmedPileCountCorrelatesANameVariantTransition() {
        assertEquals(1, StockpileStoragePolicy.confirmedInventoryTransitionCount(
                List.of(), List.of(item("Cat Gold", 29)),
                StockpileStoragePolicy.TransferDirection.OUT_OF_PILE));
    }

    @Test
    void unknownStoredQualityCanBeRemovedByConfirmedActualQuality() {
        assertTrue(StockpileStoragePolicy.isWithdrawalRecordMatch(
                item("Cat Gold", 29), item("Cat Gold", 0)));
        assertFalse(StockpileStoragePolicy.isWithdrawalRecordMatch(
                item("Cat Gold", 29), item("Axe", 0)));
    }

    @Test
    void withdrawalPrefersExactQualityBeforeUnknownFallback() {
        List<StockpileStoragePolicy.Item> stored = List.of(
                item("Cat Gold", 0), item("Cat Gold", 29));

        assertEquals(1, StockpileStoragePolicy.withdrawalRecordIndex(
                item("Cat Gold", 29), stored));
        assertEquals(-1, StockpileStoragePolicy.withdrawalRecordIndex(
                item("Cat Gold", 0), List.of(item("Cat Gold", 29))));
    }

    @Test
    void stackQualityResolutionIsNeverAttributedAsAStockpileTransfer() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Earth", 0), item("Earth", 0));
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Earth", 10), item("Earth", 11));

        assertEquals(List.of(), StockpileStoragePolicy.attributedTransfer(
                before, after, "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE, -1));
        assertEquals(List.of(), StockpileStoragePolicy.attributedTransfer(
                before, after, "Earth",
                StockpileStoragePolicy.TransferDirection.OUT_OF_PILE, -1));
    }

    @Test
    void pileIncreaseDiscoversDepositWithoutAnExplicitUiAction() {
        assertEquals(StockpileStoragePolicy.TransferDirection.INTO_PILE,
                StockpileStoragePolicy.directionFromPileCounts(8, 11));
    }

    @Test
    void pileDecreaseDiscoversWithdrawalWithoutAnExplicitUiAction() {
        assertEquals(StockpileStoragePolicy.TransferDirection.OUT_OF_PILE,
                StockpileStoragePolicy.directionFromPileCounts(11, 8));
        assertNull(StockpileStoragePolicy.directionFromPileCounts(8, 8));
    }

    @Test
    void delayedPileCountCanCorrelateWithEarlierInventoryTransition() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Earth", 10), item("Axe", 42));
        List<StockpileStoragePolicy.Item> after = List.of(item("Axe", 42));

        assertTrue(StockpileStoragePolicy.isMatchingInventoryTransition(
                before, after, "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE));
        assertFalse(StockpileStoragePolicy.isMatchingInventoryTransition(
                before, after, "Axe",
                StockpileStoragePolicy.TransferDirection.INTO_PILE));
    }

    @Test
    void withdrawalCorrelationRequiresExpectedPileItemToAppear() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Axe", 42));
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Axe", 42), item("Earth", 10));

        assertTrue(StockpileStoragePolicy.isMatchingInventoryTransition(
                before, after, "Earth",
                StockpileStoragePolicy.TransferDirection.OUT_OF_PILE));
        assertFalse(StockpileStoragePolicy.isMatchingInventoryTransition(
                before, after, "Branch",
                StockpileStoragePolicy.TransferDirection.OUT_OF_PILE));
    }

    @Test
    void multiUpdateShiftDepositCountsEveryMatchingTransition() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Earth", 10), item("Earth", 11), item("Axe", 42));
        List<StockpileStoragePolicy.Item> middle = List.of(
                item("Earth", 11), item("Axe", 42));
        List<StockpileStoragePolicy.Item> after = List.of(item("Axe", 42));

        assertEquals(1, StockpileStoragePolicy.matchingInventoryTransitionCount(
                before, middle, "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE));
        assertEquals(1, StockpileStoragePolicy.matchingInventoryTransitionCount(
                middle, after, "Earth",
                StockpileStoragePolicy.TransferDirection.INTO_PILE));
        assertEquals(3, StockpileStoragePolicy.confirmedPileDelta(8, 11));
    }

    private static StockpileStoragePolicy.Item item(String name, double quality) {
        return new StockpileStoragePolicy.Item(name, quality);
    }
}
