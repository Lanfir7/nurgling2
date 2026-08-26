package nurgling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraInvGroupTransferTest {

    @Test
    void unpacksStackIntoIndividualItems() {
        ExtraInvGroupTransfer.Listed stack = ExtraInvGroupTransfer.Listed.stack(
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0),
                ExtraInvGroupTransfer.Listed.item("Bream", 20.0));

        List<ExtraInvGroupTransfer.Listed> flat = ExtraInvGroupTransfer.unpack(List.of(stack));

        assertEquals(3, flat.size());
        assertEquals("Pike", flat.get(0).name);
        assertEquals("Bream", flat.get(2).name);
    }

    @Test
    void groupsByTypeKeepsSameFishTogether() {
        List<ExtraInvGroupTransfer.Listed> items = ExtraInvGroupTransfer.unpack(List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0),
                ExtraInvGroupTransfer.Listed.item("Bream", 20.0)));

        Map<String, List<ExtraInvGroupTransfer.Listed>> groups =
                ExtraInvGroupTransfer.group(items, NInventory.Grouping.NONE, null);

        assertEquals(2, groups.size());
        assertEquals(2, groups.get("Pike").size());
        assertEquals(1, groups.get("Bream").size());
    }

    @Test
    void groupsByQuality5SplitsFishBands() {
        List<ExtraInvGroupTransfer.Listed> items = ExtraInvGroupTransfer.unpack(List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 34.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0)));

        Map<String, List<ExtraInvGroupTransfer.Listed>> groups =
                ExtraInvGroupTransfer.group(items, NInventory.Grouping.Q5, null);

        assertEquals(2, groups.get("Pike@Q30").size());
        assertEquals(1, groups.get("Pike@Q40").size());
    }

    @Test
    void shiftClickWithoutAltTakesOneHighest() {
        List<ExtraInvGroupTransfer.Listed> pike = List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0));

        List<ExtraInvGroupTransfer.Listed> picked = ExtraInvGroupTransfer.pick(pike, false, false);

        assertEquals(1, picked.size());
        assertEquals(41.0, picked.get(0).quality);
    }

    @Test
    void altShiftTransfersWholeTypeOrQualityGroup() {
        List<ExtraInvGroupTransfer.Listed> pike = List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0));

        List<ExtraInvGroupTransfer.Listed> picked = ExtraInvGroupTransfer.pick(pike, true, false);

        assertEquals(2, picked.size());
    }

    @Test
    void rightClickReversesToLowestQualityFirst() {
        List<ExtraInvGroupTransfer.Listed> pike = List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 41.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 32.0));

        List<ExtraInvGroupTransfer.Listed> picked = ExtraInvGroupTransfer.pick(pike, false, true);

        assertEquals(32.0, picked.get(0).quality);
    }

    @Test
    void transferCountIsOneForStockpile() {
        assertEquals(1, ExtraInvGroupTransfer.TRANSFER_COUNT);
    }

    @Test
    void minQualityFilterDropsLowFish() {
        List<ExtraInvGroupTransfer.Listed> items = List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 10.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 40.0));

        Map<String, List<ExtraInvGroupTransfer.Listed>> groups =
                ExtraInvGroupTransfer.group(items, NInventory.Grouping.NONE, 30.0);

        assertEquals(1, groups.get("Pike").size());
        assertEquals(40.0, groups.get("Pike").get(0).quality);
    }

    @Test
    void emptyPickIsEmpty() {
        assertTrue(ExtraInvGroupTransfer.pick(List.of(), true, false).isEmpty());
    }

    @Test
    void itemQualityIgnoresStackAverage() {
        assertEquals(20.0, ExtraInvGroupTransfer.itemQuality(20.0, 30.0));
        assertEquals(20.0, ExtraInvGroupTransfer.itemQuality(20.0, null));
        assertEquals(null, ExtraInvGroupTransfer.itemQuality(null, 30.0));
    }

    @Test
    void qualityLabelShowsRangeWhenItemsDiffer() {
        assertEquals("q20.0", ExtraInvGroupTransfer.qualityLabel(20.0, 20.0));
        assertEquals("q20.0–40.0", ExtraInvGroupTransfer.qualityLabel(20.0, 40.0));
        assertEquals("", ExtraInvGroupTransfer.qualityLabel(0, 0));
    }

    @Test
    void matchingTopLevelSendsStacksThenSolosOfThatTypeOnly() {
        List<ExtraInvGroupTransfer.Slot> slots = List.of(
                ExtraInvGroupTransfer.Slot.stack("Pike", 32.0),
                ExtraInvGroupTransfer.Slot.solo("Pike", 40.0),
                ExtraInvGroupTransfer.Slot.stack("Bream", 20.0));

        List<ExtraInvGroupTransfer.Slot> ordered =
                ExtraInvGroupTransfer.matchingTopLevel(slots, "Pike", NInventory.Grouping.NONE);

        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0).stack);
        assertEquals("Pike", ordered.get(0).name);
        assertTrue(!ordered.get(1).stack);
        assertEquals("Pike", ordered.get(1).name);
    }

    @Test
    void invxf2ArgsMatchEnderStockpileTransfer() {
        Object[] args = ExtraInvGroupTransfer.invxf2Args(new int[]{42, 7});
        assertEquals(4, args.length);
        assertEquals(0, args[0]);
        assertEquals(1, args[1]);
        assertEquals(42, args[2]);
        assertEquals(7, args[3]);
    }

    @Test
    void leftoverPassTakesOnlyNonStacksOfThatType() {
        List<ExtraInvGroupTransfer.Slot> after = List.of(
                ExtraInvGroupTransfer.Slot.solo("Pike", 32.0),
                ExtraInvGroupTransfer.Slot.solo("Pike", 33.0),
                ExtraInvGroupTransfer.Slot.solo("Pike", 34.0),
                ExtraInvGroupTransfer.Slot.stack("Pike", 40.0),
                ExtraInvGroupTransfer.Slot.solo("Bream", 20.0));

        List<ExtraInvGroupTransfer.Slot> leftover =
                ExtraInvGroupTransfer.matchingLeftovers(after, "Pike", NInventory.Grouping.NONE);

        assertEquals(3, leftover.size());
        leftover.forEach(s -> {
            assertEquals("Pike", s.name);
            assertTrue(!s.stack);
        });
    }

    @Test
    void oneItemStackCountsAsLeftover() {
        assertTrue(ExtraInvGroupTransfer.isLeftover(false, 1));
        assertTrue(ExtraInvGroupTransfer.isLeftover(true, 1));
        assertFalse(ExtraInvGroupTransfer.isLeftover(true, 3));
    }

    @Test
    void planSendsWholeStacksFirstThenSolos() {
        List<String> group = List.of("sA-1", "sA-2", "sA-3", "sB-1", "solo-1", "solo-2");

        List<ExtraInvGroupTransfer.Op<String>> ops = ExtraInvGroupTransfer.plan(group, ExtraInvGroupTransferTest::stackOf);

        assertEquals(4, ops.size());
        assertEquals("stackA", ops.get(0).target);
        assertEquals(3, ops.get(0).count);
        assertTrue(ops.get(0).fromStack);
        assertEquals("stackB", ops.get(1).target);
        assertEquals(1, ops.get(1).count);
        assertEquals("solo-1", ops.get(2).target);
        assertEquals(1, ops.get(2).count);
        assertEquals("solo-2", ops.get(3).target);
        assertEquals(1, ops.get(3).count);
    }

    @Test
    void planWithOnlySolosKeepsThem() {
        List<ExtraInvGroupTransfer.Op<String>> ops =
                ExtraInvGroupTransfer.plan(List.of("solo-1", "solo-2"), ExtraInvGroupTransferTest::stackOf);
        assertEquals(2, ops.size());
        assertEquals("solo-1", ops.get(0).target);
        assertEquals(1, ops.get(0).count);
    }

    private static String stackOf(String id) {
        if (id.startsWith("sA-")) {
            return "stackA";
        }
        if (id.startsWith("sB-")) {
            return "stackB";
        }
        return null;
    }
}
