package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPageScrollPolicyTest {
    @Test
    void exactFitDoesNotCreatePhantomOuterScrolling() {
        assertFalse(SettingsPageScrollPolicy.needsScroll(500, 500));
        assertFalse(SettingsPageScrollPolicy.needsScroll(490, 500));
        assertTrue(SettingsPageScrollPolicy.needsScroll(501, 500));
    }
}
