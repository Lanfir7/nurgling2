package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LightObjectMinepyreTest {
    @Test
    void minepyreUsesTheSharedLightActionConfiguration() {
        LightObject.LightConfig config = LightObject.getConfig("gfx/terobjs/minepyre");

        assertNotNull(config);
        assertEquals("Mine Pyre", config.displayName);
        assertEquals(4, config.fireFlag);
        assertEquals(0, config.fuelFlag);
    }

    @Test
    void existingLightableObjectsRemainConfigured() {
        assertNotNull(LightObject.getConfig("gfx/terobjs/pow"));
        assertNotNull(LightObject.getConfig("gfx/terobjs/kiln"));
        assertNotNull(LightObject.getConfig("gfx/terobjs/bpyre"));
    }
}
