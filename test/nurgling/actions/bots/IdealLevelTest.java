package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdealLevelTest {
    @Test
    void prefersDiggingOnSkewedTerrain() {
        int[] heights = {10, 10, 10, 10, 50};
        IdealLevel.Result r = IdealLevel.compute(heights, 50);
        assertEquals(17, r.ideal);
        assertEquals(50, r.current);
        assertTrue(r.dig > r.fill);
        assertEquals(33, r.dig);
        assertEquals(28, r.fill);
    }

    @Test
    void alreadyFlatAreaNeedsNoWork() {
        int[] heights = {44, 44, 44, 44};
        IdealLevel.Result r = IdealLevel.compute(heights, 44);
        assertEquals(44, r.ideal);
        assertEquals(0, r.dig);
        assertEquals(0, r.fill);
    }

    @Test
    void integerMeanStepsDownSoDigExceedsFill() {
        int[] heights = {40, 44, 48};
        IdealLevel.Result r = IdealLevel.compute(heights, 44);
        assertEquals(43, r.ideal);
        assertEquals(6, r.dig);
        assertEquals(3, r.fill);
        assertTrue(r.dig > r.fill);
    }

    @Test
    void oddSpreadPicksBelowMean() {
        int[] heights = {1, 2, 3, 4, 5};
        IdealLevel.Result r = IdealLevel.compute(heights, 5);
        assertEquals(2, r.ideal);
        assertEquals(6, r.dig);
        assertEquals(1, r.fill);
        assertTrue(r.dig > r.fill);
    }
}
