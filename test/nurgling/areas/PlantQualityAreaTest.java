package nurgling.areas;

import haven.Coord2d;
import haven.GItem;
import haven.Pair;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlantQualityAreaTest {
    @Test
    void appendsRoundedQualityAndOnlyRaisesTrailingSuffix() {
        assertEquals("Turnips [21]", PlantQualityArea.withMaximumQuality("Turnips", 20.5f));
        assertEquals("Turnips [21]", PlantQualityArea.withMaximumQuality("Turnips [21]", 20.6f));
        assertEquals("Turnips [22]", PlantQualityArea.withMaximumQuality("Turnips [21]", 21.5f));
        assertEquals("Turnips [45] [21]", PlantQualityArea.withMaximumQuality("Turnips [45] [21]", 21f));
    }

    @Test
    void rejectsMissingOrInvalidQuality() {
        assertNull(PlantQualityArea.withMaximumQuality(null, 20f));
        assertEquals("Turnips", PlantQualityArea.withMaximumQuality("Turnips", Float.NaN));
        assertEquals("Turnips", PlantQualityArea.withMaximumQuality("Turnips", -1f));
    }

    @Test
    void usesHighestQualityFromDirectAndStackMembers() {
        assertEquals(20, PlantQualityArea.roundedMaximum(item(20.4f), null));
        assertEquals(40, PlantQualityArea.roundedMaximum(item(20f), Arrays.<GItem>asList(item(39.6f), item(30f))));
        assertEquals(41, PlantQualityArea.roundedMaximum(Arrays.asList(40.4f, 40.6f)));
        assertEquals(-1, PlantQualityArea.roundedMaximum(Arrays.asList(null, Float.NaN)));
    }

    @Test
    void usesStackContentsFromTheActivatedTopLevelShell() {
        NGItem shell = item(20f);
        ItemStack stack = new ItemStack();
        stack.wmap.put(item(20f), null);
        stack.wmap.put(item(39.6f), null);
        shell.contents = stack;

        assertEquals(40, PlantQualityArea.roundedMaximum(shell));
    }

    @Test
    void usesHeldQualityOnlyWhenThereIsNoActivatedPlantingQuality() {
        assertEquals(40, PlantQualityArea.pendingOrHeldQuality(-1, 40));
        assertEquals(30, PlantQualityArea.pendingOrHeldQuality(30, 40));
        assertEquals(-1, PlantQualityArea.pendingOrHeldQuality(-1, -1));
    }

    @Test
    void identifiesOnlyCropTreeAndBushResources() {
        assertTrue(PlantQualityArea.isPlantGobResource("gfx/terobjs/plants/turnip"));
        assertTrue(PlantQualityArea.isPlantGobResource("gfx/terobjs/trees/appletree"));
        assertTrue(PlantQualityArea.isPlantGobResource("gfx/terobjs/bushes/blackberry"));
        assertFalse(PlantQualityArea.isPlantGobResource("gfx/terobjs/smelter"));
        assertFalse(PlantQualityArea.isPlantGobResource("gfx/kritter/cow/cow"));
    }

    @Test
    void findsEveryAreaIntersectedByPointOrTileSelection() {
        BoxedArea left = boxed(1, 0, 0, 11, 11);
        BoxedArea right = boxed(2, 11, 0, 22, 11);
        BoxedArea elsewhere = boxed(3, 44, 0, 55, 11);

        assertEquals(Arrays.asList(left, right),
                PlantQualityArea.intersectedAreas(Arrays.asList(left, right, elsewhere), new Coord2d(10, 0), new Coord2d(12, 11)));
        assertEquals(Arrays.asList(left),
                PlantQualityArea.intersectedAreas(Arrays.asList(left, right, elsewhere), new Coord2d(5, 5), new Coord2d(5, 5)));
    }

    @Test
    void reversedSelectionUsesOnlySelectedTileCenters() {
        BoxedArea selected = boxed(1, 0, 0, 11, 11);
        BoxedArea boundaryOnly = boxed(2, 11, 0, 22, 11);

        assertEquals(Arrays.asList(selected), PlantQualityArea.selectedTileAreas(
                Arrays.asList(selected, boundaryOnly), new haven.Coord(0, 0), new haven.Coord(0, 0), new Coord2d(11, 11)));
        assertEquals(Arrays.asList(selected, boundaryOnly), PlantQualityArea.selectedTileAreas(
                Arrays.asList(selected, boundaryOnly), new haven.Coord(1, 0), new haven.Coord(0, 0), new Coord2d(11, 11)));
    }

    @Test
    void parsesDecimalInspectQuality() {
        assertEquals(20.6d, PlantQualityArea.inspectQuality("Quality: 20.6"));
        assertEquals(21, Math.round(PlantQualityArea.inspectQuality("Quality: 20.6").floatValue()));
        assertEquals(20.5d, PlantQualityArea.inspectQuality("Quality: 20,5"));
        assertNull(PlantQualityArea.inspectQuality("No quality here"));
    }

    private static BoxedArea boxed(int id, double x1, double y1, double x2, double y2) {
        return new BoxedArea(id, new Pair<>(new Coord2d(x1, y1), new Coord2d(x2, y2)));
    }

    private static NGItem item(float quality) {
        NGItem item = new NGItem(null);
        item.quality = quality;
        return item;
    }

    static final class BoxedArea extends NArea {
        final Pair<Coord2d, Coord2d> box;

        BoxedArea(int id, Pair<Coord2d, Coord2d> box) {
            super("a" + id);
            this.id = id;
            this.box = box;
        }

        @Override
        public Pair<Coord2d, Coord2d> getRCArea() {
            return box;
        }
    }
}
