package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecLayeredItemTest {
    @Test
    void namesLayeredMeatByBothIconResources() {
        assertEquals("Raw Badger", VSpec.nameForLayers(List.of(
                "gfx/invobjs/meat-raw", "gfx/invobjs/meat-badger")));
        assertEquals("Raw Badger", VSpec.nameForLayers(List.of(
                "gfx/invobjs/meat-badger", "gfx/invobjs/meat-raw")));
        assertEquals("Raw Ice Bear", VSpec.nameForLayers(List.of(
                "gfx/invobjs/meat-raw", "gfx/invobjs/meat-polarbear")));
        assertEquals("Raw Narwhal", VSpec.nameForLayers(List.of(
                "gfx/invobjs/meat-raw", "gfx/invobjs/meat-narwhal")));
    }

    @Test
    void treatsGenericMeatOverlayAsALayerNotAnItem() {
        assertTrue(VSpec.isLayerResource("gfx/invobjs/meat-raw"));
        assertFalse(VSpec.isKnownItemName("Meat"));
        assertTrue(VSpec.isKnownItemName("Raw Badger"));
        assertTrue(VSpec.isKnownItemName("Raw Meat"));
        assertFalse(VSpec.isLayerResource("gfx/invobjs/intestines"));
    }
}
