package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NGobConeOverlayTest {
    @Test
    void dowseAndTrackingGetMapCones() {
        assertTrue(NGob.isMapConeOverlay("gfx/fx/dowse"));
        assertTrue(NGob.isMapConeOverlay("gfx/fx/track"));
    }

    @Test
    void senseThingwallGetsMapCones() {
        assertTrue(NGob.isMapConeOverlay("gfx/fx/sense"));
        assertTrue(NGob.isMapConeOverlay("gfx/fx/thingwall"));
    }

    @Test
    void worldObjectsDoNotGetMapCones() {
        assertFalse(NGob.isMapConeOverlay("gfx/terobjs/dframe"));
        assertFalse(NGob.isMapConeOverlay("gfx/terobjs/thingwall"));
        assertFalse(NGob.isMapConeOverlay(null));
    }
}
