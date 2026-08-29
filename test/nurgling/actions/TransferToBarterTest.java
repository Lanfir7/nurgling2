package nurgling.actions;

import haven.res.ui.barterbox.Shopbox;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferToBarterTest {

    @Test
    void exactModeDoesNotMatchLongerItemName() {
        Shopbox.ShopItem price = new Shopbox.ShopItem(null, "Fine Bone");

        assertFalse(TransferToBarter.matchesPrice(
                price, "Bone", new NAlias("Bone")));
        assertTrue(TransferToBarter.matchesPrice(
                new Shopbox.ShopItem(null, "Bone"), "Bone", new NAlias("Bone")));
    }

    @Test
    void barterIterationMustReduceMatchingInventory() {
        assertTrue(TransferToBarter.madeProgress(3, 2));
        assertFalse(TransferToBarter.madeProgress(3, 3));
    }
}
