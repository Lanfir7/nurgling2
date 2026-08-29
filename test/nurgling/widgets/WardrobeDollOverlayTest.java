package nurgling.widgets;

import haven.Coord;
import haven.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void overlayIsSiblingOnHostNotChildOfDoll() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));
        Widget overlay = new Widget(Coord.of(1, 1));

        assertTrue(WardrobeDollOverlay.attachAsSibling(doll, overlay));

        assertSame(host, overlay.parent);
        assertNotSame(doll, overlay.parent);
        assertFalse(isDirectChild(doll, overlay));
        assertTrue(isDirectChild(host, overlay));
        assertEquals(Coord.of(40, 0), overlay.c);
        assertEquals(Coord.of(80, 120), overlay.sz);
    }

    @Test
    void overlayIsNotParentedToImmediateWrapperWhenHostIsResolved() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));
        Widget overlay = new Widget(Coord.z);

        WardrobeDollOverlay.attachAsSibling(doll, overlay);

        assertSame(WardrobeDollOverlay.resolveDollHost(doll), overlay.parent);
        assertNotSame(doll, overlay.parent);
    }

    @Test
    void installDoesNotRelyOnDollDrawingChildren() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));
        Widget overlay = new Widget(Coord.z);

        WardrobeDollOverlay.attachAsSibling(doll, overlay);

        assertNull(doll.child);
        assertSame(host, overlay.parent);
    }

    @Test
    void overlayStaysGluedToDollRectOnHost() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));
        Widget overlay = new Widget(Coord.z);
        WardrobeDollOverlay.attachAsSibling(doll, overlay);

        doll.move(Coord.of(55, 12));
        doll.resize(Coord.of(90, 130));
        WardrobeDollOverlay.syncOverlayToDoll(overlay, doll);

        assertEquals(Coord.of(55, 12), overlay.c);
        assertEquals(Coord.of(90, 130), overlay.sz);
        assertSame(host, overlay.parent);
    }

    @Test
    void installOnRequiresWardrobeWindowAndDoesNotTouchEquipment() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));

        assertFalse(WardrobeDollOverlay.installOn(doll));
        assertNull(WardrobeDollOverlay.findOverlay(host));
        assertNull(doll.child);
    }

    private static boolean isDirectChild(Widget parent, Widget child) {
        for (Widget ch = parent.child; ch != null; ch = ch.next) {
            if (ch == child)
                return true;
        }
        return false;
    }
}
