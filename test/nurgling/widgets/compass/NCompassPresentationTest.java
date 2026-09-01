package nurgling.widgets.compass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCompassPresentationTest {
    @Test
    void targetLabelContainsNameDistanceAndOverflow() {
        assertEquals("Alice · 12.3 m +2", NCompassPresentation.targetLabel("Alice", 12.34, 2));
        assertEquals("Quest · 5.0 m", NCompassPresentation.targetLabel("Quest", 5.0, 0));
    }

    @Test
    void directionKeysMatchWorldBearings() {
        assertEquals("compass.direction.e", NCompassPresentation.directionKey(0.0));
        assertEquals("compass.direction.s", NCompassPresentation.directionKey(Math.PI / 2));
        assertEquals("compass.direction.w", NCompassPresentation.directionKey(Math.PI));
        assertEquals("compass.direction.n", NCompassPresentation.directionKey(-Math.PI / 2));
        assertEquals("compass.direction.ne", NCompassPresentation.directionKey(-Math.PI / 4));
    }

    @Test
    void onlyCardinalDirectionsUsePrimaryEmphasis() {
        assertTrue(NCompassPresentation.isPrimaryDirection(0));
        assertTrue(NCompassPresentation.isPrimaryDirection(Math.PI / 2));
        assertTrue(NCompassPresentation.isPrimaryDirection(Math.PI));
        assertTrue(NCompassPresentation.isPrimaryDirection(-Math.PI / 2));
        assertFalse(NCompassPresentation.isPrimaryDirection(Math.PI / 4));
        assertFalse(NCompassPresentation.isPrimaryDirection(-Math.PI / 4));
    }
}
