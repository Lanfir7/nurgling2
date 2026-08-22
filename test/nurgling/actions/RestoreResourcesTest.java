package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreResourcesTest {
    @Test
    void emptyWaterskinsGoToGlobalWaterZone() {
        assertTrue(RestoreResources.shouldRefillAtWaterZone(true, true));
        assertFalse(RestoreResources.shouldRefillAtWaterZone(true, false));
        assertFalse(RestoreResources.shouldRefillAtWaterZone(false, true));
    }
}
