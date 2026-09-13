package nurgling.conf;

import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropRegistryNewCropsTest {
    private static final NAlias RADISH = new NAlias("plants/radish");
    private static final NAlias WATERMELON = new NAlias("plants/watermelon");
    private static final NAlias WHITE_ONION = new NAlias("plants/whiteonion");

    @Test
    void radishStagesThreeAndFourYieldSeedsAndVegetables() {
        List<CropRegistry.CropStage> stages = CropRegistry.getStages(RADISH);
        assertEquals(4, stages.size());

        assertStage(stages.get(0), 3, "Radish Seeds", CropRegistry.StorageBehavior.BARREL, true);
        assertStage(stages.get(1), 3, "Radish", CropRegistry.StorageBehavior.STOCKPILE, false);
        assertStage(stages.get(2), 4, "Radish Seeds", CropRegistry.StorageBehavior.BARREL, true);
        assertStage(stages.get(3), 4, "Radish", CropRegistry.StorageBehavior.STOCKPILE, false);
    }

    @Test
    void radishStockpileProductIsNotPlantingMaterial() {
        CropRegistry.CropStage veg = CropRegistry.getProductByStorage(RADISH, CropRegistry.StorageBehavior.STOCKPILE);
        assertNotNull(veg);
        assertEquals("Radish", veg.result.getDefault());
        assertFalse(veg.isPlantingMaterial);

        CropRegistry.CropStage barrel = CropRegistry.getProductByStorage(RADISH, CropRegistry.StorageBehavior.BARREL);
        assertNotNull(barrel);
        assertEquals("Radish Seeds", barrel.result.getDefault());
        assertTrue(barrel.isPlantingMaterial);

        assertNull(CropRegistry.getPlantingProductByStorage(RADISH, CropRegistry.StorageBehavior.STOCKPILE));
        CropRegistry.CropStage planting = CropRegistry.getPlantingProductByStorage(RADISH, CropRegistry.StorageBehavior.BARREL);
        assertNotNull(planting);
        assertEquals("Radish Seeds", planting.result.getDefault());
    }

    @Test
    void radishHarvestStagesAreUniqueAndSkipStageTwo() {
        Set<Integer> expected = new HashSet<Integer>();
        expected.add(3);
        expected.add(4);
        assertEquals(expected, CropRegistry.harvestStageNumbers(RADISH));
        assertFalse(CropRegistry.isHarvestableStage(RADISH, 2));
        assertTrue(CropRegistry.isHarvestableStage(RADISH, 3));
        assertTrue(CropRegistry.isHarvestableStage(RADISH, 4));
    }

    @Test
    void watermelonHarvestYieldsSeedsLikePumpkin() {
        CropRegistry.CropStage seeds = CropRegistry.getProductByStorage(
                WATERMELON, CropRegistry.StorageBehavior.BARREL);

        assertNotNull(seeds);
        assertEquals(4, seeds.stage);
        assertEquals("Watermelon Seeds", seeds.result.getDefault());
        assertTrue(seeds.isPlantingMaterial);
        assertNull(CropRegistry.getProductByStorage(WATERMELON, CropRegistry.StorageBehavior.STOCKPILE));
        CropRegistry.CropStage planting = CropRegistry.getPlantingProductByStorage(
                WATERMELON, CropRegistry.StorageBehavior.BARREL);
        assertNotNull(planting);
        assertEquals("Watermelon Seeds", planting.result.getDefault());
    }

    @Test
    void whiteOnionIsReplantedFromTheOnionItself() {
        CropRegistry.CropStage onion = CropRegistry.getProductByStorage(
                WHITE_ONION, CropRegistry.StorageBehavior.STOCKPILE);

        assertNotNull(onion);
        assertEquals(3, onion.stage);
        assertEquals("White Onion", onion.result.getDefault());
        assertTrue(onion.isPlantingMaterial);
        assertNull(CropRegistry.getProductByStorage(WHITE_ONION, CropRegistry.StorageBehavior.BARREL));
        CropRegistry.CropStage planting = CropRegistry.getPlantingProductByStorage(
                WHITE_ONION, CropRegistry.StorageBehavior.STOCKPILE);
        assertNotNull(planting);
        assertEquals("White Onion", planting.result.getDefault());
        assertNull(CropRegistry.getPlantingProductByStorage(WHITE_ONION, CropRegistry.StorageBehavior.BARREL));
    }

    @Test
    void hybridTrellisConstructorDefaultsPlantingMaterial() {
        CropRegistry.CropStage pea = new CropRegistry.CropStage(
                4, new NAlias("Peapods"), CropRegistry.StorageBehavior.STOCKPILE, true);
        assertTrue(pea.isHybridTrellis);
        assertTrue(pea.isPlantingMaterial);

        CropRegistry.CropStage cucumberSeeds = CropRegistry.getProductByStorage(
                new NAlias("plants/cucumber"), CropRegistry.StorageBehavior.BARREL);
        assertNotNull(cucumberSeeds);
        assertTrue(cucumberSeeds.isHybridTrellis);
        assertTrue(cucumberSeeds.isPlantingMaterial);
    }

    private static void assertStage(CropRegistry.CropStage stage, int expectedStage, String name,
                                    CropRegistry.StorageBehavior storage, boolean planting) {
        assertNotNull(stage);
        assertEquals(expectedStage, stage.stage);
        assertEquals(name, stage.result.getDefault());
        assertEquals(storage, stage.storageBehavior);
        assertEquals(planting, stage.isPlantingMaterial);
    }
}
