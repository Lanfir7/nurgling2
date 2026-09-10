package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainWeatherToggleTest {
    @Test
    void disablingRainFiltersItsRenderingWetnessAndAmbientAudioOnly() {
        assertFalse(Glob.weatherEnabled("gfx/fx/rain", false));
        assertFalse(Glob.weatherEnabled("gfx/fx/wet", false));
        assertFalse(Glob.weatherEnabled("sfx/ambient/weather/wsound", false));
        assertTrue(Glob.weatherEnabled("gfx/fx/snow", false));
        assertTrue(Glob.weatherEnabled("gfx/fx/clouds", false));
        assertTrue(Glob.weatherEnabled("gfx/fx/rain", true));
    }
}
