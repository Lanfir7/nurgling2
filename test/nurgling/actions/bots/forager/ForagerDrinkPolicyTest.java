package nurgling.actions.bots.forager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForagerDrinkPolicyTest {

    @Test
    void disabledOrRecoveredForagerSkipsDrinking() {
        ForagerDrinkPolicy.RunState state = new ForagerDrinkPolicy.RunState();
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(0.65, 4.0);

        assertEquals(ForagerDrinkPolicy.Action.SKIP,
                state.atCheckpoint(false, 0.20, true, 1_000L, settings));
        assertEquals(ForagerDrinkPolicy.Action.SKIP,
                state.atCheckpoint(true, 0.65, true, 1_000L, settings));
    }

    @Test
    void belowConfiguredThresholdDrinksBackToThatThreshold() {
        ForagerDrinkPolicy.RunState state = new ForagerDrinkPolicy.RunState();
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(0.72, 4.0);

        assertEquals(ForagerDrinkPolicy.Action.DRINK,
                state.atCheckpoint(true, 0.71, true, 1_000L, settings));
        assertEquals(0.72, settings.target(), 0.0001);
        assertEquals(4_000L, settings.retryCooldownMs());
    }

    @Test
    void failedDrinkWaitsForConfiguredCooldownBeforeRetrying() {
        ForagerDrinkPolicy.RunState state = new ForagerDrinkPolicy.RunState();
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(0.70, 3.0);

        state.recordFailedDrink(1_000L);
        assertEquals(ForagerDrinkPolicy.Action.SKIP,
                state.atCheckpoint(true, 0.20, true, 3_999L, settings));
        assertEquals(ForagerDrinkPolicy.Action.DRINK,
                state.atCheckpoint(true, 0.20, true, 4_000L, settings));
    }

    @Test
    void missingDrinkMeterDoesNotSuppressAnAvailableDrinkAction() {
        assertEquals(true, ForagerDrinkPolicy.mayHaveDrink(null));
        assertEquals(false, ForagerDrinkPolicy.mayHaveDrink(0.0));
        assertEquals(true, ForagerDrinkPolicy.mayHaveDrink(0.1));
    }

    @Test
    void successfulNoOpStillStartsConfiguredCooldown() {
        ForagerDrinkPolicy.RunState state = new ForagerDrinkPolicy.RunState();
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(0.70, 3.0);

        state.recordDrinkAttempt(true, 0.20, 1_000L, settings);
        assertEquals(ForagerDrinkPolicy.Action.SKIP,
                state.atCheckpoint(true, 0.20, true, 3_999L, settings));
        assertEquals(ForagerDrinkPolicy.Action.DRINK,
                state.atCheckpoint(true, 0.20, true, 4_000L, settings));
    }

    @Test
    void noDrinkNotifiesOnceAndNewRunResetsThatState() {
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(0.60, 5.0);
        ForagerDrinkPolicy.RunState run = new ForagerDrinkPolicy.RunState();

        assertEquals(ForagerDrinkPolicy.Action.NOTIFY_NO_DRINK,
                run.atCheckpoint(true, 0.20, false, 1_000L, settings));
        assertEquals(ForagerDrinkPolicy.Action.SKIP,
                run.atCheckpoint(true, 0.20, false, 2_000L, settings));
        assertEquals(ForagerDrinkPolicy.Action.NOTIFY_NO_DRINK,
                new ForagerDrinkPolicy.RunState().atCheckpoint(true, 0.20, false, 3_000L, settings));
    }

    @Test
    void malformedConfigurationUsesSafeDefaults() {
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(Double.NaN, -2.0);

        assertEquals(ForagerDrinkPolicy.DEFAULT_THRESHOLD, settings.target(), 0.0001);
        assertEquals(ForagerDrinkPolicy.DEFAULT_RETRY_COOLDOWN_MS, settings.retryCooldownMs());
    }
}
