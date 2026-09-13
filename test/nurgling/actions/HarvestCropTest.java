package nurgling.actions;

import nurgling.conf.CropRegistry;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarvestCropTest {
    private static final NAlias RADISH = new NAlias("plants/radish");

    @Test
    void radishSeedsAreNotARadishVegetable() {
        NAlias vegetable = new NAlias("Radish");
        assertTrue(vegetable.matches("Radish Seeds"), "substring NAlias is the trap");
        assertFalse(vegetable.matchesExact("Radish Seeds"));
        assertTrue(vegetable.matchesExact("Radish"));

        assertEquals(CropRegistry.StorageBehavior.STOCKPILE,
                HarvestCrop.storageForExactItemName(CropRegistry.getStages(RADISH), "Radish"));
        assertEquals(CropRegistry.StorageBehavior.BARREL,
                HarvestCrop.storageForExactItemName(CropRegistry.getStages(RADISH), "Radish Seeds"));
        assertFalse(HarvestCrop.shouldDropHarvestedItem("Radish", "Radish Seeds"));
        assertTrue(HarvestCrop.shouldDropHarvestedItem("Radish", "Radish"));
    }

    @Test
    void radishVegetableIsNotSeeds() {
        NAlias seeds = new NAlias("Radish Seeds");
        assertFalse(seeds.matchesExact("Radish"));
        assertTrue(seeds.matchesExact("Radish Seeds"));
        assertFalse(HarvestCrop.shouldDropHarvestedItem("Radish Seeds", "Radish"));
        assertTrue(HarvestCrop.shouldDropHarvestedItem("Radish Seeds", "Radish Seeds"));
        assertNull(HarvestCrop.storageForExactItemName(CropRegistry.getStages(RADISH), "Carrot"));
    }

    @Test
    void leftoverPresenceUsesExactItemName() {
        assertTrue(HarvestCrop.hasExactItemName(Arrays.asList("Radish Seeds", "Radish"), "Radish Seeds"));
        assertTrue(HarvestCrop.hasExactItemName(Arrays.asList("Radish Seeds"), "Radish Seeds"));
        assertFalse(HarvestCrop.hasExactItemName(Arrays.asList("Radish"), "Radish Seeds"));
        assertFalse(HarvestCrop.hasExactItemName(Arrays.asList("Radish Root"), "Radish Seeds"));
        assertFalse(HarvestCrop.hasExactItemName(Collections.emptyList(), "Radish Seeds"));
        assertFalse(HarvestCrop.hasExactItemName(null, "Radish Seeds"));
    }

    @Test
    void uniqueHarvestStagesDoNotDoubleWalkRadish() {
        Set<Integer> expected = new HashSet<Integer>();
        expected.add(3);
        expected.add(4);
        assertEquals(expected, HarvestCrop.uniqueHarvestStages(RADISH));
        assertEquals(2, HarvestCrop.uniqueHarvestStages(RADISH).size());
        assertEquals(4, CropRegistry.getStages(RADISH).size());
    }

    @Test
    void harvestedTransfersUseExactNameOverloads() throws Exception {
        TransferToBarrel vegBarrel = HarvestCrop.exactBarrelTransfer(null, "Radish");
        assertEquals("Radish", vegBarrel.exactName);
        assertEquals(9000, vegBarrel.th);
        assertFalse("Radish Seeds".equals(vegBarrel.exactName));

        TransferToBarrel seedBarrel = HarvestCrop.exactBarrelTransfer(null, "Radish Seeds");
        assertEquals("Radish Seeds", seedBarrel.exactName);
        assertEquals(9000, seedBarrel.th);

        TransferToContainer vegContainer = HarvestCrop.exactContainerTransfer(null, "Radish");
        assertEquals("Radish", vegContainer.exactName);
        assertEquals(-1.0, vegContainer.th, 0.0);
        assertFalse("Radish Seeds".equals(vegContainer.exactName));

        TransferToContainer seedContainer = HarvestCrop.exactContainerTransfer(null, "Radish Seeds");
        assertEquals("Radish Seeds", seedContainer.exactName);
        assertEquals(-1.0, seedContainer.th, 0.0);

        String harvestCropSrc = Files.readString(Path.of("src/nurgling/actions/HarvestCrop.java"));
        assertTrue(harvestCropSrc.contains("exactBarrelTransfer(barrel, barrelItemExactName)"));
        assertTrue(harvestCropSrc.contains("new TransferToBarrel(barrel, exactName)"));
        assertFalse(harvestCropSrc.contains("new TransferToBarrel(barrel, seedAlias)"));
        assertTrue(harvestCropSrc.contains("exactContainerTransfer(container, itemName)"));
        assertTrue(harvestCropSrc.contains("new TransferToContainer(container, exactName, Integer.valueOf(-1))"));
        assertFalse(harvestCropSrc.contains("TransferToContainer(container, exactProductAlias"));
        assertFalse(harvestCropSrc.contains("exactProductAlias"));
        assertTrue(harvestCropSrc.contains("hasExactItemName(leftoverNames, barrelItemExactName)"));
        assertFalse(harvestCropSrc.contains("getItems(seedAlias)"));
    }
}
