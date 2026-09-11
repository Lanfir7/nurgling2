package nurgling.guarding;

import nurgling.conf.NAreaRad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerGuardSeamTest {

    @Test
    void guardOutcomeIdsRoundTrip() {
        assertEquals(GuardOutcome.LOGOUT, GuardOutcome.fromId("logout"));
        assertEquals(GuardOutcome.TRAVEL_HEARTH, GuardOutcome.fromId("travel hearth"));
        assertEquals(GuardOutcome.BREAK, GuardOutcome.fromId("nothing"));
        assertEquals("logout", GuardOutcome.LOGOUT.id());
        assertEquals("travel hearth", GuardOutcome.TRAVEL_HEARTH.id());
        assertEquals("break", GuardOutcome.BREAK.id());
    }

    @Test
    void defaultProfileHasInflightAndPreflightGuards() {
        GuardingProfile profile = GuardingProfile.withDefaults();
        assertFalse(profile.preflightGuards.isEmpty());
        assertFalse(profile.inflightGuards.isEmpty());
        assertTrue(profile.ignoreBats);
        assertFalse(profile.waterMode);
    }

    @Test
    void ratIsNotAnActiveThreatByDefaultSavedShape() {
        NAreaRad rat = new NAreaRad("gfx/kritter/rat/rat", 200);
        rat.dangerous = false;
        assertFalse(rat.isActiveThreat(false));
        assertEquals(200 * NAreaRad.DANGER_MARGIN, rat.triggerDist(), 0.001);
    }

    @Test
    void ignoreBatsExemptsBatResourceSegment() {
        NAreaRad bat = new NAreaRad("gfx/kritter/bat/bat", 50);
        assertTrue(bat.isActiveThreat(false));
        assertFalse(bat.isActiveThreat(true));
        NAreaRad notBat = new NAreaRad("gfx/kritter/combatant/combatant", 50);
        assertTrue(notBat.isActiveThreat(true));
    }
}
