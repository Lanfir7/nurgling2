package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightObjectBpyreTest {
    @Test
    void bpyreIsLitByAnySdtOrSmokeOverlay() {
        assertFalse(LightObject.isBpyreLit(0, false));
        assertTrue(LightObject.isBpyreLit(1, false));
        assertTrue(LightObject.isBpyreLit(4, false));
        assertTrue(LightObject.isBpyreLit(0, true));
    }
}
