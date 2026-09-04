package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeItems2Test {

    @Test
    void pileWithdrawalSettlesAfterApproachBeforeOpening() throws InterruptedException {
        StringBuilder order = new StringBuilder();

        Results result = TakeItems2.approachSettleAndOpenPile(
                () -> {
                    order.append('A');
                    return Results.SUCCESS();
                },
                () -> order.append('S'),
                () -> {
                    order.append('O');
                    return Results.SUCCESS();
                });

        assertTrue(result.IsSuccess());
        assertEquals("ASO", order.toString());
    }

    @Test
    void pileWithdrawalIsClampedToInventoryCapacity() {
        assertEquals(12, TakeItems2.pileTransferCount(50, 12));
        assertEquals(0, TakeItems2.pileTransferCount(50, 0));
    }
}
