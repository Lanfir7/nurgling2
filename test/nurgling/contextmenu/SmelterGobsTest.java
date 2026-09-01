package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmelterGobsTest {

    @Test
    void matchesOreAndSmithSmelterNotStackFurnace() {
        assertTrue(SmelterGobs.matches("gfx/terobjs/smelter"));
        assertFalse(SmelterGobs.matches("gfx/terobjs/primsmelter"));
        assertFalse(SmelterGobs.matches("gfx/terobjs/kiln"));
        assertFalse(SmelterGobs.matches(null));
        assertFalse(SmelterGobs.matches(""));
    }
}
