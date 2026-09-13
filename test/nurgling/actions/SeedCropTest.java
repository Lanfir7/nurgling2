package nurgling.actions;

import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedCropTest {
    private static final NAlias RADISH = new NAlias("plants/radish");
    private static final NAlias WHITE_ONION = new NAlias("plants/whiteonion");
    private static final NAlias WATERMELON = new NAlias("plants/watermelon");
    private static final NAlias CARROT = new NAlias("plants/carrot");

    @Test
    void radishPlantsFromBarrelSeedsOnly() {
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(RADISH, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(RADISH, false, true));
    }

    @Test
    void whiteOnionPlantsFromStockpileOnly() {
        assertEquals(Collections.singletonList("stockpile"),
                SeedCrop.plantingSourceKinds(WHITE_ONION, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(WHITE_ONION, true, false));
    }

    @Test
    void watermelonPlantsFromBarrelSeedsOnly() {
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(WATERMELON, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(WATERMELON, false, true));
    }

    @Test
    void cropWithBothPlantableSourcesKeepsBothWhenContainersExist() {
        List<String> both = Arrays.asList("barrel", "stockpile");
        assertEquals(both, SeedCrop.plantingSourceKinds(CARROT, true, true));
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(CARROT, true, false));
        assertEquals(Collections.singletonList("stockpile"),
                SeedCrop.plantingSourceKinds(CARROT, false, true));
    }

    @Test
    void vegItemAliasExcludesSeedSibling() {
        NAlias radishVeg = SeedCrop.vegItemAlias(new NAlias("Radish"));
        assertTrue(radishVeg.matches("Radish"));
        assertFalse(radishVeg.matches("Radish Seeds"));

        NAlias onionVeg = SeedCrop.vegItemAlias(new NAlias("White Onion"));
        assertTrue(onionVeg.matches("White Onion"));
        assertFalse(onionVeg.matches("White Onion Seeds"));
    }

    @Test
    void seedItemAliasDoesNotMatchVegetable() {
        NAlias seeds = SeedCrop.seedItemAlias(new NAlias("Radish Seeds"));
        assertTrue(seeds.matches("Radish Seeds"));
        assertFalse(seeds.matches("Radish"));
        assertTrue(SeedCrop.seedItemAlias(new NAlias("Watermelon Seeds")).matches("Watermelon Seeds"));
    }
}
