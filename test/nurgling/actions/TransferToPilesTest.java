package nurgling.actions;

import nurgling.ExtraInvGroupTransfer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nurgling.actions.TransferToPiles.PileMode;

class TransferToPilesTest {

    @Test
    void mixedTypesWithoutQualityUseTypeBulk() {
        assertEquals(PileMode.TYPE_BULK, TransferToPiles.pileMode(0, true));
        assertEquals(PileMode.TYPE_BULK, TransferToPiles.pileMode(1, true));
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
    void stackedDumpMustNotWaitForExactInventorySize() {
        assertFalse(TransferToPiles.useExactInventoryWait(PileMode.TYPE_BULK));
        assertFalse(TransferToPiles.useExactInventoryWait(PileMode.GOB_SHIFT_BULK));
        assertTrue(TransferToPiles.useExactInventoryWait(PileMode.ONE_BY_ONE));
    }

    @Test
    void typeBulkDoesNotAddAnArtificialWait() {
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.TYPE_BULK));
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.GOB_SHIFT_BULK));
        assertFalse(TransferToPiles.waitsForInventoryUpdate(PileMode.ONE_BY_ONE));
    }

    @Test
    void typeBulkUsesOneGobShiftForLooseStackRemainders() {
        assertTrue(TransferToPiles.typeBulkUsesGobShift(false));
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

}
