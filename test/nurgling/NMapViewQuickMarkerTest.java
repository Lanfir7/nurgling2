package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMapViewQuickMarkerTest {
    @Test
    void onlyUnmodifiedAltMiddleClickCreatesQuickMarker() {
        assertTrue(QuickMapMarkerGesture.matches(2, true, false, false));
        assertFalse(QuickMapMarkerGesture.matches(2, true, true, false));
        assertFalse(QuickMapMarkerGesture.matches(2, true, false, true));
        assertFalse(QuickMapMarkerGesture.matches(2, false, false, false));
        assertFalse(QuickMapMarkerGesture.matches(1, true, false, false));
    }
}
