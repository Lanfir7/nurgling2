package nurgling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraInvGroupTransferTest {

    private static final class NestedItem {
        final String name;
        final boolean transparent;
        final boolean leftover;
        final Object transferTarget;
        final List<NestedItem> contents;

        NestedItem(String name, boolean transparent, NestedItem... contents) {
            this(name, transparent, false, null, contents);
        }

        NestedItem(String name, boolean transparent, boolean leftover,
                   Object transferTarget, NestedItem... contents) {
            this.name = name;
            this.transparent = transparent;
            this.leftover = leftover;
            this.transferTarget = transferTarget != null ? transferTarget : this;
            this.contents = List.of(contents);
        }
    }

    @Test
    void inventoryContainerAndItsContentsAreBothListed() {
        NestedItem seeds = new NestedItem("Seeds of Barley", false);
        NestedItem seedbag = new NestedItem("Seedbag", false, seeds);

        assertEquals(List.of(seedbag, seeds), ExtraInvGroupTransfer.walkListings(
                seedbag, item -> item.contents, item -> item.transparent));
    }

    @Test
    void stackWrapperIsHiddenWhenItsContentsAreListed() {
        NestedItem seeds = new NestedItem("Seeds of Barley", false);
        NestedItem stack = new NestedItem("Stack wrapper", true, seeds);

        assertEquals(List.of(seeds), ExtraInvGroupTransfer.walkListings(
                stack, item -> item.contents, item -> item.transparent));
    }

    @Test
    void nestedStackLeftoversResolveToUniqueTransferTargets() {
        Object stackWrapper = new Object();
        Object looseTarget = new Object();
        NestedItem stackedSeedA = new NestedItem(
                "Seeds of Barley", false, true, stackWrapper);
        NestedItem stackedSeedB = new NestedItem(
                "Seeds of Barley", false, true, stackWrapper);
        NestedItem stack = new NestedItem(
                "Stack wrapper", true, false, null, stackedSeedA, stackedSeedB);
        NestedItem looseSeed = new NestedItem(
                "Seeds of Barley", false, true, looseTarget);
        NestedItem seedbag = new NestedItem("Seedbag", false, stack, looseSeed);

        List<NestedItem> listed = ExtraInvGroupTransfer.walkListings(
                seedbag, item -> item.contents, item -> item.transparent);
        List<Object> targets = ExtraInvGroupTransfer.uniqueTargets(
                listed,
                item -> item.leftover && item.name.equals("Seeds of Barley"),
                item -> item.transferTarget);

        assertEquals(List.of(stackWrapper, looseTarget), targets);
    }

    @Test
    void externalBagIsHiddenWhileItsContentsAreListed() {
        NestedItem seeds = new NestedItem("Seeds of Barley", false);
        NestedItem seedbag = new NestedItem("Seedbag", false, seeds);
        NestedItem hammer = new NestedItem("Hammer", false);

        List<NestedItem> listed = ExtraInvGroupTransfer.externalBagContents(
                List.of(seedbag, hammer),
                item -> item.transferTarget,
                item -> item.name,
                item -> item.contents,
                item -> item.transparent);

        assertEquals(List.of(seeds), listed);
    }

    @Test
    void unnamedEquipmentCandidateIsIgnored() {
        NestedItem unnamed = new NestedItem(null, false);

        List<NestedItem> listed = ExtraInvGroupTransfer.externalBagContents(
                List.of(unnamed),
                item -> item.transferTarget,
                item -> item.name,
                item -> item.contents,
                item -> item.transparent);

        assertTrue(listed.isEmpty());
    }

    @Test
    void onlyApprovedBeltAndPouchContainersContributeContents() {
        NestedItem creelContent = new NestedItem("Fish", false);
        NestedItem poacherContent = new NestedItem("Raw Meat", false);
        NestedItem leatherContent = new NestedItem("Coin", false);
        NestedItem silkContent = new NestedItem("Gemstone", false);
        NestedItem seedContent = new NestedItem("Seeds of Wheat", false);
        NestedItem key = new NestedItem("Key", false);

        List<NestedItem> listed = ExtraInvGroupTransfer.externalBagContents(
                List.of(
                        new NestedItem("Creel", false, creelContent),
                        new NestedItem("Poacher's Pouch", false, poacherContent),
                        new NestedItem("Leather Purse", false, leatherContent),
                        new NestedItem("Silk Purse", false, silkContent),
                        new NestedItem("Seedbag", false, seedContent),
                        new NestedItem("Keyring", false, key)),
                item -> item.transferTarget,
                item -> item.name,
                item -> item.contents,
                item -> item.transparent);

        assertEquals(List.of(creelContent, poacherContent, leatherContent,
                silkContent, seedContent), listed);
    }

    @Test
    void duplicateEquipmentWrappersForSameBagAreCountedOnce() {
        Object equippedBag = new Object();
        NestedItem seeds = new NestedItem("Seeds of Barley", false);
        NestedItem leftSlot = new NestedItem("Seedbag", false, false, equippedBag, seeds);
        NestedItem rightSlot = new NestedItem("Seedbag", false, false, equippedBag, seeds);

        List<NestedItem> listed = ExtraInvGroupTransfer.externalBagContents(
                List.of(leftSlot, rightSlot),
                item -> item.transferTarget,
                item -> item.name,
                item -> item.contents,
                item -> item.transparent);

        assertEquals(List.of(seeds), listed);
    }

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
    void leftoverIncludesOneItemStacksAfterUnpack() {
        List<ExtraInvGroupTransfer.Slot> after = List.of(
                ExtraInvGroupTransfer.Slot.solo("Pike", 32.0),
                ExtraInvGroupTransfer.Slot.oneItemStack("Pike", 40.0),
                ExtraInvGroupTransfer.Slot.stack("Pike", 41.0),
                ExtraInvGroupTransfer.Slot.solo("Bream", 20.0));

        List<ExtraInvGroupTransfer.Slot> leftover =
                ExtraInvGroupTransfer.matchingLeftovers(after, "Pike", NInventory.Grouping.NONE);

        assertEquals(2, leftover.size());
        assertTrue(leftover.stream().anyMatch(s -> !s.stack));
        assertTrue(leftover.stream().anyMatch(s -> s.stack && s.stackSize <= 1));
    }

    @Test
    void leftoverWatchKeepsGoingUntilUnpackThenStops() {
        assertFalse(ExtraInvGroupTransfer.leftoverWatchDone(1, 0));
        assertTrue(ExtraInvGroupTransfer.leftoverWatchDone(2, 0));
        assertFalse(ExtraInvGroupTransfer.leftoverWatchDone(2, 3));
        assertTrue(ExtraInvGroupTransfer.leftoverWatchDone(
                ExtraInvGroupTransfer.LEFTOVER_MAX_PASSES, 3));
        assertEquals(12, ExtraInvGroupTransfer.LEFTOVER_DELAY_TICKS);
    }

    @Test
    void exactQualityGroupingKeepsDifferentDecimalsSeparate() {
        String q321 = ExtraInvGroupTransfer.groupKey("Pike", 32.1, NInventory.Grouping.Q);
        String q329 = ExtraInvGroupTransfer.groupKey("Pike", 32.9, NInventory.Grouping.Q);
        assertNotEquals(q321, q329);

        Map<String, List<ExtraInvGroupTransfer.Listed>> groups = ExtraInvGroupTransfer.group(
                List.of(
                        ExtraInvGroupTransfer.Listed.item("Pike", 32.1),
                        ExtraInvGroupTransfer.Listed.item("Pike", 32.9)),
                NInventory.Grouping.Q, null);
        assertEquals(2, groups.size());
        assertEquals(1, groups.get(q321).size());
        assertEquals(1, groups.get(q329).size());
    }

    @Test
    void mixedStackWrapperMustNotTransferOtherQuality() {
        List<ExtraInvGroupTransfer.Listed> mixed = List.of(
                ExtraInvGroupTransfer.Listed.item("Pike", 40.0),
                ExtraInvGroupTransfer.Listed.item("Pike", 20.0));
        String q40 = ExtraInvGroupTransfer.groupKey("Pike", 40.0, NInventory.Grouping.Q);
        assertFalse(ExtraInvGroupTransfer.stackWrapperSafe(mixed, q40, NInventory.Grouping.Q));
        assertTrue(ExtraInvGroupTransfer.stackWrapperSafe(
                List.of(ExtraInvGroupTransfer.Listed.item("Pike", 40.0),
                        ExtraInvGroupTransfer.Listed.item("Pike", 40.0)),
                q40, NInventory.Grouping.Q));
    }

    @Test
    void typeBulkAllowsMixedQualityInSameStack() {
        List<ExtraInvGroupTransfer.Listed> mixed = List.of(
                ExtraInvGroupTransfer.Listed.item("Granite", 40.0),
                ExtraInvGroupTransfer.Listed.item("Granite", 20.0));
        assertTrue(ExtraInvGroupTransfer.stackWrapperSafe(mixed, "Granite", NInventory.Grouping.NONE));
        assertFalse(ExtraInvGroupTransfer.bulkByType(NInventory.Grouping.Q));
        assertFalse(ExtraInvGroupTransfer.bulkByType(NInventory.Grouping.Q1));
        assertFalse(ExtraInvGroupTransfer.bulkByType(NInventory.Grouping.Q5));
        assertTrue(ExtraInvGroupTransfer.bulkByType(NInventory.Grouping.NONE));
    }

    @Test
    void invxf2BulkCountGoesOnStackWrapper() {
        Object[] args = ExtraInvGroupTransfer.invxf2Args(new int[]{42}, 12);
        assertEquals(3, args.length);
        assertEquals(0, args[0]);
        assertEquals(12, args[1]);
        assertEquals(42, args[2]);
    }

    @Test
    void extraPanelSkippedForEnderExcludedWindows() {
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", false));
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Chest", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Belt", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Pouch", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Stack", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Table", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", true));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel(null, false));
    }

    @Test
    void oneItemStackCountsAsLeftover() {
        assertTrue(ExtraInvGroupTransfer.isLeftover(false, 1));
        assertTrue(ExtraInvGroupTransfer.isLeftover(true, 1));
        assertFalse(ExtraInvGroupTransfer.isLeftover(true, 3));
    }

    @Test
    void extraShiftClickOnTypeSendsOneInvxf2PerItem() {
        List<ExtraInvGroupTransfer.Op<String>> ops = ExtraInvGroupTransfer.plan(
                List.of("sA-1", "sA-2", "sA-3", "sA-4"), ExtraInvGroupTransferTest::stackOf);
        List<Object[]> msgs = ExtraInvGroupTransfer.extraShiftClickInvxf2(99, ops);

        assertEquals("invxf2", ExtraInvGroupTransfer.EXTRA_SHIFT_MSG);
        assertEquals(4, msgs.size());
        for (Object[] msg : msgs) {
            assertEquals(0, msg[0]);
            assertEquals(1, msg[1]);
            assertEquals(99, msg[2]);
        }
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
