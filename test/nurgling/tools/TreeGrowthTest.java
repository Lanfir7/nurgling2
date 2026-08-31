package nurgling.tools;

import haven.Coord2d;
import haven.Gob;
import haven.res.lib.tree.TreeScale;
import nurgling.gattrr.NTreeDisplayScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeGrowthTest {

    private static float treeScaleForGrowth(double fraction) {
        return (float) (0.1 + 0.9 * fraction);
    }

    private static float bushScaleForGrowth(double fraction) {
        return (float) (0.3 + 0.7 * fraction);
    }

    private static TreeScale treeScale(float visual, float original) {
        Gob gob = new Gob(null, Coord2d.of(0, 0), 1);
        return new TreeScale(gob, visual, original);
    }

    @Test
    void ninetyPercentTreeIgnoresVisualResize() {
        float original = treeScaleForGrowth(0.9);
        TreeScale ts = treeScale(original * 0.5f, original);
        assertEquals(90, TreeGrowth.percent(ts, false));
    }

    @Test
    void bushFormulaUnchanged() {
        float original = bushScaleForGrowth(0.9);
        TreeScale ts = treeScale(original * 0.5f, original);
        assertEquals(90, TreeGrowth.percent(ts, true));
    }

    @Test
    void twoArgConstructorTreatsScaleAsOriginal() {
        Gob gob = new Gob(null, Coord2d.of(0, 0), 1);
        TreeScale ts = new TreeScale(gob, treeScaleForGrowth(0.9));
        assertEquals(90, TreeGrowth.percent(ts, false));
    }

    @Test
    void overlayDrawsAtOrAboveMinThresholdIncludingMature() {
        assertFalse(TreeGrowth.shouldDraw(50, 80));
        assertTrue(TreeGrowth.shouldDraw(85, 80));
        assertTrue(TreeGrowth.shouldDraw(100, 80));
    }

    @Test
    void displayScaleIsNotPartOfPercent() {
        float original = treeScaleForGrowth(0.9);
        Gob gob = new Gob(null, Coord2d.of(0, 0), 1);
        TreeScale ts = new TreeScale(gob, original, original);
        gob.setattr(new NTreeDisplayScale(gob, 0.25f));
        assertEquals(90, TreeGrowth.percent(ts, false));
    }

    @Test
    void prefersOriginalScaleWhenPositive() {
        TreeScale ts = treeScale(0.5f, 0f);
        assertEquals(Math.round(100 * (0.5 - 0.1) / 0.9), TreeGrowth.percent(ts, false));
    }
}
