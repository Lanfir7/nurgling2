package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dewy Lady's Mantle window is inclusive 04:45–07:15 game time (285–435),
 * matching Cal.tick icon bounds. RL conversion uses worldSpeed 3.29.
 */
class DayCycleEventsTest {

    private static final double WORLD_SPEED = 3.29;

    @Test
    void dtMatchesAstronomyClock() {
        assertEquals(360, DayCycleEvents.minutesOfDay(0.25));
        assertEquals(285, DayCycleEvents.minutesOfDay(4.75 / 24.0));
        assertEquals(435, DayCycleEvents.minutesOfDay(7.25 / 24.0));
        assertEquals(DayCycleEvents.minutesOfDay(6, 0), DayCycleEvents.minutesOfDay(0.25));
    }

    @Test
    void midWindowShowsLeftNotIn() {
        DayCycleEvents.MantleEta eta = DayCycleEvents.mantleEta(6, 0, WORLD_SPEED);
        assertTrue(eta.inWindow);
        assertTrue(eta.rlHours * 60 + eta.rlMinutes > 0);
        assertEquals(0, eta.rlHours);
        assertEquals(22, eta.rlMinutes);
    }

    @Test
    void afterWindowWrapsPastMidnightUntilNextStart() {
        DayCycleEvents.MantleEta eta = DayCycleEvents.mantleEta(7, 16, WORLD_SPEED);
        assertFalse(eta.inWindow);
        assertEquals(6, eta.rlHours);
        assertEquals(31, eta.rlMinutes);
    }

    @Test
    void lateNightStillCountsAsInUntilNextDawn() {
        DayCycleEvents.MantleEta eta = DayCycleEvents.mantleEta(23, 0, WORLD_SPEED);
        assertFalse(eta.inWindow);
        assertEquals(1, eta.rlHours);
        assertEquals(44, eta.rlMinutes);
    }

    @Test
    void beforeWindowSameDayIsIn() {
        DayCycleEvents.MantleEta eta = DayCycleEvents.mantleEta(4, 0, WORLD_SPEED);
        assertFalse(eta.inWindow);
        assertEquals(0, eta.rlHours);
        assertEquals(13, eta.rlMinutes);
    }

    @Test
    void atStartInclusiveRemainingIsUntilEnd() {
        /* 04:45 is inside the window (Cal.tick uses >= 285). Remaining RL is
         * (435 - 285) / 3.29 → 00:45. */
        DayCycleEvents.MantleEta eta = DayCycleEvents.mantleEta(4, 45, WORLD_SPEED);
        assertTrue(eta.inWindow);
        assertEquals(0, eta.rlHours);
        assertEquals(45, eta.rlMinutes);
    }

    @Test
    void atEndInclusiveRemainingIsZeroThenNextMinuteIsIn() {
        /* 07:15 is still inside (Cal.tick uses <= 435); leftover RL is 00:00.
         * 07:16 leaves the window and counts forward to the next 04:45. */
        DayCycleEvents.MantleEta atEnd = DayCycleEvents.mantleEta(7, 15, WORLD_SPEED);
        assertTrue(atEnd.inWindow);
        assertEquals(0, atEnd.rlHours);
        assertEquals(0, atEnd.rlMinutes);

        DayCycleEvents.MantleEta justAfter = DayCycleEvents.mantleEta(7, 16, WORLD_SPEED);
        assertFalse(justAfter.inWindow);
        assertTrue(justAfter.rlHours * 60 + justAfter.rlMinutes > 0);
    }
}
