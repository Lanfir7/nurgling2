package nurgling.contextmenu;

import nurgling.actions.bots.DFrameFishAction;
import nurgling.actions.bots.DFrameHidesAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DryingFrameContextTest {

    @Test
    void matchesExactDryingFrameGob() {
        assertTrue(DryingFrameGobs.matches("gfx/terobjs/dframe"));
        assertFalse(DryingFrameGobs.matches("gfx/terobjs/ttub"));
        assertFalse(DryingFrameGobs.matches("gfx/terobjs/dframe/extra"));
        assertFalse(DryingFrameGobs.matches(null));
        assertFalse(DryingFrameGobs.matches(""));
    }

    @Test
    void hidesActionStartsHidesBot() {
        assertTrue(new DryHidesContextAction().create(null) instanceof DFrameHidesAction);
    }

    @Test
    void fishActionStartsFishBot() {
        assertTrue(new DryFishContextAction().create(null) instanceof DFrameFishAction);
    }
}
