package nurgling;

import haven.KeyMatch;
import nurgling.hotkeys.Hotkeys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMapViewQuickMarkerTest {
    @Test
    void onlyUnmodifiedAltMiddleClickCreatesQuickMarker() {
        assertTrue(Hotkeys.registry().find(Hotkeys.MAP_QUICK_MARKER).current().matchesMouse(2, KeyMatch.M));
        assertFalse(Hotkeys.registry().find(Hotkeys.MAP_QUICK_MARKER).current().matchesMouse(2, KeyMatch.C | KeyMatch.M));
        assertFalse(Hotkeys.registry().find(Hotkeys.MAP_QUICK_MARKER).current().matchesMouse(2, KeyMatch.M | KeyMatch.S));
        assertFalse(Hotkeys.registry().find(Hotkeys.MAP_QUICK_MARKER).current().matchesMouse(2, 0));
        assertFalse(Hotkeys.registry().find(Hotkeys.MAP_QUICK_MARKER).current().matchesMouse(1, KeyMatch.M));
    }
}
