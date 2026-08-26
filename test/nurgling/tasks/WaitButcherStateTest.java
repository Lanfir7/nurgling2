package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static nurgling.tasks.WaitButcherState.State;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitButcherStateTest {
    @Test
    void onFootIdleMeansReady() {
        assertEquals(State.READY, WaitButcherState.resolve("[(<gfx/borka/idle>, Message())]", false, false, false, 0));
    }

    @Test
    void onFootButcherMeansStillWorking() {
        assertEquals(State.WORKING, WaitButcherState.resolve("gfx/borka/butcher", false, false, true, 10));
    }

    @Test
    void mountedRestAfterButcherIsReady() {
        assertEquals(State.READY, WaitButcherState.resolve("gfx/kritter/horse/idle", false, true, true, 1));
        assertEquals(State.READY, WaitButcherState.resolve("gfx/borka/riding", false, true, true, 40));
    }

    @Test
    void mountedStillButcheringIsWorking() {
        assertEquals(State.WORKING, WaitButcherState.resolve("gfx/borka/butcher", false, true, true, 10));
    }

    @Test
    void mountedWithoutButcherPoseDoesNotFinishImmediately() {
        assertEquals(State.WORKING, WaitButcherState.resolve("gfx/borka/riding", false, true, false, 10));
        assertEquals(State.READY, WaitButcherState.resolve("gfx/borka/riding", false, true, false, 200));
    }

    @Test
    void workStartTimesOutWhenMounted() {
        assertFalse(WaitButcherState.workStarted("gfx/borka/riding", true, 10));
        assertTrue(WaitButcherState.workStarted("gfx/borka/butcher", true, 1));
        assertTrue(WaitButcherState.workStarted("gfx/borka/riding", true, 200));
    }

    @Test
    void workStartOnFootStillNeedsButcherOrIdleTimeout() {
        assertFalse(WaitButcherState.workStarted("gfx/borka/walking", false, 200));
        assertTrue(WaitButcherState.workStarted("gfx/borka/idle", false, 200));
        assertTrue(WaitButcherState.workStarted("gfx/borka/butcher", false, 1));
    }

    @Test
    void fullInventoryWhileWorking() {
        assertEquals(State.NOFREESPACE, WaitButcherState.resolve("gfx/borka/butcher", true, false, true, 5));
        assertEquals(State.READY, WaitButcherState.resolve("gfx/borka/idle", true, false, true, 5));
    }
}
