package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvenGobsTest {

    @Test
    void matchesOnlyTheExactOvenResource() {
        assertTrue(OvenGobs.matches("gfx/terobjs/oven"));
        assertFalse(OvenGobs.matches("gfx/terobjs/oven/extra"));
        assertFalse(OvenGobs.matches("gfx/terobjs/brickoven"));
        assertFalse(OvenGobs.matches(null));
        assertFalse(OvenGobs.matches(""));
    }
}
