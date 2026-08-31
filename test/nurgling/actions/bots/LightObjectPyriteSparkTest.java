package nurgling.actions.bots;

import nurgling.actions.LightFire;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightObjectPyriteSparkTest {
    @Test
    void skipsWhenNoCatGold() {
        assertTrue(LightFire.shouldSkipPyriteSparkTier(false, true));
        assertTrue(LightFire.shouldSkipPyriteSparkTier(false, false));
    }

    @Test
    void skipsWhenRecipeMissingEvenWithCatGold() {
        assertTrue(LightFire.shouldSkipPyriteSparkTier(true, false));
    }

    @Test
    void runsWhenCatGoldAndPyriteSparkRecipePresent() {
        assertFalse(LightFire.shouldSkipPyriteSparkTier(true, true));
    }

    @Test
    void usesPyriteSparkRecipeNotLightFire() {
        assertEquals("Pyrite Spark", LightFire.RECIPE_PYRITE_SPARK);
        assertEquals("Cat Gold", LightFire.CAT_GOLD);
        assertTrue(LightFire.isPyriteSparkRecipe("Pyrite Spark"));
        assertTrue(LightFire.isPyriteSparkRecipe("pyrite spark"));
        assertFalse(LightFire.isPyriteSparkRecipe("Light fire"));
        assertFalse(LightFire.isLightFireRecipe("Pyrite Spark"));
        assertTrue(LightFire.isLightFireRecipe("Light fire"));
    }

    @Test
    void pyriteSparkRunsAfterNeighborStickAndBeforeTwoBranch() {
        assertTrue(LightObject.PYRITE_SPARK_PRIORITY > LightObject.NEIGHBOR_STICK_PRIORITY);
        assertTrue(LightObject.BRANCH_FIREBRAND_PRIORITY > LightObject.PYRITE_SPARK_PRIORITY);
        assertEquals(6, LightObject.NEIGHBOR_STICK_PRIORITY);
        assertEquals(7, LightObject.PYRITE_SPARK_PRIORITY);
        assertEquals(8, LightObject.BRANCH_FIREBRAND_PRIORITY);
    }
}
