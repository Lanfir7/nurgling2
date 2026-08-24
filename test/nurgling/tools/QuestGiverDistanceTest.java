package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestGiverDistanceTest {
    @Test
    void appendsRoundedMeters() {
        assertEquals("Greet Martwief - 597m", QuestGiverDistance.withMeters("Greet Martwief", 597.2));
    }

    @Test
    void keepsLineWhenUnknown() {
        assertEquals("Greet Martwief", QuestGiverDistance.withMeters("Greet Martwief", null));
        assertEquals("Greet Martwief", QuestGiverDistance.withMeters("Greet Martwief", -1.0));
    }

    @Test
    void worldDistToMeters() {
        assertEquals(597.0, QuestGiverDistance.meters(597.0 * 11.0), 0.001);
    }

    @Test
    void closerFirstUnknownLast() {
        assertTrue(QuestGiverDistance.compareMeters(10.0, 597.0) < 0);
        assertTrue(QuestGiverDistance.compareMeters(null, 10.0) > 0);
        assertEquals(0, QuestGiverDistance.compareMeters(null, null));
    }

    @Test
    void pointerTipMatchesGiver() {
        assertTrue(QuestGiverDistance.namesMatch("Martwief", "Martwief"));
        assertTrue(QuestGiverDistance.namesMatch("Martwief", "Martwief (597.2m)"));
        assertFalse(QuestGiverDistance.namesMatch("Martwief", "Marknik"));
        assertFalse(QuestGiverDistance.namesMatch("Samle", null));
    }
}
