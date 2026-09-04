package nurgling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NConfigDuckSettingsTest {
    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreCurrent() {
        NConfig.current = previous;
    }

    @Test
    void destructiveDuckProcessingDefaultsToEnabled() {
        NConfig.Key skipButcher = NConfig.Key.valueOf("skipButcherInDuck");
        NConfig.Key skipPlucking = NConfig.Key.valueOf("skipPluckingDrakesInDuck");
        NConfig.current = new NConfig();

        assertEquals(Boolean.FALSE, NConfig.get(skipButcher));
        assertEquals(Boolean.FALSE, NConfig.get(skipPlucking));
    }
}
