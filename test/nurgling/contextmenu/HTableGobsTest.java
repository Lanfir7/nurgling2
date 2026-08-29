package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTableGobsTest {

    @Test
    void matchesHerbalistTableGobOnly() {
        assertTrue(HTableGobs.matches("gfx/terobjs/htable"));
        assertFalse(HTableGobs.matches("gfx/terobjs/table"));
        assertFalse(HTableGobs.matches("gfx/terobjs/htable/extra"));
        assertFalse(HTableGobs.matches(null));
        assertFalse(HTableGobs.matches(""));
    }
}
