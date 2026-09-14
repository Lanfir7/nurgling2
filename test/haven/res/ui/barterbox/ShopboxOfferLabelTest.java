package haven.res.ui.barterbox;

import org.junit.jupiter.api.Test;

import haven.Coord;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopboxOfferLabelTest {
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
}
