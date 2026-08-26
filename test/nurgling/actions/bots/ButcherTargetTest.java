package nurgling.actions.bots;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherTargetTest {
    @Test
    void knockPoseIsACarcass() {
        assertTrue(ButcherTarget.isCarcass("gfx/kritter/boar/boar", "gfx/kritter/boar/knock(v4)"));
        assertTrue(ButcherTarget.isCarcassPose("[(<gfx/kritter/boar/knock(v4)>, Message())]"));
        assertTrue(ButcherTarget.isCarcass("gfx/kritter/horse/stallion", "gfx/kritter/horse/knock"));
        assertTrue(ButcherTarget.isCarcass("gfx/kritter/horse/horse", "gfx/kritter/horse/knock(v4)"));
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

    @Test
    void emptyFlowerMenuRetriesWhileCarcassMayHaveNewId() {
        assertFalse(ButcherTarget.giveUpOnEmptyMenu(0));
        assertFalse(ButcherTarget.giveUpOnEmptyMenu(ButcherTarget.EMPTY_MENU_RETRIES - 1));
        assertTrue(ButcherTarget.giveUpOnEmptyMenu(ButcherTarget.EMPTY_MENU_RETRIES));
    }

    @Test
    void singleModeKeepsGobIfCarcassStillNearby() {
        assertFalse(ButcherTarget.finishedSingle(true));
        assertTrue(ButcherTarget.finishedSingle(false));
    }

    @Test
    void mountedSkipsGoToWhenAlreadyInReach() {
        assertNull(ButcherTarget.mountedApproach(new Coord2d(0, 0), new Coord2d(10, 0)));
        assertNull(ButcherTarget.mountedApproach(new Coord2d(0, 0), new Coord2d(21, 0)));
    }

    @Test
    void mountedStopsShortOfCarcassInsteadOfStandingOnIt() {
        Coord2d stop = ButcherTarget.mountedApproach(new Coord2d(0, 0), new Coord2d(50, 0));
        assertEquals(30, stop.x, 0.01);
        assertEquals(0, stop.y, 0.01);
        assertEquals(ButcherTarget.MOUNTED_REACH, new Coord2d(50, 0).dist(stop), 0.01);
    }
}
