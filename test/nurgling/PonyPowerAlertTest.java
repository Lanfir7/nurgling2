package nurgling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PonyPowerAlertTest {
    @BeforeEach
    void reset() {
        PonyPowerAlert.reset();
    }

    @Test
    void recognizesHorseMeters() {
        assertTrue(PonyPowerAlert.isPonyPowerMeter("gfx/hud/meter/hast"));
        assertTrue(PonyPowerAlert.isPonyPowerMeter("gfx/hud/meter/häst"));
        assertTrue(PonyPowerAlert.isPonyPowerMeter("nurgling/hud/meter/hast"));
        assertFalse(PonyPowerAlert.isPonyPowerMeter("gfx/hud/meter/stam"));
        assertFalse(PonyPowerAlert.isPonyPowerMeter("gfx/hud/meter/hp"));
        assertFalse(PonyPowerAlert.isPonyPowerMeter(null));
    }

    @Test
    void firstSampleDoesNotAlert() {
        assertFalse(PonyPowerAlert.shouldAlert(1.0));
        PonyPowerAlert.reset();
        assertFalse(PonyPowerAlert.shouldAlert(0.05));
    }

    @Test
    void alertsWhenCrossingTenPercent() {
        PonyPowerAlert.shouldAlert(1.0);
        assertFalse(PonyPowerAlert.shouldAlert(0.11));
        assertTrue(PonyPowerAlert.shouldAlert(0.10));
        assertFalse(PonyPowerAlert.shouldAlert(0.05));
    }

    @Test
    void rearmAfterRecovery() {
        PonyPowerAlert.shouldAlert(1.0);
        assertTrue(PonyPowerAlert.shouldAlert(0.09));
        assertFalse(PonyPowerAlert.shouldAlert(0.08));
        PonyPowerAlert.shouldAlert(0.50);
        assertTrue(PonyPowerAlert.shouldAlert(0.10));
    }

    @Test
    void soundResourceIsBundled() {
        assertNotNull(PonyPowerAlert.class.getResourceAsStream(PonyPowerAlert.SOUND_RESOURCE));
    }
}
