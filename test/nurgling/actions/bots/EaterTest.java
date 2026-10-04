package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EaterTest {
    @Test
    void fullCharacterDoesNotNeedTheFoodZone() {
        assertFalse(Eater.needsFood(0.8));
        assertFalse(Eater.needsFood(0.81));
        assertFalse(Eater.needsFood(1.0));
    }

    @Test
    void hungryCharacterNeedsFood() {
        assertTrue(Eater.needsFood(0.7999));
        assertTrue(Eater.needsFood(0));
        assertTrue(Eater.needsFood(-1));
    }
}
