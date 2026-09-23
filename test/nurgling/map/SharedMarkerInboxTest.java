package nurgling.map;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SharedMarkerInboxTest {
    @Test
    void renamedLocallyCopiedMarkerIsOfferedWithTheEditedName() {
        SharedMarkerInbox inbox = new SharedMarkerInbox();
        String original = SharedMarkerCode.encode("Barter Stand", "world-16", 42L,
                new Coord(1, 2), Color.YELLOW);
        inbox.ignore(original);
        String renamed = "Мой бартер стенд такой то" + original.substring(original.indexOf("-NGM1-"));
        org.junit.jupiter.api.Assertions.assertEquals("Мой бартер стенд такой то",
                inbox.offer(renamed, "world-16").name);
    }

    @Test
    void offersEachClipboardCodeOnlyOnceUntilClipboardChanges() {
        SharedMarkerInbox inbox = new SharedMarkerInbox();
        String first = SharedMarkerCode.encode(
            "Shop", "world-16", 42L, new Coord(1, 2), Color.YELLOW);

        assertNotNull(inbox.offer(first, "world-16"));
        assertNull(inbox.offer(first, "world-16"));
        assertNull(inbox.offer("ordinary clipboard text", "world-16"));
        assertNotNull(inbox.offer(first, "world-16"));
    }

    @Test
    void malformedAndOtherWorldCodesAreNotOffered() {
        SharedMarkerInbox inbox = new SharedMarkerInbox();
        String otherWorld = SharedMarkerCode.encode(
            "Shop", "world-15", 42L, new Coord(1, 2), Color.YELLOW);

        assertNull(inbox.offer("Shop-NGM1-broken", "world-16"));
        assertNull(inbox.offer(otherWorld, "world-16"));
    }

    @Test
    void locallyCopiedCodeCanBeIgnored() {
        SharedMarkerInbox inbox = new SharedMarkerInbox();
        String ownCode = SharedMarkerCode.encode(
            "My shop", "world-16", 42L, new Coord(1, 2), Color.YELLOW);

        inbox.ignore(ownCode);

        assertNull(inbox.offer(ownCode, "world-16"));
        assertNull(inbox.offer("ordinary clipboard text", "world-16"));
        assertNotNull(inbox.offer(ownCode, "world-16"));
    }
}
