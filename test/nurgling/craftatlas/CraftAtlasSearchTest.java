package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftAtlasSearchTest {
    @Test
    void matchesAcrossBonusAndIngredientWithAndSemanticsAndNormalizesYo() {
        CraftAtlasEntry axe = CraftAtlasEntry.builder("test-axe", "Тестовый топор")
                .input(new CraftAtlasEntry.InputSlot(1, false, Arrays.asList(
                        new CraftAtlasEntry.IngredientOption("glue", "Клей"))))
                .bonus(new CraftAtlasEntry.Bonus("survive", "Выживание", 2.0)).build();
        CraftAtlasEntry food = CraftAtlasEntry.builder("food", "Ёжик в тумане").build();
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, Arrays.asList(axe, food));
        assertEquals(Arrays.asList("test-axe"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("выживание клей"))));
        assertEquals(Arrays.asList("food"), ids(CraftAtlasSearch.query(snapshot,
                CraftAtlasSearch.Query.text("ежик"))));
    }

    @Test
    void unknownBonusSortsAfterKnownValues() {
        CraftAtlasSnapshot snapshot = CraftAtlasSnapshot.of(1, Arrays.asList(
                bonus("unknown", null), bonus("plus-one", 1.0), bonus("plus-three", 3.0)));
        CraftAtlasSearch.Query q = CraftAtlasSearch.Query.builder().bonus("survive").descending(true).build();
        assertEquals(Arrays.asList("plus-three", "plus-one", "unknown"),
                ids(CraftAtlasSearch.query(snapshot, q)));
    }

    private CraftAtlasEntry bonus(String id, Double value) {
        return CraftAtlasEntry.builder(id, id).bonus(new CraftAtlasEntry.Bonus("survive", "Survival", value)).build();
    }

    private List<String> ids(List<CraftAtlasEntry> entries) {
        return entries.stream().map(e -> e.recipeResource).collect(Collectors.toList());
    }
}
