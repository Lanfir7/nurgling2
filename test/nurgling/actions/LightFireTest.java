package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightFireTest {
    @Test
    void twoBranchPiecesInInventorySkipFuelArea() {
        assertFalse(LightFire.needsFuelArea(2));
        assertFalse(LightFire.needsFuelArea(5));
    }

    @Test
    void fewerThanTwoBranchPiecesNeedFuelArea() {
        assertTrue(LightFire.needsFuelArea(0));
        assertTrue(LightFire.needsFuelArea(1));
    }

    @Test
    void stopWaitingForFirebrandWhenInHandOrProgressOrTimeout() {
        assertTrue(LightFire.craftWaitDone(true, false, 0, 200));
        assertTrue(LightFire.craftWaitDone(false, true, 0, 200));
        assertFalse(LightFire.craftWaitDone(false, false, 0, 200));
        assertFalse(LightFire.craftWaitDone(false, false, 199, 200));
        assertTrue(LightFire.craftWaitDone(false, false, 200, 200));
    }

    @Test
    void lightFireRecipeMatchesEnglishName() {
        assertTrue(LightFire.isLightFireRecipe("Light fire"));
        assertTrue(LightFire.isLightFireRecipe("light fire"));
        assertFalse(LightFire.isLightFireRecipe("Wrought Iron"));
        assertFalse(LightFire.isLightFireRecipe("Pyrite Spark"));
        assertFalse(LightFire.isLightFireRecipe(null));
    }

    @Test
    void lightFireCraftsOneFirebrandNotCraftAll() {
        assertEquals(0, LightFire.MAKE_ONE);
        assertEquals(1, LightFire.MAKE_ALL);
    }

    @Test
    void lightingClocksAreTheProgWidgetNotThePercent() {
        assertFalse(LightFire.hasClocks(false));
        assertTrue(LightFire.hasClocks(true));
    }

    @Test
    void lightingIsNotDoneUntilClocksAppearedAndVanished() {
        assertFalse(LightFire.lightingUseFinished(false, false));
        assertFalse(LightFire.lightingUseFinished(true, true));
        assertTrue(LightFire.lightingUseFinished(false, true));
    }

    @Test
    void leftoverCraftClocksMustClearBeforeUse() {
        assertFalse(LightFire.leftoverClocksCleared(true));
        assertTrue(LightFire.leftoverClocksCleared(false));
    }

    @Test
    void doNotDropFirebrandWhileLightingClocks() {
        assertFalse(LightFire.shouldDropFirebrand(true, true));
        assertTrue(LightFire.shouldDropFirebrand(true, false));
        assertFalse(LightFire.shouldDropFirebrand(false, false));
    }
}
