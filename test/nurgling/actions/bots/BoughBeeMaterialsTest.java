package nurgling.actions.bots;

import java.util.Arrays;

import haven.Coord2d;
import nurgling.tools.HarvestState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoughBeeMaterialsTest {
    @Test
    void pyreSpotPrefersAdjacentTileOverFarCorner() {
        Coord2d hive = new Coord2d(0, 0);
        Coord2d twoTilesNw = new Coord2d(-22, -22);
        Coord2d oneTileEast = new Coord2d(11, 0);
        Coord2d picked = BoughBeeMaterials.closestSpot(hive, Arrays.asList(twoTilesNw, oneTileEast));
        assertEquals(11.0, picked.x, 0.01);
        assertEquals(0.0, picked.y, 0.01);
        assertNull(BoughBeeMaterials.closestSpot(hive, Arrays.asList()));
    }

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
    void lightingCollectsBranchesFromTreesWithoutFuelArea() {
        assertTrue(BoughBeeMaterials.shouldCollectBranchesForLight(false, 0));
        assertTrue(BoughBeeMaterials.shouldCollectBranchesForLight(true, 1));
        assertFalse(BoughBeeMaterials.shouldCollectBranchesForLight(false, 2));
        assertFalse(BoughBeeMaterials.shouldCollectBranchesForLight(true, 2));
    }

    @Test
    void branchesComeFromTakeBranchNotBreakBranch() {
        assertTrue(BoughBeeMaterials.isBranchFlowerAction("Take branch"));
        assertFalse(BoughBeeMaterials.isBranchFlowerAction("Break branch"));
        assertFalse(BoughBeeMaterials.isBranchFlowerAction("Take bough"));
        assertFalse(BoughBeeMaterials.isBranchFlowerAction(null));
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
    void boughPyreGobMatchesPlacedPyreNotConstruction() {
        assertTrue(BoughBeeMaterials.isBoughPyreGob("gfx/terobjs/bpyre"));
        assertFalse(BoughBeeMaterials.isBoughPyreGob("gfx/terobjs/consobj"));
        assertFalse(BoughBeeMaterials.isBoughPyreGob("gfx/terobjs/pow"));
        assertFalse(BoughBeeMaterials.isBoughPyreGob("gfx/kritter/wildbees/wildbeehive"));
        assertFalse(BoughBeeMaterials.isBoughPyreGob(null));
    }

    @Test
    void wildHiveMatchesGobTooltipPath() {
        assertTrue(BoughBeeMaterials.isWildHive("gfx/kritter/wildbees/wildbeehive"));
        assertFalse(BoughBeeMaterials.isWildHive("gfx/terobjs/beehive"));
        assertFalse(BoughBeeMaterials.isWildHive("gfx/terobjs/vehicle/cart"));
        assertFalse(BoughBeeMaterials.isWildHive("gfx/terobjs/trees/oak"));
        assertFalse(BoughBeeMaterials.isWildHive(null));
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
