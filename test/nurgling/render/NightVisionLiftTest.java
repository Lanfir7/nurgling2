package nurgling.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NightVisionLiftTest {
    @Test
    void vulkanNightVisionUsesTheBrightnessSlider() {
        assertEquals(0.65f, NPostFX.nightVisionLift(true, 0.65f, true), 0.0001f);
    }

    @Test
    void openGlKeepsTheLegacyLightBoost() {
        assertEquals(0f, NPostFX.nightVisionLift(true, 0.65f, false), 0.0001f);
    }

    @Test
    void disabledOrInvalidAmountDoesNotLift() {
        assertEquals(0f, NPostFX.nightVisionLift(false, 0.65f, true), 0.0001f);
        assertEquals(0f, NPostFX.nightVisionLift(true, 0f, true), 0.0001f);
        assertEquals(0f, NPostFX.nightVisionLift(true, Float.NaN, true), 0.0001f);
        assertEquals(1f, NPostFX.nightVisionLift(true, 4f, true), 0.0001f);
    }
}
