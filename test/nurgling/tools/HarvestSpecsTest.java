package nurgling.tools;

import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarvestSpecsTest {
    @Test
    void minimapTreeHarvestToggleDrivesObjHarvestSpec() {
        assertEquals(NConfig.Key.treeHarvestOverlay, HarvestSpecs.TREE.masterToggle());
        assertSame(HarvestSpecs.TREE, HarvestSpecs.forResource("gfx/terobjs/trees/oak"));
    }

    @Test
    void overlayShowsOnlyWhenGlobalAndTypeEnabled() {
        assertFalse(HarvestSpecs.overlayEnabled(false, false));
        assertFalse(HarvestSpecs.overlayEnabled(false, true));
        assertFalse(HarvestSpecs.overlayEnabled(true, false));
        assertTrue(HarvestSpecs.overlayEnabled(true, true));
    }
}
