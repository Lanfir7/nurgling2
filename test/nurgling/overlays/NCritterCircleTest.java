package nurgling.overlays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NCritterCircleTest {
    @Test
    void dumbledoreIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/dumbledore/dumbledore"));
    }

    @Test
    void woodScorpionIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/woodscorpion/woodscorpion"));
    }
}
