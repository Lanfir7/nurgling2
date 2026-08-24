package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapViewPlaceRetryTest {
    @Test
    void visualSessionRetriesLoadingSoHologramCanAppear() {
        assertTrue(PlaceHologramPolicy.retryPlaceOn(new Loading("mesh"), true));
        assertTrue(PlaceHologramPolicy.retryPlaceOn(new Loading("Waiting for resource gfx/terobjs/chest...") {}, true));
    }

    @Test
    void headlessSessionSwallowsLoadingSoStockpileBotDoesNotHang() {
        assertFalse(PlaceHologramPolicy.retryPlaceOn(new Loading("mesh"), false));
    }

    @Test
    void glPickAndStaleTreeFailuresNeverRetry() {
        assertFalse(PlaceHologramPolicy.retryPlaceOn(new NullPointerException("no env"), true));
        assertFalse(PlaceHologramPolicy.retryPlaceOn(new IllegalStateException("stale tree"), false));
    }

    @Test
    void visualPlaceNeedsLiveUiEnvAndRenderTree() {
        assertTrue(PlaceHologramPolicy.isVisualPlaceSession(true, true, true, false, false, false));
        assertFalse(PlaceHologramPolicy.isVisualPlaceSession(true, true, true, false, false, true));
        assertFalse(PlaceHologramPolicy.isVisualPlaceSession(true, true, false, false, false, false));
        assertFalse(PlaceHologramPolicy.isVisualPlaceSession(true, false, true, false, false, false));
        assertFalse(PlaceHologramPolicy.isVisualPlaceSession(true, true, true, true, false, false));
        assertFalse(PlaceHologramPolicy.isVisualPlaceSession(true, true, true, false, true, false));
    }
}
