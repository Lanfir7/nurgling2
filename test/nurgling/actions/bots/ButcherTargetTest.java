package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherTargetTest {
    @Test
    void knockPoseIsACarcass() {
        assertTrue(ButcherTarget.isCarcass("gfx/kritter/boar/boar", "gfx/kritter/boar/knock(v4)"));
        assertTrue(ButcherTarget.isCarcassPose("[(<gfx/kritter/boar/knock(v4)>, Message())]"));
    }

    @Test
    void livingAnimalIsNotACarcass() {
        assertFalse(ButcherTarget.isCarcass("gfx/kritter/boar/boar", "gfx/kritter/boar/idle"));
        assertFalse(ButcherTarget.isCarcassPose(null));
        assertFalse(ButcherTarget.isCarcass(null, "knock"));
        assertFalse(ButcherTarget.isCarcass("gfx/terobjs/trees/oak", "knock"));
    }

    @Test
    void clickOnCarcassIsSingleEvenIfZoneExists() {
        assertEquals(ButcherTarget.Mode.SINGLE, ButcherTarget.resolve(true, true));
        assertEquals(ButcherTarget.Mode.SINGLE, ButcherTarget.resolve(true, false));
    }

    @Test
    void visibleCarcassZoneUsesHomeMode() {
        assertEquals(ButcherTarget.Mode.ZONE, ButcherTarget.resolve(false, true));
    }

    @Test
    void noVisibleZoneAsksForLocalArea() {
        assertEquals(ButcherTarget.Mode.LOCAL, ButcherTarget.resolve(false, false));
    }
}
