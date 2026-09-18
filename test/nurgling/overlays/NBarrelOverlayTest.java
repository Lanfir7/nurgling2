package nurgling.overlays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NBarrelOverlayTest {
    @Test
    void readsBarrelContentFromResourcePath() {
        assertEquals("water", NBarrelOverlay.contentName("gfx/terobjs/barrel-water"));
        assertEquals("milk", NBarrelOverlay.contentName("gfx/terobjs/barrel-milk"));
    }

    @Test
    void ignoresCarryPolesAndOtherNonContentOverlays() {
        assertNull(NBarrelOverlay.contentName("gfx/terobjs/items/carrytagpole"));
        assertNull(NBarrelOverlay.contentName("gfx/terobjs/barrel"));
        assertNull(NBarrelOverlay.contentName(null));
        assertNull(NBarrelOverlay.contentName(""));
    }

    @Test
    void doesNotParseSpriteToStringAsContent() {
        assertNull(NBarrelOverlay.contentName(
                "#<Billpole gfx/terobjs/items/carrytagpole of haven.Gob$Overlay@79ad08b>"));
    }
}
