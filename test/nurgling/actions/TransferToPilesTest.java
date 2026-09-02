package nurgling.actions;

import nurgling.ExtraInvGroupTransfer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nurgling.actions.TransferToPiles.PileMode;

class TransferToPilesTest {

    @Test
    void failedGobShiftWaitsThenRetriesExactlyOnce() throws InterruptedException {
        StringBuilder order = new StringBuilder();
        int[] attempts = {0};

        boolean accepted = TransferToPiles.retryGobShiftTransfer(
                () -> {
                    order.append('A');
                    return ++attempts[0] == 2;
                },
                () -> order.append('P'));

        assertTrue(accepted);
        assertEquals(2, attempts[0]);
        assertEquals("APA", order.toString());
    }

    @Test
    void failedExistingPileAccessDoesNotCreateAReplacementPile() {
        assertFalse(TransferToPiles.shouldCreateNewPile(true, true));
        assertTrue(TransferToPiles.shouldCreateNewPile(true, false));
        assertFalse(TransferToPiles.shouldCreateNewPile(false, false));
    }

    @Test
    void alexandrApproachRunsOnlyForReachableNonFullPile() {
        assertTrue(TransferToPiles.shouldApproachExistingPile(0, true));
        assertFalse(TransferToPiles.shouldApproachExistingPile(0, false));
        assertFalse(TransferToPiles.shouldApproachExistingPile(31, true));
    }

    @Test
    void unreachableNonFullPileBlocksReplacementPileCreation() {
        boolean accessFailed = TransferToPiles.existingPileAccessFailed(0, false);

        assertTrue(accessFailed);
        assertFalse(TransferToPiles.shouldCreateNewPile(true, accessFailed));
    }

    @Test
    void mixedQuartzAndFlintUseTypeBulk() {
        // Quartz and Flint share the stone pile; exactName + isSameExistExact
        // reports mixedCategory=true, so Free Inventory must stay on TYPE_BULK.
        assertEquals(PileMode.TYPE_BULK, TransferToPiles.pileMode(0, true));
        assertEquals(PileMode.TYPE_BULK, TransferToPiles.pileMode(1, true));
    }

    @Test
    void mixedCategoryUsesGobShiftWhenEveryTypeRoutesToThisArea() {
        assertEquals(PileMode.GOB_SHIFT_BULK,
                TransferToPiles.pileMode(1, true, true));
        assertEquals(PileMode.TYPE_BULK,
                TransferToPiles.pileMode(1, true, false));
    }

    @Test
    void pureLoadUsesShiftClickOnStockpileGob() {
        assertEquals(PileMode.GOB_SHIFT_BULK, TransferToPiles.pileMode(0, false));
        assertEquals(PileMode.GOB_SHIFT_BULK, TransferToPiles.pileMode(1, false));
    }

    @Test
    void qualityThresholdStaysOneByOne() {
        assertEquals(PileMode.ONE_BY_ONE, TransferToPiles.pileMode(20, true));
        assertEquals(PileMode.ONE_BY_ONE, TransferToPiles.pileMode(20, false));
    }

    @Test
    void lowerThresholdBandUsesGobShiftWhenEveryAcceptedTypeGoesToThisArea() {
        assertEquals(PileMode.GOB_SHIFT_BULK,
                TransferToPiles.thresholdPileMode(false, true, false));
        assertEquals(PileMode.GOB_SHIFT_BULK,
                TransferToPiles.thresholdPileMode(true, true, true));
    }

    @Test
    void lowerThresholdBandUsesTypeBulkWhenOnlyCurrentTypeIsSafe() {
        assertEquals(PileMode.TYPE_BULK,
                TransferToPiles.thresholdPileMode(true, true, false));
        assertEquals(PileMode.ONE_BY_ONE,
                TransferToPiles.thresholdPileMode(false, false, true));
        assertEquals(PileMode.ONE_BY_ONE,
                TransferToPiles.thresholdPileMode(true, false, false));
    }

    @Test
    void everyItemMustFitTheCurrentThresholdBand() {
        assertTrue(TransferToPiles.allQualitiesInBand(
                List.of(25.0, 30.0, 39.9), 1, 40.0));
        assertFalse(TransferToPiles.allQualitiesInBand(
                List.of(25.0, 40.0), 1, 40.0));
        assertFalse(TransferToPiles.allQualitiesInBand(
                List.of(), 1, 40.0));
    }

    @Test
    void highestThresholdBandAllowsBulkWhenEveryItemIsAboveItsMinimum() {
        assertTrue(TransferToPiles.allQualitiesInBand(
                List.of(30.0, 31.0, 34.0), 30, null));
        assertFalse(TransferToPiles.allQualitiesInBand(
                List.of(29.9, 31.0), 30, null));

        assertEquals(PileMode.GOB_SHIFT_BULK,
                TransferToPiles.pileMode(30, null, false, true, false));
        assertEquals(PileMode.TYPE_BULK,
                TransferToPiles.pileMode(30, null, true, true, false));
        assertEquals(PileMode.ONE_BY_ONE,
                TransferToPiles.pileMode(30, null, false, false, false));
    }

    @Test
    void typeBulkIsExtraPanelShiftClickNotHandTake() {
        assertEquals("invxf2", ExtraInvGroupTransfer.EXTRA_SHIFT_MSG);
        List<ExtraInvGroupTransfer.Op<String>> ops = ExtraInvGroupTransfer.plan(
                List.of("a", "a", "a"), id -> "stack");
        List<Object[]> messages = ExtraInvGroupTransfer.extraShiftClickInvxf2(7, ops);
        assertEquals(3, messages.size());
        for (Object[] msg : messages) {
            assertEquals(1, msg[1]);
            assertEquals(7, msg[2]);
        }
        Object[] leftover = ExtraInvGroupTransfer.invxf2Args(
                new int[]{7}, ExtraInvGroupTransfer.TRANSFER_COUNT);
        assertEquals(1, leftover[1]);
    }

    @Test
    void oneByOneDoesNotWaitForTotalInventoryWhenUnrelatedItemsRemain() {
        assertFalse(TransferToPiles.useExactInventoryWait(PileMode.TYPE_BULK));
        assertFalse(TransferToPiles.useExactInventoryWait(PileMode.GOB_SHIFT_BULK));
        assertFalse(TransferToPiles.useExactInventoryWait(PileMode.ONE_BY_ONE));
    }

    @Test
    void typeBulkDoesNotAddAnArtificialWait() {
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.TYPE_BULK));
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.GOB_SHIFT_BULK));
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.ONE_BY_ONE));
    }

    @Test
    void typeBulkNeverUsesGobShiftForMixedLoads() {
        // Vanilla Shift-on-pile-gob dumps every type the pile accepts.
        // Loose leftovers after a stack dump are still mixed (Quartz+Flint).
        assertFalse(TransferToPiles.typeBulkUsesGobShift(false));
        assertFalse(TransferToPiles.typeBulkUsesGobShift(true));
    }

    @Test
    void gobShiftOnlyContinuesAfterStockpileFreeSpaceDecreases() {
        assertTrue(TransferToPiles.stockpileFillChanged(50, 0));
        assertTrue(TransferToPiles.stockpileFillChanged(50, 49));
        assertFalse(TransferToPiles.stockpileFillChanged(50, 50));
        assertFalse(TransferToPiles.stockpileFillChanged(50, -1));
    }

    @Test
    void gobShiftFinishesWhenItemDisappearsOrPileIsFull() {
        assertTrue(TransferToPiles.stockpileTransferFinished(true, false, 50, 50));
        assertTrue(TransferToPiles.stockpileTransferFinished(false, true, 50, 50));
        assertTrue(TransferToPiles.stockpileTransferFinished(false, false, 50, 49));
        assertFalse(TransferToPiles.stockpileTransferFinished(false, false, 50, 50));
    }

    @Test
    void typeBulkTargetsOnlyTheStockpileOpenedByTheMacro() {
        assertArrayEquals(new int[]{42}, TransferToPiles.typeBulkDestination(42));
    }

    @Test
    void typeBulkSendsInvxf2OnlyForStackedItems() {
        assertTrue(TransferToPiles.typeBulkSendsInvxf2(true, 3));
        assertFalse(TransferToPiles.typeBulkSendsInvxf2(true, 1));
        assertFalse(TransferToPiles.typeBulkSendsInvxf2(false, 1));
        List<ExtraInvGroupTransfer.Slot> matching = List.of(
                ExtraInvGroupTransfer.Slot.stack("Quartz", 10.0),
                ExtraInvGroupTransfer.Slot.oneItemStack("Quartz", 11.0),
                ExtraInvGroupTransfer.Slot.solo("Quartz", 12.0),
                ExtraInvGroupTransfer.Slot.solo("Flint", 13.0));
        List<ExtraInvGroupTransfer.Slot> stacks = TransferToPiles.typeBulkInvxf2Targets(matching);
        assertEquals(1, stacks.size());
        assertEquals("Quartz", stacks.get(0).name);
        assertTrue(stacks.get(0).stack);
        assertEquals(3, stacks.get(0).stackSize);
    }

    @Test
    void typeBulkLeftoverFlushUsesTransferAllNotInvxf2() {
        assertEquals("transfer", TransferToPiles.LEFTOVER_FLUSH_MSG);
        assertEquals(-1, TransferToPiles.LEFTOVER_FLUSH_COUNT);
        assertFalse("invxf2".equals(TransferToPiles.LEFTOVER_FLUSH_MSG));
    }

    @Test
    void typeBulkLeftoverFlushPicksOneQuartzNotFlint() {
        List<ExtraInvGroupTransfer.Slot> after = List.of(
                ExtraInvGroupTransfer.Slot.solo("Quartz", 10.0),
                ExtraInvGroupTransfer.Slot.solo("Quartz", 11.0),
                ExtraInvGroupTransfer.Slot.solo("Flint", 12.0),
                ExtraInvGroupTransfer.Slot.oneItemStack("Flint", 13.0));
        ExtraInvGroupTransfer.Slot target = TransferToPiles.leftoverFlushTarget(after, "Quartz");
        assertEquals("Quartz", target.name);
        assertTrue(ExtraInvGroupTransfer.isLeftover(target.stack, target.stackSize));
        assertNull(TransferToPiles.leftoverFlushTarget(after, "Cinnabar"));
        ExtraInvGroupTransfer.Slot firstType = TransferToPiles.leftoverFlushTarget(after, null);
        assertEquals("Quartz", firstType.name);
    }

    @Test
    void typeBulkLeftoverWaitUntilRemnantsAppear() {
        assertFalse(TransferToPiles.leftoverFlushReady(1, 0, 0));
        assertTrue(TransferToPiles.leftoverFlushReady(1, 2, 0));
        assertFalse(TransferToPiles.leftoverFlushReady(1, 1, 1));
        assertTrue(TransferToPiles.leftoverFlushReady(1, 3, 1));
        assertTrue(TransferToPiles.leftoverFlushReady(2, 0, 0));
        assertTrue(TransferToPiles.leftoverFlushReady(2, 1, 1));
        assertFalse(TransferToPiles.leftoverFlushSends(0));
        assertTrue(TransferToPiles.leftoverFlushSends(1));
    }

    @Test
    void typeBulkLeftoverDoesNotReportSuccessBeforeServerConfirmation()
            throws InterruptedException {
        int[] sends = {0};
        int[] waits = {0};

        boolean accepted = TransferToPiles.sendAndConfirmTypeBulkLeftover(
                () -> sends[0]++,
                () -> {
                    waits[0]++;
                    return false;
                });

        assertFalse(accepted);
        assertEquals(1, sends[0]);
        assertEquals(1, waits[0]);
    }

    @Test
    void freeSpaceZeroIsFullEvenIfModelAttrIsNot31() {
        assertTrue(TransferToPiles.stockpileIsFull(0, 0));
        assertTrue(TransferToPiles.stockpileIsFull(30, 0));
        assertFalse(TransferToPiles.stockpileIsFull(0, 1));
        assertTrue(TransferToPiles.stockpileIsFull(31, 5));
    }

    @Test
    void fullPileBreaksInnerLoopAndClosesWindow() {
        assertTrue(TransferToPiles.shouldLeaveOpenedPile(0, 0));
        assertTrue(TransferToPiles.shouldCloseStockpileWhenLeaving(true));
        assertFalse(TransferToPiles.shouldLeaveOpenedPile(0, 3));
        assertFalse(TransferToPiles.keepFillingOpenedPile(0, 0));
        assertTrue(TransferToPiles.keepFillingOpenedPile(0, 5));
    }

    @Test
    void emptyInventoryLeavesInnerLoopEvenIfPileHasSpace() {
        assertTrue(TransferToPiles.shouldStopFillingOpenedPile(0, 5, 0));
        assertFalse(TransferToPiles.shouldStopFillingOpenedPile(0, 5, 3));
        assertTrue(TransferToPiles.shouldStopFillingOpenedPile(0, 0, 3));
    }

    @Test
    void unknownFreeSpaceIsNotTreatedAsFull() {
        assertFalse(TransferToPiles.stockpileIsFull(0, -1));
        assertTrue(TransferToPiles.shouldStopFillingOpenedPile(0, -1, 3));
    }

    @Test
    void pileMakerDoesNotStartWhileStockpileWindowOpen() {
        assertFalse(TransferToPiles.canStartPileMaker(true));
        assertTrue(TransferToPiles.shouldCloseStockpileBeforePileMaker(true));
        assertTrue(TransferToPiles.canStartPileMaker(false));
        assertFalse(TransferToPiles.shouldCloseStockpileBeforePileMaker(false));
    }

}
