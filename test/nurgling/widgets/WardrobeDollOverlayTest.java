package nurgling.widgets;

import haven.Coord;
import haven.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeDollOverlayTest {
    @Test
    void onlyWardrobeWindowCaptionInstallsOverlay() {
        assertTrue(WardrobeDollOverlay.isWardrobeCap("Wardrobe"));
        assertTrue(WardrobeDollOverlay.isWardrobeCap("wardrobe"));
        assertTrue(WardrobeDollOverlay.isWardrobeCap("Гардероб"));
        assertFalse(WardrobeDollOverlay.isWardrobeCap("Equipment"));
        assertFalse(WardrobeDollOverlay.isWardrobeCap("Экипировка"));
        assertFalse(WardrobeDollOverlay.isWardrobeCap(null));
        assertFalse(WardrobeDollOverlay.isWardrobeCap(""));
    }

    @Test
    void dollHostIsTheAvaviewParentNotASiblingInventory() {
        Widget dollWidget = new Widget(Coord.of(80, 120));
        Widget gearHost = new Widget(Coord.of(200, 300));
        Widget window = new Widget(Coord.of(240, 340));
        Widget extraInv = new Widget(Coord.of(40, 40));
        window.add(gearHost, Coord.z);
        window.add(extraInv, Coord.of(200, 0));
        gearHost.add(dollWidget, Coord.of(40, 0));

        assertSame(gearHost, WardrobeDollOverlay.resolveDollHost(dollWidget));
        assertFalse(WardrobeDollOverlay.resolveDollHost(dollWidget) == extraInv);
        assertFalse(WardrobeDollOverlay.resolveDollHost(dollWidget) == window);
    }

    @Test
    void overlayIsBoundToHostItemsNotASeparateEquiporyList() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget playerEquipory = new Widget(Coord.of(200, 300));
        Widget doll = new Widget(Coord.of(80, 120));
        host.add(doll, Coord.of(40, 0));

        assertSame(host, WardrobeDollOverlay.resolveDollHost(doll));
        assertFalse(WardrobeDollOverlay.resolveDollHost(doll) == playerEquipory);
        assertEquals(0, WardrobeDollOverlay.itemsOnDoll(playerEquipory).length);
        assertEquals(0, WardrobeDollOverlay.itemsOnDoll(host).length);
    }
}
