package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergedBagLayoutTest {
    @Test
    void sameKindPouchesMergeAndOtherContainersDoNot() {
        assertTrue(MergedBagLayout.shouldMerge("Seedbag", 2));
        assertTrue(MergedBagLayout.shouldMerge("Poacher's Pouch", 3));
        assertFalse(MergedBagLayout.shouldMerge("Seedbag", 1));
        assertFalse(MergedBagLayout.shouldMerge("Chest", 4));
        assertEquals("Silk Purse", PouchKinds.key(null, null, "gfx/invobjs/silkpurse"));
        assertEquals("Silk Purse", PouchKinds.key("Silk Purse", "Inventory", null));
        assertEquals("Seedbag", PouchKinds.key(null, "Seedbag", null));
        assertEquals(null, PouchKinds.key("Chest", "Chest", "gfx/invobjs/chest"));
    }

    @Test
    void pouchesStayOnOneRowUntilTheWidthRunsOut() {
        List<MergedBagLayout.Box> boxes = MergedBagLayout.place(
                Arrays.asList(Coord.of(10, 10), Coord.of(10, 10), Coord.of(10, 10)),
                4, 50, 2);
        assertEquals(2, boxes.get(0).x);
        assertEquals(16, boxes.get(1).x);
        assertEquals(30, boxes.get(2).x);
        assertEquals(2, boxes.get(0).y);
        assertEquals(2, boxes.get(2).y);
        assertEquals(Coord.of(42, 14), MergedBagLayout.bounds(boxes, 2));
        assertEquals(2, MergedBagLayout.seams(boxes, 4, 2).size());
    }

    @Test
    void extraPouchesWrapAndGetAHorizontalSplit() {
        List<MergedBagLayout.Box> boxes = MergedBagLayout.place(
                Arrays.asList(Coord.of(10, 8), Coord.of(10, 8), Coord.of(10, 8)),
                4, 30, 0);
        assertEquals(0, boxes.get(0).x);
        assertEquals(14, boxes.get(1).x);
        assertEquals(0, boxes.get(2).x);
        assertEquals(12, boxes.get(2).y);
        List<MergedBagLayout.Box> seams = MergedBagLayout.seams(boxes, 4, 2);
        assertEquals(2, seams.size());
        assertTrue(seams.get(0).h > seams.get(0).w);
        assertTrue(seams.get(1).w > seams.get(1).h);
    }

    @Test
    void aSingleWidePouchStillGetsAPlace() {
        List<MergedBagLayout.Box> boxes = MergedBagLayout.place(
                Arrays.asList(Coord.of(50, 12)),
                4, 20, 1);
        assertEquals(1, boxes.size());
        assertEquals(50, boxes.get(0).w);
        assertEquals(Coord.of(52, 14), MergedBagLayout.bounds(boxes, 1));
    }
}
