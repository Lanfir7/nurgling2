package nurgling.overlays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NModelBoxFirstDrawTest {
    @Test
    void drawsWhenShowTogglesOnWithReadySlot() {
        assertTrue(NModelBox.needsFirstDraw(true, false, true));
    }

    @Test
    void drawsWhenSlotArrivesAfterShowAlreadyOn() {
        assertTrue(NModelBox.needsFirstDraw(true, false, true));
    }

    @Test
    void waitsUntilSlotIsReady() {
        assertFalse(NModelBox.needsFirstDraw(true, false, false));
    }

    @Test
    void doesNotRedrawWhenAlreadyVisible() {
        assertFalse(NModelBox.needsFirstDraw(true, true, true));
    }
}
