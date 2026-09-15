package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetSpeedBotTest {
    @Test
    void restoresRunForMissingOrInvalidSavedSpeed() {
        assertEquals(2, SetSpeedBot.normalizedSpeed(null));
        assertEquals(2, SetSpeedBot.normalizedSpeed("sprint"));
        assertEquals(2, SetSpeedBot.normalizedSpeed(-1));
        assertEquals(2, SetSpeedBot.normalizedSpeed(4));
        assertEquals(3, SetSpeedBot.normalizedSpeed(3L));
    }

    @Test
    void limitsRequestedSpeedToServerMaximum() {
        assertEquals(1, SetSpeedBot.availableSpeed(3, 1));
        assertEquals(2, SetSpeedBot.availableSpeed(2, 3));
        assertEquals(-1, SetSpeedBot.availableSpeed(2, -1));
    }
}
