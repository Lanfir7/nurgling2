package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateAllCropsReadyTest {
    @Test
    void mixedHarvestStagesThreeAndFourCountAsAllReady() {
        assertTrue(ValidateAllCropsReady.allCropsReady(10, new int[] {6, 4}));
        assertEquals(10, ValidateAllCropsReady.readyCountFromStages(new int[] {6, 4}));
    }

    @Test
    void leftoverStageTwoIsNotReady() {
        assertFalse(ValidateAllCropsReady.allCropsReady(10, new int[] {6, 3}));
        assertEquals(9, ValidateAllCropsReady.readyCountFromStages(new int[] {6, 3}));
    }

    @Test
    void duplicateStageRowsMustNotInflateReadyCount() {
        // 10 plants: 6 at stage 3, 3 at stage 4, 1 leftover at stage 2.
        int[] uniqueStages = {6, 3};
        assertEquals(9, ValidateAllCropsReady.readyCountFromStages(uniqueStages));
        assertFalse(ValidateAllCropsReady.allCropsReady(10, uniqueStages));

        // Four radish registry rows (seed+veg at 3 and 4) would count the same gobs twice.
        int[] duplicatedRows = {6, 6, 3, 3};
        assertEquals(18, ValidateAllCropsReady.readyCountFromStages(duplicatedRows));
        assertFalse(ValidateAllCropsReady.allCropsReady(10, new int[] {5}));
    }

    @Test
    void emptyFieldIsReady() {
        assertTrue(ValidateAllCropsReady.allCropsReady(0, new int[] {}));
        assertEquals(0, ValidateAllCropsReady.readyCountFromStages(new int[] {}));
    }
}
