package nurgling.actions.bots;

import haven.Gob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightObjectReturnToOriginTest {

    @Test
    void singleGobPathReturnsToOriginWithoutCandelabrumAreaFetch() {
        LightObject light = new LightObject((Gob) null);
        assertTrue(light.returnsToOrigin(), "MacroKey / context-menu Light should walk back");
        assertFalse(light.allowsCandelabrumAreaFetch(), "single-Gob must not fetch candelabrum area");
    }

    @Test
    void listPathStillReturnsToOriginAndAllowsCandelabrumAreaFetch() {
        LightObject light = new LightObject(new ArrayList<>());
        assertTrue(light.returnsToOrigin());
        assertTrue(light.allowsCandelabrumAreaFetch());
    }
}
