package nurgling.actions.bots.swillpiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TroughDeliveryOfferTest {
    @Test
    void aFullTroughWithCisternCannotBeRetriedAfterItRefusesSwill() {
        assertTrue(TroughDelivery.canOffer(true, false, true));
        assertFalse(TroughDelivery.canOffer(true, true, true));
        assertFalse(TroughDelivery.canOffer(true, false, false));
        assertTrue(TroughDelivery.canOffer(false, false, false));
    }
}
