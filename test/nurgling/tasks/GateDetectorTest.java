package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateDetectorTest {
    @Test
    void recognizesAllSupportedGateResourcesAndRejectsNearMatches() {
        assertTrue(GateDetector.isGateName("gfx/terobjs/arch/brickbiggate"));
        assertTrue(GateDetector.isGateName("gfx/terobjs/arch/palisadegate"));
        assertFalse(GateDetector.isGateName("gfx/terobjs/arch/brickwallbiggate"));
        assertFalse(GateDetector.isGateName("gfx/terobjs/arch/palisadegate-open"));
    }
}
