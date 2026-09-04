package nurgling.craftatlas;

import nurgling.craftatlas.CraftAtlasMaterialPlanner.Allocation;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Plan;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.SlotRequest;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasMaterialPlannerTest {
    @Test
    void defaultsToBestInventoryCandidateBeforeHigherWarehouseQuality() {
        Candidate inventory = candidate("inv-linen-80", "Linen Cloth", 80, 2, Source.INVENTORY);
        Candidate warehouse = candidate("db-linen-120", "Linen Cloth", 120, 8, Source.STORAGE);

        Selection selected = CraftAtlasMaterialPlanner.defaultSelection(List.of(warehouse, inventory));

        assertEquals("Linen Cloth", selected.material);
        assertEquals("inv-linen-80", selected.preferredCandidateId);
    }

    @Test
    void defaultsToHighestWarehouseQualityWhenInventoryIsEmpty() {
        Selection selected = CraftAtlasMaterialPlanner.defaultSelection(List.of(
                candidate("db-90", "Linen Cloth", 90, 5, Source.STORAGE),
                candidate("db-120", "Linen Cloth", 120, 1, Source.STORAGE)));

        assertEquals("db-120", selected.preferredCandidateId);
    }

    @Test
    void candidateOrderIsStableAtEqualQuality() {
        List<Candidate> candidates = CraftAtlasMaterialPlanner.sortedCandidates(List.of(
                candidate("db-hemp", "Hemp Cloth", 100, 1, Source.STORAGE),
                candidate("inv-linen", "Linen Cloth", 100, 1, Source.INVENTORY),
                candidate("db-linen", "Linen Cloth", 100, 1, Source.STORAGE)));

        assertEquals(List.of("inv-linen", "db-hemp", "db-linen"), candidates.stream()
                .map(value -> value.id).collect(Collectors.toList()));
    }

    @Test
    void selectedMaterialFallsBackOnlyThroughItsLowerQualities() {
        SlotRequest fabric = new SlotRequest(0, 3, false,
                List.of("Linen Cloth", "Hemp Cloth"));
        List<Candidate> stock = List.of(
                candidate("linen-100", "Linen Cloth", 100, 2, Source.STORAGE),
                candidate("hemp-99", "Hemp Cloth", 99, 20, Source.STORAGE),
                candidate("linen-92", "Linen Cloth", 92, 10, Source.STORAGE));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(fabric), Map.of(0, stock),
                Map.of(0, Selection.preferred(stock.get(0))), 2);

        assertTrue(plan.complete);
        assertEquals(List.of(2, 4), plan.slots.get(0).allocations.stream()
                .map(value -> value.count).collect(Collectors.toList()));
        assertTrue(plan.slots.get(0).allocations.stream()
                .allMatch(value -> value.material.equals("Linen Cloth")));
    }

    @Test
    void selectedCandidateNeverFallsForwardToHigherQuality() {
        SlotRequest fabric = new SlotRequest(0, 3, false, List.of("Linen Cloth"));
        List<Candidate> stock = List.of(
                candidate("linen-120", "Linen Cloth", 120, 10, Source.STORAGE),
                candidate("linen-100", "Linen Cloth", 100, 1, Source.INVENTORY));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(fabric), Map.of(0, stock),
                Map.of(0, Selection.preferred(stock.get(1))), 1);

        assertFalse(plan.complete);
        assertEquals(List.of("linen-100"), plan.slots.get(0).allocations.stream()
                .map(value -> value.candidateId).collect(Collectors.toList()));
        assertEquals(2, plan.slots.get(0).missing);
    }

    @Test
    void allMatchingCanMixVSpecMembersByQuality() {
        SlotRequest fabric = new SlotRequest(0, 4, false,
                List.of("Linen Cloth", "Hemp Cloth"));
        List<Candidate> stock = List.of(
                candidate("linen-110", "Linen Cloth", 110, 1, Source.STORAGE),
                candidate("hemp-105", "Hemp Cloth", 105, 3, Source.STORAGE),
                candidate("linen-90", "Linen Cloth", 90, 8, Source.STORAGE));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(fabric), Map.of(0, stock),
                Map.of(0, Selection.all()), 1);

        assertEquals(List.of("linen-110", "hemp-105"), plan.slots.get(0).allocations.stream()
                .map(value -> value.candidateId).collect(Collectors.toList()));
    }

    @Test
    void anyShortageMakesWholePlanIncomplete() {
        List<SlotRequest> slots = List.of(
                new SlotRequest(0, 1, false, List.of("Glue")),
                new SlotRequest(1, 2, false, List.of("Board")));

        Plan plan = CraftAtlasMaterialPlanner.plan(slots, Map.of(
                0, List.of(candidate("glue", "Glue", 50, 1, Source.INVENTORY)),
                1, List.of(candidate("board", "Board", 50, 1, Source.STORAGE))),
                Map.of(), 1);

        assertFalse(plan.complete);
        assertEquals(1, plan.slots.get(1).missing);
        assertNull(plan.quality);
    }

    @Test
    void qualityWeightsBatchesWithinSlotButNotRecipeSlots() {
        List<SlotRequest> slots = List.of(
                new SlotRequest(0, 4, false, List.of("Cloth")),
                new SlotRequest(1, 1, false, List.of("Glue")));

        Plan plan = CraftAtlasMaterialPlanner.plan(slots, Map.of(
                0, List.of(candidate("cloth-100", "Cloth", 100, 1, Source.STORAGE),
                        candidate("cloth-80", "Cloth", 80, 3, Source.STORAGE)),
                1, List.of(candidate("glue-40", "Glue", 40, 1, Source.STORAGE))),
                Map.of(0, Selection.all(), 1, Selection.all()), 1);

        assertEquals(62.5, plan.quality, 0.001);
    }

    @Test
    void ignoredOptionalSlotAddsNoDemandOrQuality() {
        SlotRequest optional = new SlotRequest(0, 5, true, List.of("Pepper"));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(optional), Map.of(),
                Map.of(0, Selection.ignored()), 10);

        assertTrue(plan.complete);
        assertTrue(plan.slots.get(0).allocations.isEmpty());
        assertNull(plan.quality);
    }

    @Test
    void requiredSlotCannotBeIgnored() {
        SlotRequest required = new SlotRequest(0, 2, false, List.of("Glue"));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(required), Map.of(),
                Map.of(0, Selection.ignored()), 1);

        assertFalse(plan.complete);
        assertEquals(2, plan.slots.get(0).missing);
    }

    @Test
    void samePhysicalCandidateCannotBeAllocatedTwiceAcrossSlots() {
        Candidate shared = candidate("inv-glue", "Glue", 50, 3, Source.INVENTORY);
        List<SlotRequest> slots = List.of(
                new SlotRequest(0, 2, false, List.of("Glue")),
                new SlotRequest(1, 2, false, List.of("Glue")));

        Plan plan = CraftAtlasMaterialPlanner.plan(slots,
                Map.of(0, List.of(shared), 1, List.of(shared)), Map.of(), 1);

        assertFalse(plan.complete);
        assertEquals(2, plan.slots.get(0).supplied);
        assertEquals(1, plan.slots.get(1).supplied);
        assertEquals(1, plan.slots.get(1).missing);
    }

    @Test
    void disallowedSavedMaterialResetsToAnAllowedDefault() {
        SlotRequest slot = new SlotRequest(0, 1, false, List.of("Glue"));
        Candidate oldCloth = candidate("cloth", "Linen Cloth", 100, 1, Source.STORAGE);
        Candidate glue = candidate("glue", "Glue", 80, 1, Source.STORAGE);

        Selection normalized = CraftAtlasMaterialPlanner.normalizeSelection(
                slot, List.of(glue), Selection.preferred(oldCloth));

        assertEquals("glue", normalized.preferredCandidateId);
    }

    @Test
    void allowedSavedMaterialSurvivesTemporaryStockLoss() {
        SlotRequest slot = new SlotRequest(0, 1, false, List.of("Linen Cloth", "Hemp Cloth"));
        Selection linen = Selection.preferred(candidate(
                "linen-100", "Linen Cloth", 100, 1, Source.STORAGE));

        assertSame(linen, CraftAtlasMaterialPlanner.normalizeSelection(slot, List.of(), linen));
    }

    @Test
    void materialSelectionUsesAllBatchesOfThatMaterialFromBestQualityDown() {
        SlotRequest slot = new SlotRequest(0, 1, false, List.of("Linen Cloth", "Hemp Cloth"));
        List<Candidate> stock = List.of(
                candidate("hemp-150", "Hemp Cloth", 150, 10, Source.STORAGE),
                candidate("linen-100", "Linen Cloth", 100, 2, Source.STORAGE),
                candidate("linen-92", "Linen Cloth", 92, 4, Source.STORAGE));

        Plan plan = CraftAtlasMaterialPlanner.plan(List.of(slot), Map.of(0, stock),
                Map.of(0, Selection.material("Linen Cloth")), 6);

        assertTrue(plan.complete);
        assertEquals(List.of("linen-100", "linen-92"), plan.slots.get(0).allocations.stream()
                .map(value -> value.candidateId).collect(Collectors.toList()));
        assertEquals(List.of(2, 4), plan.slots.get(0).allocations.stream()
                .map(value -> value.count).collect(Collectors.toList()));
    }

    @Test
    void excessiveCraftCountIsRejectedBeforePlanning() {
        SlotRequest slot = new SlotRequest(0, Integer.MAX_VALUE, false, List.of("Stone"));

        assertFalse(CraftAtlasMaterialPlanner.supportsCraftCount(List.of(slot), 2));
        assertThrows(IllegalArgumentException.class, () -> CraftAtlasMaterialPlanner.plan(
                List.of(slot), Map.of(), Map.of(), 2));
    }

    @Test
    void ignoredOptionalSlotDoesNotOverflowAtLargeCount() {
        SlotRequest slot = new SlotRequest(0, Integer.MAX_VALUE, true, List.of("Pepper"));

        assertTrue(CraftAtlasMaterialPlanner.supportsCraftCount(
                List.of(slot), Map.of(0, Selection.ignored()), Integer.MAX_VALUE));
        Plan plan = assertDoesNotThrow(() -> CraftAtlasMaterialPlanner.plan(
                List.of(slot), Map.of(), Map.of(0, Selection.ignored()), Integer.MAX_VALUE));

        assertTrue(plan.complete);
        assertTrue(plan.slots.get(0).ignored);
    }

    @Test
    void provisionalAllDoesNotReplaceBestStorageDefault() {
        SlotRequest slot = new SlotRequest(0, 1, false, List.of("Linen Cloth"));
        Candidate storage = candidate("linen-90", "Linen Cloth", 90, 4, Source.STORAGE);

        Map<Integer, Selection> provisional = CraftAtlasMaterialPlanner.resolveSelections(
                List.of(slot), Map.of(0, List.of()), Map.of());
        Map<Integer, Selection> refreshed = CraftAtlasMaterialPlanner.resolveSelections(
                List.of(slot), Map.of(0, List.of(storage)), Map.of());

        assertTrue(provisional.get(0).isAll());
        assertEquals("linen-90", refreshed.get(0).preferredCandidateId);
    }

    private static Candidate candidate(String id, String material, double quality, int count, Source source) {
        return new Candidate(id, material, quality, count, source,
                source == Source.INVENTORY ? "Inventory" : "Warehouse");
    }
}
