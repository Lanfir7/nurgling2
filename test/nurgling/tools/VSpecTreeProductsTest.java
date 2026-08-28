package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecTreeProductsTest {
    @Test
    void fruitAndLogProductsResolveToLivingTree() {
        assertTrue(VSpec.treeResourcesForProduct("Acacia Pod").contains("gfx/terobjs/trees/acacia"));
        assertTrue(VSpec.treeResourcesForProduct("Board of Oak").contains("gfx/terobjs/trees/oak"));
        assertTrue(VSpec.treeResourcesForProduct("Block of Oak").contains("gfx/terobjs/trees/oak"));
    }
}
