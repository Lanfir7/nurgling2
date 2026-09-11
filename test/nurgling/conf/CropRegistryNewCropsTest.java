package nurgling.conf;

import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CropRegistryNewCropsTest {
    @Test
    void radishHasSeedAndStockpileStages() {
        CropRegistry.CropStage seeds = CropRegistry.getProductByStorage(
                new NAlias("plants/radish"), CropRegistry.StorageBehavior.BARREL);
        CropRegistry.CropStage crop = CropRegistry.getProductByStorage(
                new NAlias("plants/radish"), CropRegistry.StorageBehavior.STOCKPILE);

        assertNotNull(seeds);
        assertEquals(2, seeds.stage);
        assertEquals("Radish Seeds", seeds.result.getDefault());
        assertNotNull(crop);
        assertEquals(4, crop.stage);
        assertEquals("Radish", crop.result.getDefault());
    }

    @Test
    void watermelonHarvestYieldsSeedsLikePumpkin() {
        CropRegistry.CropStage seeds = CropRegistry.getProductByStorage(
                new NAlias("plants/watermelon"), CropRegistry.StorageBehavior.BARREL);

        assertNotNull(seeds);
        assertEquals(4, seeds.stage);
        assertEquals("Watermelon Seeds", seeds.result.getDefault());
    }

    @Test
    void whiteOnionIsReplantedFromTheOnionItself() {
        CropRegistry.CropStage onion = CropRegistry.getProductByStorage(
                new NAlias("plants/whiteonion"), CropRegistry.StorageBehavior.STOCKPILE);

        assertNotNull(onion);
        assertEquals(3, onion.stage);
        assertEquals("White Onion", onion.result.getDefault());
    }
}
