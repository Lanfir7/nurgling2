package nurgling.conf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FontSettingsTest {
    @Test
    void startsWhenOptionalBundledFontsAreUnavailable() {
        assertDoesNotThrow(() -> {
            new FontSettings();
        });
    }
}
