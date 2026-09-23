package haven.res.ui.barterbox;

import org.junit.jupiter.api.Test;

import haven.Coord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopboxOfferLabelTest {
    @Test
    void quickButtonsKeepBarePresetLabelsAndClampEachActionIndependently() {
        int[] presets = {5, 20};
        for(int stock : new int[] {0, 1, 6, 20}) {
            for(int requested : presets) {
                assertEquals(Integer.toString(requested), BarterText.quickLabel(requested));
                assertEquals(Math.min(requested, stock),
                        BarterPurchase.emittedBuyCount(requested, true, stock, 500, true, true, 7));
            }
        }
    }

    @Test
    void formatsOfferedItemNameAndQuality() {
        assertEquals("Bronze Bar (Q 42.5)", OfferLabel.format("Bronze Bar", 42.5));
    }

    @Test
    void keepsTheNameVisibleWhileQualityIsUnavailable() {
        assertEquals("Bronze Bar", OfferLabel.format("Bronze Bar", null));
    }

    @Test
    void placesIdentityAtTheTopRightOfTheOfferIcon() {
        assertEquals(new Coord(43, 5), OfferLayout.identityOrigin(new Coord(5, 5), new Coord(33, 33), 5));
    }

    @Test
    void leavesBottomInsetBelowActions() {
        int actionY = OfferLayout.actionY(95, 28, 5);

        assertEquals(62, actionY);
        assertEquals(5, 95 - (actionY + 28));
    }

    @Test
    void positionsPurchaseCountEntirelyAboveActions() {
        int countTop = OfferLayout.purchaseCountTop(62, 3, 20);

        assertEquals(39, countTop);
        assertEquals(59, countTop + 20);
        assertEquals(3, 62 - (countTop + 20));
    }

    @Test
    void quickCountsClampToKnownStockAndRemoveDuplicates() {
        assertEquals(0, BarterPurchase.quickCounts(false, 20)[0]);
        assertEquals(1, BarterPurchase.quickCounts(true, 1)[0]);
        assertEquals(0, BarterPurchase.quickCounts(true, 1)[1]);
        assertEquals(1, BarterPurchase.quickCounts(true, 6)[0]);
        assertEquals(5, BarterPurchase.quickCounts(true, 6)[1]);
        assertEquals(6, BarterPurchase.quickCounts(true, 6)[2]);
        assertEquals(1, BarterPurchase.quickCounts(true, 20)[0]);
        assertEquals(5, BarterPurchase.quickCounts(true, 20)[1]);
        assertEquals(20, BarterPurchase.quickCounts(true, 20)[2]);
    }

    @Test
    void purchaseValidationExplainsInvalidAmountsAndTotals() {
        assertFalse(BarterPurchase.quantityState(null, true, 20, 500, 3).valid);
        assertFalse(BarterPurchase.quantityState(0, true, 20, 500, 3).valid);
        assertFalse(BarterPurchase.quantityState(-1, true, 20, 500, 3).valid);
        assertFalse(BarterPurchase.quantityState(501, true, 600, 500, 3).valid);
        QuantityState clamped = BarterPurchase.quantityState(21, true, 20, 500, 3);
        assertTrue(clamped.valid);
        assertEquals(20, clamped.quantity);
        QuantityState valid = BarterPurchase.quantityState(5, true, 20, 500, 3);
        assertTrue(valid.valid);
        assertEquals(5, valid.quantity);
        assertTrue(valid.message.contains("5"));
        assertTrue(valid.message.contains("15"));
    }

    @Test
    void purchaseMessagesOnlyAllowKnownAffordableStock() {
        assertTrue(BarterPurchase.canBuy(5, true, 6, 500, true, true, 2));
        assertFalse(BarterPurchase.canBuy(5, false, 6, 500, true, true, 2));
        assertTrue(BarterPurchase.canBuy(7, true, 6, 500, true, true, 2));
        assertEquals(6, BarterPurchase.clamp(20, 6, 500));
        assertEquals(6, BarterPurchase.emittedBuyCount(20, true, 6, 500, true, true, 2));
        assertEquals(0, BarterPurchase.emittedBuyCount(20, false, 6, 500, true, true, 2));
    }

    @Test
    void customQuantityShowsClampedLotsAndTotalPayment() {
        QuantityState state = BarterPurchase.quantityState(9, true, 4, 500, 3, 6);

        assertTrue(state.valid);
        assertEquals(4, state.quantity);
        assertTrue(state.message.contains("24"));
        assertTrue(state.message.contains("12"));
    }

    @Test
    void resolvedPriceSpriteStaysReadyUntilThePriceIsCleared() {
        assertTrue(BarterPurchase.priceSpriteReady(true, true, false));
        assertTrue(BarterPurchase.priceSpriteReady(true, false, true));
        assertFalse(BarterPurchase.priceSpriteReady(false, true, false));
    }

    @Test
    void totalCostUsesLongArithmeticForHighServerPrices() {
        QuantityState state = BarterPurchase.quantityState(500, true, 500, 500, Integer.MAX_VALUE, 1);

        assertTrue(state.valid);
        assertTrue(state.message.contains("1073741823500"));
    }

    @Test
    void purchaseEdgesKeepProtocolCountsSafe() {
        assertArrayEquals(new int[] {0, 0, 0}, BarterPurchase.quickCounts(true, 0));
        assertEquals(0, BarterPurchase.emittedBuyCount(1, true, 0, 500, true, true, 3));
        assertEquals(0, BarterPurchase.emittedBuyCount(1, true, 1, 500, false, true, 3));
        assertEquals(0, BarterPurchase.emittedBuyCount(1, true, 1, 500, true, false, 3));
        assertEquals(500, BarterPurchase.emittedBuyCount(500, true, 500, 500, true, true, 3));
        assertEquals(0, BarterPurchase.emittedBuyCount(501, true, 501, 500, true, true, 3));
    }

    @Test
    void priceUpdatesRecalculateTheSameDealQuantity() {
        assertTrue(BarterPurchase.quantityState(4, true, 4, 500, 3, 1).message.contains("12"));
        assertTrue(BarterPurchase.quantityState(4, true, 4, 500, 7, 1).message.contains("28"));
    }

    @Test
    void recognizesRussianLanguageWithoutSharedTranslationFiles() {
        assertTrue(BarterText.russian("ru"));
        assertFalse(BarterText.russian("en"));
    }
}
