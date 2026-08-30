package nurgling.widgets;

import haven.Coord;
import haven.Frame;
import haven.GOut;
import haven.IBox;
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
        Frame frame = host.add(new Frame(Coord.of(80, 120), true, zeroBox()), Coord.of(10, 5));
        Widget doll = frame.add(new Widget(Coord.of(80, 120)), Coord.z);
        Widget overlay = new Widget(Coord.z);

        assertTrue(WardrobeDollOverlay.attachAsSibling(doll, overlay));

        assertSame(host, overlay.parent);
        assertNotSame(frame, overlay.parent);
        assertNotSame(doll, overlay.parent);
        assertEquals(doll.parentpos(host), overlay.c);
        assertEquals(doll.sz, overlay.sz);
    }

    @Test
    void tickReadsItemsFromOverlayParentNotWindowAboveHost() {
        Widget window = new Widget(Coord.of(240, 340));
        Widget host = window.add(new Widget(Coord.of(200, 300)), Coord.z);
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));
        Widget overlay = new Widget(Coord.z);
        WardrobeDollOverlay.attachAsSibling(doll, overlay);

        assertSame(host, WardrobeDollOverlay.overlayStatsHost(overlay));
        assertSame(window, WardrobeDollOverlay.resolveDollHost(overlay.parent));
        assertNotSame(WardrobeDollOverlay.overlayStatsHost(overlay),
                WardrobeDollOverlay.resolveDollHost(overlay.parent));
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

    @Test
    void installSkippedWithoutEquiporyHostEvenInWardrobe() {
        Widget pickerHost = new Widget(Coord.of(80, 80));
        Widget pickerDoll = pickerHost.add(new Widget(Coord.of(40, 40)), Coord.z);

        assertFalse(WardrobeDollOverlay.isPaperDollHost(pickerHost));
        assertFalse(WardrobeDollOverlay.isPaperDollHost(null));
        assertFalse(WardrobeDollOverlay.canInstall(pickerDoll, "Wardrobe", pickerHost));
        assertFalse(WardrobeDollOverlay.canInstall(pickerDoll, "Гардероб", pickerHost));
        assertFalse(WardrobeDollOverlay.installOn(pickerDoll));
        assertFalse(WardrobeDollOverlay.installFrom(pickerHost));
        assertNull(WardrobeDollOverlay.findOverlay(pickerHost));
        assertNull(pickerDoll.child);
    }

    @Test
    void equipmentWindowCaptionIsANoOp() {
        Widget host = new Widget(Coord.of(200, 300));
        Widget doll = host.add(new Widget(Coord.of(80, 120)), Coord.of(40, 0));

        assertFalse(WardrobeDollOverlay.canInstall(doll, "Equipment", host));
        assertFalse(WardrobeDollOverlay.canInstall(doll, "Экипировка", host));
        assertFalse(WardrobeDollOverlay.installOn(doll));
        assertNull(WardrobeDollOverlay.findOverlay(host));
    }

    @Test
    void wardrobeListBackdropIsDarkerThanOldTitleStrip() {
        assertTrue(WardrobeDollOverlay.LIST_BACKDROP_ALPHA > 128);
        assertTrue(WardrobeDollOverlay.HEADER_BACKDROP_ALPHA > WardrobeDollOverlay.LIST_BACKDROP_ALPHA);
    }

    @Test
    void textBlockBackdropFitsTitleAndStatsNotWholeDoll() {
        Coord title = Coord.of(80, 14);
        Coord stats = Coord.of(70, 60);
        Coord doll = Coord.of(200, 250);
        Coord block = WardrobeDollOverlay.textBlockSize(title, stats, 8, 6, doll);

        assertEquals(Coord.of(86, 88), block);
        assertTrue(block.x < doll.x);
        assertTrue(block.y < doll.y);
        assertEquals(Coord.of(80, 18),
                WardrobeDollOverlay.headerBandSize(Coord.of(18, 14), Coord.of(80, 88)));
    }

    private static boolean isDirectChild(Widget parent, Widget child) {
        for (Widget ch = parent.child; ch != null; ch = ch.next) {
            if (ch == child)
                return true;
        }
        return false;
    }

    private static IBox zeroBox() {
        return new IBox() {
            public Coord btloff() { return Coord.z; }
            public Coord ctloff() { return Coord.z; }
            public Coord bbroff() { return Coord.z; }
            public Coord cbroff() { return Coord.z; }
            public Coord bisz() { return Coord.z; }
            public Coord cisz() { return Coord.z; }
            public void draw(GOut g, Coord tl, Coord sz) {}
        };
    }
}
