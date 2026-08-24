package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityPickTest {
    @Test
    void emptyIsNone() {
        assertEquals(-1, QualityPick.highest(new double[0], new boolean[0]));
    }

    @Test
    void highestQualityWins() {
        assertEquals(1, QualityPick.highest(new double[]{10, 80, 40}, new boolean[]{true, false, false}));
    }

    @Test
    void tieKeepsEquipped() {
        assertEquals(0, QualityPick.highest(new double[]{50, 50}, new boolean[]{true, false}));
    }

    @Test
    void missingQualityCountsAsZero() {
        assertEquals(0, QualityPick.highest(new double[]{QualityPick.orZero(null)}, new boolean[]{true}));
        assertEquals(20.0, QualityPick.orZero(20f), 0.001);
    }
}
