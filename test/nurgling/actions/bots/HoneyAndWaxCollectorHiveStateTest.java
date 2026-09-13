package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoneyAndWaxCollectorHiveStateTest {
    @Test
    void honeyBitMatchesPlainAndDecoratedHives() {
        for (long attr : new long[] {35, 39, 11, 15, 27, 31}) {
            assertTrue(HoneyAndWaxCollector.hasHoney(attr), "honey expected for " + attr);
        }
    }

    @Test
    void waxBitMatchesPlainAndDecoratedHives() {
        for (long attr : new long[] {6, 39, 15, 31}) {
            assertTrue(HoneyAndWaxCollector.hasWax(attr), "wax expected for " + attr);
        }
    }

    @Test
    void overlayOnlyAndUnrelatedBitsAreEmpty() {
        for (long attr : new long[] {0, 2, 8, 16, 32}) {
            assertFalse(HoneyAndWaxCollector.hasHoney(attr), "honey not expected for " + attr);
            assertFalse(HoneyAndWaxCollector.hasWax(attr), "wax not expected for " + attr);
        }
    }

    @Test
    void waxOnlyIsNotHoneyAndHoneyOnlyIsNotWax() {
        assertTrue(HoneyAndWaxCollector.hasWax(6));
        assertFalse(HoneyAndWaxCollector.hasHoney(6));
        assertTrue(HoneyAndWaxCollector.hasHoney(35));
        assertFalse(HoneyAndWaxCollector.hasWax(35));
    }
}
