package nurgling.overlays;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class BarterOfferDataTest {
    @Test void decodesLiveStandAttachmentsInSlotOrderRatherThanOverlayCreationOrder() {
        // Captured from gfx/fx/eq v21 on a filled barterstand v70, without opening its window.
        BarterOfferData hemp = decode("321e00653200");
        BarterOfferData leather = decode("8c1e00653000");
        BarterOfferData straw = decode("d92200653100");
        assertEquals(2, hemp.slot); assertEquals(0x1e32, hemp.resourceId);
        assertEquals(0, leather.slot); assertEquals(0x1e8c, leather.resourceId);
        assertEquals(1, straw.slot); assertEquals(0x22d9, straw.resourceId);
    }

    @Test void acceptsBoundedSubspriteDataButRejectsTruncationAndOtherAnchors() {
        assertEquals(4, decode("01000265340003010203").slot);
        for(String invalid : new String[]{"", "0100", "010000", "0100006535", "010000653500",
                "010001653000", "010004653000", "01000265340003", "01000065300001"})
            assertNull(decode(invalid), invalid);
        assertNull(BarterOfferData.decode(null));
    }

    @Test void duplicateItemsKeepTheirSeparateOfferSlots() {
        BarterOfferData a = decode("d92200653000"), b = decode("d92200653400");
        assertEquals(a.resourceId, b.resourceId);
        assertNotEquals(a.slot, b.slot);
    }

    @Test void mapsOnlyActualItemResourcesToVerifiedInventoryCounterparts() {
        for(String name : new String[]{"hemppants", "finebonering", "carrytagh", "leatherpants", "chewingstraw"})
            assertEquals("gfx/invobjs/" + name, BarterOfferData.inventoryResource("gfx/terobjs/items/" + name));
        assertNull(BarterOfferData.inventoryResource("gfx/terobjs/barterstand-sign"));
        assertNull(BarterOfferData.inventoryResource("gfx/terobjs/items/"));
        assertNull(BarterOfferData.inventoryResource("gfx/terobjs/items/../other"));
        assertNull(BarterOfferData.inventoryResource(null));
    }

    private static BarterOfferData decode(String hex) {return BarterOfferData.decode(HexFormat.of().parseHex(hex));}
}
