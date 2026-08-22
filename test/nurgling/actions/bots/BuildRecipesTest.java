package nurgling.actions.bots;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRecipesTest {
    @Test
    void dryingFrameTimesGhostCount() {
        List<BuildRecipes.Line> totals = BuildRecipes.totals("Drying Frame", 24);
        assertEquals(3, totals.size());
        assertEquals("branch", totals.get(0).materialId);
        assertEquals(120, totals.get(0).count);
        assertEquals("bough", totals.get(1).materialId);
        assertEquals(48, totals.get(1).count);
        assertEquals("string", totals.get(2).materialId);
        assertEquals(48, totals.get(2).count);
    }

    @Test
    void cupboardPerBuilding() {
        List<BuildRecipes.Line> one = BuildRecipes.totals("Cupboard", 1);
        assertEquals(1, one.size());
        assertEquals("board", one.get(0).materialId);
        assertEquals(8, one.get(0).count);
    }

    @Test
    void smokeShedKeepsThatchOrBoughAsOneLine() {
        List<BuildRecipes.Line> totals = BuildRecipes.totals("Smoke Shed", 2);
        assertEquals(4, totals.size());
        assertEquals("board", totals.get(0).materialId);
        assertEquals(24, totals.get(0).count);
        assertEquals("block", totals.get(1).materialId);
        assertEquals(8, totals.get(1).count);
        assertEquals("thatch_or_bough", totals.get(2).materialId);
        assertEquals(12, totals.get(2).count);
        assertEquals("brick", totals.get(3).materialId);
        assertEquals(20, totals.get(3).count);
    }

    @Test
    void unknownOrZeroIsEmpty() {
        assertTrue(BuildRecipes.totals("Not A Building", 10).isEmpty());
        assertTrue(BuildRecipes.totals("Drying Frame", 0).isEmpty());
        assertTrue(BuildRecipes.totals(null, 5).isEmpty());
    }

    @Test
    void slugMapsMenuNameToL10nSuffix() {
        assertEquals("drying_frame", BuildRecipes.slug("Drying Frame"));
        assertEquals("wooden_chest", BuildRecipes.slug("Wooden Chest"));
    }
}
