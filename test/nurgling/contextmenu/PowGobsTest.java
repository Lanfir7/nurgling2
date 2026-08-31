package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowGobsTest {

    @Test
    void matchesBonfireGobOnly() {
        assertTrue(PowGobs.matches("gfx/terobjs/pow"));
        assertFalse(PowGobs.matches("gfx/terobjs/pow/extra"));
        assertFalse(PowGobs.matches("gfx/terobjs/kiln"));
        assertFalse(PowGobs.matches("gfx/terobjs/dframe"));
        assertFalse(PowGobs.matches(null));
        assertFalse(PowGobs.matches(""));
    }
}
