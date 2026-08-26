package nurgling.tools;

import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HarvestSpecsTest {
    @Test
    void minimapTreeHarvestToggleDrivesObjHarvestSpec() {
        assertEquals(NConfig.Key.treeHarvestOverlay, HarvestSpecs.TREE.masterToggle());
        assertSame(HarvestSpecs.TREE, HarvestSpecs.forResource("gfx/terobjs/trees/oak"));
    }
}
