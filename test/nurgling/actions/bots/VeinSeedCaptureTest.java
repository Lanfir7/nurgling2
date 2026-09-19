package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinSeedCaptureTest {
    @Test
    void offerIgnoredUntilArmed() {
        VeinSeedCapture cap = new VeinSeedCapture();
        assertFalse(cap.offer(new Coord(1, 2), new Coord(1, 2)));
        assertNull(cap.peek());
    }

    @Test
    void firstArmedSelBecomesSeedAndDisarms() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        assertTrue(cap.isArmed());
        assertTrue(cap.offer(new Coord(3, 4), new Coord(3, 4)));
        assertEquals(new Coord(3, 4), cap.peek());
        assertFalse(cap.isArmed());
        assertFalse(cap.offer(new Coord(9, 9), new Coord(9, 9)));
        assertEquals(new Coord(3, 4), cap.peek());
    }

    @Test
    void disarmDropsWaitWithoutKeepingLaterSel() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        cap.disarm();
        assertFalse(cap.offer(new Coord(0, 0), new Coord(0, 0)));
        assertNull(cap.peek());
    }
}
