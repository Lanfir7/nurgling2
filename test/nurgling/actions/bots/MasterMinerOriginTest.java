package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerOriginTest {
    @Test
    void baselineCapturedBeforeCursorMakesExistingItemsCarried() {
        Object existing = new Object();
        MasterMiner.ItemOrigins<Object> origins = new MasterMiner.ItemOrigins<>();

        origins.observe(Arrays.asList(existing), true);

        assertEquals(MasterMiner.Origin.CARRIED, origins.get(existing).origin);
    }

    @Test
    void recreatedWidgetsWithSameItemIdentityKeepTheirOriginalOrigin() {
        Object item = new Object();
        MasterMiner.ItemOrigins<Object> origins = new MasterMiner.ItemOrigins<>();
        origins.observe(Arrays.asList(item), true);
        origins.observe(Arrays.asList(item), false);

        assertEquals(MasterMiner.Origin.CARRIED, origins.get(item).origin);
    }

    @Test
    void carriedItemsNeverRecordButMinedItemsDoExactlyOnceAcrossRetries() {
        Object carried = new Object();
        Object mined = new Object();
        MasterMiner.ItemOrigins<Object> origins = new MasterMiner.ItemOrigins<>();
        origins.observe(Arrays.asList(carried), true);
        origins.observe(Arrays.asList(mined), false);

        assertFalse(origins.claimCount(carried));
        assertFalse(origins.claimRecord(carried));
        assertTrue(origins.claimCount(mined));
        assertFalse(origins.claimCount(mined));
        assertTrue(origins.claimRecord(mined));
        assertFalse(origins.claimRecord(mined));
    }

    @Test
    void inventoryStackLeavesAndHandDiscoveryDeduplicateByItemIdentity() {
        Object inventory = new Object();
        Object stackHolder = new Object();
        Object stackLeaf = new Object();
        Map<Object, Object> widgetByItem = new LinkedHashMap<>();
        widgetByItem.put(stackHolder, stackLeaf);

        List<Object> stackLeaves = MasterMiner.orderedStackMembers(Arrays.asList(stackHolder), widgetByItem);
        List<Object> discovered = MasterMiner.withHand(Arrays.asList(inventory, stackLeaf, inventory), stackLeaf);

        assertEquals(Arrays.asList(stackLeaf), stackLeaves);
        assertEquals(Arrays.asList(inventory, stackLeaf), discovered);
    }

    @Test
    void supportReserveLimitsDropBudget() {
        assertEquals(0, MasterMiner.dropBudget(30, 30));
        assertEquals(4, MasterMiner.dropBudget(34, 30));
    }

    @Test
    void onlySingleFreshStoneCanBeRecorded() {
        assertTrue(MasterMiner.shouldRecordMined(MasterMiner.Origin.MINED, true));
        assertFalse(MasterMiner.shouldRecordMined(MasterMiner.Origin.MINED, false));
        assertFalse(MasterMiner.shouldRecordMined(MasterMiner.Origin.CARRIED, true));
    }

    @Test
    void stackLeavesAlwaysUseAmountOneDropProtocol() {
        assertTrue(MasterMiner.usesSingleDropProtocol(true, 1));
        assertTrue(MasterMiner.usesSingleDropProtocol(false, 3));
        assertFalse(MasterMiner.usesSingleDropProtocol(false, 1));
    }

    @Test
    void firstContentsLoadInheritsHolderButLaterMinedLeafDoesNot() {
        assertEquals(MasterMiner.Origin.CARRIED,
                MasterMiner.stackLeafOrigin(MasterMiner.Origin.MINED, MasterMiner.Origin.CARRIED, true));
        assertEquals(MasterMiner.Origin.MINED,
                MasterMiner.stackLeafOrigin(MasterMiner.Origin.CARRIED, MasterMiner.Origin.MINED, true));
        assertEquals(MasterMiner.Origin.MINED,
                MasterMiner.stackLeafOrigin(MasterMiner.Origin.MINED, null, true));
        assertEquals(MasterMiner.Origin.MINED,
                MasterMiner.stackLeafOrigin(MasterMiner.Origin.MINED, MasterMiner.Origin.CARRIED, false));
    }

    @Test
    void emptyStackSnapshotDoesNotConsumeFirstContentsInheritance() {
        assertFalse(MasterMiner.isFirstPopulatedContentsSnapshot(false, 0));
        assertTrue(MasterMiner.isFirstPopulatedContentsSnapshot(false, 1));
        assertFalse(MasterMiner.isFirstPopulatedContentsSnapshot(true, 1));
    }

    @Test
    void aggregateDropNeedsDepartureOrAnAmountDecreaseBeforeBudgetCanMove() {
        assertTrue(MasterMiner.dropConfirmed(true, 3, 3));
        assertTrue(MasterMiner.dropConfirmed(false, 3, 2));
        assertFalse(MasterMiner.dropConfirmed(false, 3, 3));
        assertTrue(MasterMiner.isAggregateStackAmount(2));
        assertFalse(MasterMiner.isAggregateStackAmount(1));
    }
}
