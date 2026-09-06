package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftAtlasSearchTest {
    @Test
    void searchesOnlyByRecipeNameAndBonuses() {
        CraftAtlasEntry axe = CraftAtlasEntry.builder("test-axe", "Тестовый топор")
                .input(new CraftAtlasEntry.InputSlot(1, false, Arrays.asList(
                        new CraftAtlasEntry.IngredientOption("glue", "Клей"))))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.SKILL,
                        "sewing", "Sewing", null))
                .bonus(new CraftAtlasEntry.Bonus("survive", "Выживание", 2.0)).build();
        CraftAtlasEntry food = CraftAtlasEntry.builder("food", "Ёжик в тумане").build();
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, Arrays.asList(axe, food));
        assertEquals(Arrays.asList("test-axe"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("выживание"))));
        assertEquals(Arrays.asList("food"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("ежик"))));
        assertEquals(List.of(), ids(CraftAtlasSearch.query(snapshot, CraftAtlasSearch.Query.text("клей"))));
        assertEquals(List.of(), ids(CraftAtlasSearch.query(snapshot, CraftAtlasSearch.Query.text("sewing"))));
    }

    @Test
    void unknownBonusSortsAfterKnownValues() {
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, Arrays.asList(
                bonus("unknown", null), bonus("plus-one", 1.0), bonus("plus-three", 3.0)));
        CraftAtlasSearch.Query q = CraftAtlasSearch.Query.builder().bonus("survive").descending(true).build();
        assertEquals(Arrays.asList("plus-three", "plus-one", "unknown"),
                ids(CraftAtlasSearch.query(snapshot, q)));
    }

    @Test
    void filtersCuriositiesByStudyMetricsAndAvailability() {
        CraftAtlasEntry fitting = CraftAtlasEntry.builder("fitting", "Fitting Curio")
                .category("curiosities")
                .availability(CraftAtlasEntry.Availability.OPEN)
                .inputsObserved(true)
                .input(new CraftAtlasEntry.InputSlot(1, false, List.of(
                        new CraftAtlasEntry.IngredientOption("string", "String"))))
                .curiosity(new CraftAtlasEntry.Curiosity(7200, 180, 6)).build();
        CraftAtlasEntry tooHeavy = CraftAtlasEntry.builder("heavy", "Heavy Curio")
                .category("curiosities")
                .availability(CraftAtlasEntry.Availability.OPEN)
                .inputsObserved(true)
                .input(new CraftAtlasEntry.InputSlot(1, false, List.of(
                        new CraftAtlasEntry.IngredientOption("stone", "Stone"))))
                .curiosity(new CraftAtlasEntry.Curiosity(9000, 180, 12)).build();
        CraftAtlasEntry reference = CraftAtlasEntry.builder("reference", "Reference Curio")
                .category("curiosities")
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .curiosity(new CraftAtlasEntry.Curiosity(12000, 180, 4)).build();

        CraftAtlasSearch.Query query = CraftAtlasSearch.Query.text(
                "type:curiosity recipe:true known:true time>=3h weight<=6 lph>=2400");

        assertEquals(List.of("fitting"), ids(CraftAtlasSearch.query(
                CraftAtlasSnapshot.of(1, List.of(fitting, tooHeavy, reference)), query)));
    }

    @Test
    void filtersEveryCategoryByItsOwnFields() {
        CraftAtlasEntry food = CraftAtlasEntry.builder("food", "Rich Food")
                .category("foods").bonus(new CraftAtlasEntry.Bonus("food:strength", "Strength", 8.0)).build();
        CraftAtlasEntry gilding = CraftAtlasEntry.builder("gild", "Fine Gilding")
                .category("gildings")
                .gilding(new CraftAtlasEntry.Gilding(0.4, 1.0, List.of()))
                .bonus(new CraftAtlasEntry.Bonus("gild:sewing", "Sewing", 4.0)).build();
        CraftAtlasEntry equipment = CraftAtlasEntry.builder("ring", "Silver Ring")
                .category("equipment").equipmentSlot("7L; 7R").build();
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, List.of(food, gilding, equipment));

        assertEquals(List.of("food"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("type:food str>=8"))));
        assertEquals(List.of("gild"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("type:gilding sewing>=4 chance-min>=40"))));
        assertEquals(List.of("ring"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("type:equipment slot:7l"))));
    }

    @Test
    void sortsByExtendedMetricAndPreservesRecentOrderWhenRequested() {
        CraftAtlasEntry slow = CraftAtlasEntry.builder("slow", "Slow")
                .category("curiosities").curiosity(new CraftAtlasEntry.Curiosity(3000, 180, 5)).build();
        CraftAtlasEntry fast = CraftAtlasEntry.builder("fast", "Fast")
                .category("curiosities").curiosity(new CraftAtlasEntry.Curiosity(6000, 120, 5)).build();
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, List.of(slow, fast));

        assertEquals(List.of("fast", "slow"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("sort:lph order:desc"))));
        assertEquals(List.of("slow", "fast"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.builder()
                        .restrictTo(new LinkedHashSet<>(Set.of("slow", "fast")))
                        .preferredOrder(List.of("slow", "fast")).build())));
    }

    @Test
    void exposesUsefulExamplesForEveryAtlasCategory() {
        for(String category : List.of("all", "foods", "gildings", "curiosities", "equipment"))
            org.junit.jupiter.api.Assertions.assertFalse(CraftAtlasSearch.examplesFor(category).isEmpty(), category);
    }

    @Test
    void headerFiltersKeepOnlyKnownRecipesWhoseProductsExistInBaseStorage() {
        CraftAtlasEntry storedKnown = CraftAtlasEntry.builder("stored", "Iron Axe")
                .availability(CraftAtlasEntry.Availability.UNAVAILABLE_NOW).build();
        CraftAtlasEntry absentKnown = CraftAtlasEntry.builder("absent", "Stone Axe")
                .availability(CraftAtlasEntry.Availability.OPEN).build();
        CraftAtlasEntry storedReference = CraftAtlasEntry.builder("reference", "Iron Axe")
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY).build();

        CraftAtlasSearch.Query query = CraftAtlasSearch.Query.builder()
                .knownOnly(true)
                .storedItems(Set.of("  IRON AXE  "))
                .build();

        assertEquals(List.of("stored"), ids(CraftAtlasSearch.query(
                CraftAtlasSnapshot.of(1, List.of(storedKnown, absentKnown, storedReference)), query)));
    }

    @Test
    void disabledHeaderFiltersLeaveKnownReferenceAndUnstoredRecipesVisible() {
        CraftAtlasEntry known = CraftAtlasEntry.builder("known", "Known").availability(
                CraftAtlasEntry.Availability.OPEN).build();
        CraftAtlasEntry reference = CraftAtlasEntry.builder("reference", "Reference").availability(
                CraftAtlasEntry.Availability.REFERENCE_ONLY).build();

        CraftAtlasSearch.Query query = CraftAtlasSearch.Query.builder().knownOnly(false).build();

        assertEquals(List.of("known", "reference"), ids(CraftAtlasSearch.query(
                CraftAtlasSnapshot.of(1, List.of(reference, known)), query)));
    }

    private CraftAtlasEntry bonus(String id, Double value) {
        return CraftAtlasEntry.builder(id, id).bonus(new CraftAtlasEntry.Bonus("survive", "Survival", value)).build();
    }

    private List<String> ids(List<CraftAtlasEntry> entries) {
        return entries.stream().map(e -> e.recipeResource).collect(Collectors.toList());
    }
}
