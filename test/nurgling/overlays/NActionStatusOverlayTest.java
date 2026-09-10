package nurgling.overlays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NActionStatusOverlayTest {
    @Test
    void identifiesOnlyFleeceBearingSheepAndGoats() {
        assertEquals(NActionStatusOverlay.Status.SHEARS,
                NActionStatusOverlay.statusFor("gfx/kritter/sheep/ewe-fleece", 0));
        assertEquals(NActionStatusOverlay.Status.SHEARS,
                NActionStatusOverlay.statusFor("gfx/kritter/goat/nanny-fleece", 0));
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor("gfx/kritter/cattle/cow-fleece", 0));
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor("gfx/kritter/horse/mare-fleece", 0));
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor("gfx/kritter/pig/sow-fleece", 0));
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor("gfx/kritter/sheep/ewe", 0));
    }

    @Test
    void decodesStackFurnaceReadyAndColdFlags() {
        assertEquals(NActionStatusOverlay.Status.BAR,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x04));
        assertEquals(NActionStatusOverlay.Status.COLD,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x03));
        assertEquals(NActionStatusOverlay.Status.COLD,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x0b));
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x13));
        assertEquals(NActionStatusOverlay.Status.BAR,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x17));
        assertEquals(NActionStatusOverlay.Status.BAR_AND_COLD,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.STACK_FURNACE, 0x07));
    }

    @Test
    void decodesOnlyOreSmelterBarFlagsAsReady() {
        assertEquals(NActionStatusOverlay.Status.NONE,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.ORE_SMELTER, 0x04));
        assertEquals(NActionStatusOverlay.Status.BAR,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.ORE_SMELTER, 0x08));
        assertEquals(NActionStatusOverlay.Status.BAR,
                NActionStatusOverlay.statusFor(NActionStatusOverlay.ORE_SMELTER, 0x30));
    }

    @Test
    void recognizesOnlyTheThreeSupportedGobKinds() {
        assertTrue(NActionStatusOverlay.supports("gfx/kritter/sheep/ewe-fleece"));
        assertTrue(NActionStatusOverlay.supports(NActionStatusOverlay.STACK_FURNACE));
        assertTrue(NActionStatusOverlay.supports(NActionStatusOverlay.ORE_SMELTER));
        assertFalse(NActionStatusOverlay.supports("gfx/terobjs/fineryforge"));
        assertFalse(NActionStatusOverlay.supports("gfx/terobjs/trees/oak"));
    }
}
