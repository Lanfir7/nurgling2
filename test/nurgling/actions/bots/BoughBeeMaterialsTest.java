package nurgling.actions.bots;

import nurgling.tools.HarvestState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoughBeeMaterialsTest {
    @Test
    void stackedBoughsCountAsPieces() {
        int have = BoughBeeMaterials.stackPieces(3) + BoughBeeMaterials.stackPieces(null);
        assertEquals(4, have);
        assertEquals(0, BoughBeeMaterials.boughsNeeded(have));
        assertEquals(1, BoughBeeMaterials.boughsNeeded(BoughBeeMaterials.stackPieces(3)));
    }

    @Test
    void boughsStopAtFour() {
        assertEquals(4, BoughBeeMaterials.boughsNeeded(0));
        assertEquals(1, BoughBeeMaterials.boughsNeeded(3));
        assertEquals(0, BoughBeeMaterials.boughsNeeded(4));
        assertEquals(0, BoughBeeMaterials.boughsNeeded(6));
    }

    @Test
    void branchesNeedAtLeastTwo() {
        assertTrue(BoughBeeMaterials.needsBranches(0));
        assertTrue(BoughBeeMaterials.needsBranches(1));
        assertFalse(BoughBeeMaterials.needsBranches(2));
    }

    @Test
    void nearbyPyreWithinThreeTilesSkipsBuild() {
        assertTrue(BoughBeeMaterials.isNearbyPyre(3 * 11.0, 11.0));
        assertTrue(BoughBeeMaterials.isNearbyPyre(32.9, 11.0));
        assertFalse(BoughBeeMaterials.isNearbyPyre(3 * 11.0 + 0.1, 11.0));
        assertFalse(BoughBeeMaterials.isNearbyPyre(Double.POSITIVE_INFINITY, 11.0));
    }

    @Test
    void boughPyreIsABuildNotACraft() {
        assertTrue(BoughBeeMaterials.isPyreBuild("gfx/terobjs/bpyre", null));
        assertTrue(BoughBeeMaterials.isPyreBuild("gfx/terobjs/consobj", "gfx/terobjs/bpyre"));
        assertFalse(BoughBeeMaterials.isPyreBuild("gfx/terobjs/consobj", "gfx/terobjs/pow"));
        assertFalse(BoughBeeMaterials.isPyreBuild("gfx/terobjs/pow", null));
    }

    @Test
    void pyreConstructionWindowMatchesLooseCaption() {
        assertTrue(BoughBeeMaterials.isPyreWindowCap("Bough Pyre"));
        assertTrue(BoughBeeMaterials.isPyreWindowCap("bough pyre"));
        assertFalse(BoughBeeMaterials.isPyreWindowCap("Chest"));
        assertFalse(BoughBeeMaterials.isPyreWindowCap(null));
    }

    @Test
    void hiveMustBeWithinFiveTiles() {
        assertTrue(BoughBeeMaterials.isHiveInRange(5 * 11.0, 11.0));
        assertFalse(BoughBeeMaterials.isHiveInRange(5 * 11.0 + 0.1, 11.0));
    }

    @Test
    void emptyInventoryCannotStartPyreConstruction() {
        assertFalse(BoughBeeMaterials.hasBoughsForPyre(0));
        assertFalse(BoughBeeMaterials.hasBoughsForPyre(3));
        assertTrue(BoughBeeMaterials.hasBoughsForPyre(4));
    }

    @Test
    void livingBoughTreesMatchHarvestState() {
        assertTrue(BoughBeeMaterials.isLivingTree("gfx/terobjs/trees/spruce"));
        assertFalse(BoughBeeMaterials.isLivingTree("gfx/terobjs/trees/sprucelog"));
        assertFalse(BoughBeeMaterials.isLivingTree("gfx/terobjs/trees/sprucestump"));
        assertTrue(HarvestState.hasBough("spruce"));
        assertFalse(HarvestState.hasBough("oak"));
        assertTrue(BoughBeeMaterials.isBoughTree("gfx/terobjs/trees/spruce"));
        assertFalse(BoughBeeMaterials.isBoughTree("gfx/terobjs/trees/oak"));
    }
}
