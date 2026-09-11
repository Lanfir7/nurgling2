package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SwillItemRegistryNewCropsTest {
    @Test
    void newCropProductsAndSeedsAreSwillCompatible() {
        assertTrue(SwillItemRegistry.STANDARD_SWILL.contains("Radish"));
        assertTrue(SwillItemRegistry.STANDARD_SWILL.contains("Watermelon"));
        assertTrue(SwillItemRegistry.STANDARD_SWILL.contains("Watermelon Slice"));
        assertTrue(SwillItemRegistry.STANDARD_SWILL.contains("White Onion"));
        assertTrue(SwillItemRegistry.SEED_SWILL.contains("Radish Seeds"));
        assertTrue(SwillItemRegistry.SEED_SWILL.contains("Watermelon Seeds"));
        assertTrue(SwillItemRegistry.isSwillItem("Radish"));
        assertTrue(SwillItemRegistry.isSwillItem("Watermelon Seeds"));
    }
}
