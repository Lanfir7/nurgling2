package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecTreeProductsTest {
    @Test
    void fruitAndLogProductsResolveToLivingTree() {
        assertTrue(VSpec.treeResourcesForProduct("Acacia Pod").contains("gfx/terobjs/trees/acacia"));
        assertTrue(VSpec.treeResourcesForProduct("Board of Oak").contains("gfx/terobjs/trees/oak"));
        assertTrue(VSpec.treeResourcesForProduct("Block of Oak").contains("gfx/terobjs/trees/oak"));
    }

    @Test
    void crabappleTreeUsesActualItemName() {
        assertTrue(VSpec.object.get("gfx/terobjs/trees/crabappletree").contains("Crabapples"));
        assertFalse(VSpec.object.get("gfx/terobjs/trees/crabappletree").contains("Crabapple"));
        assertTrue(VSpec.getCategory("Crabapples").contains("Seed of Tree or Bush"));
    }
}
