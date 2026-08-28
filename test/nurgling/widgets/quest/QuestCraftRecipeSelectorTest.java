package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestCraftRecipeSelectorTest {
    @Test
    void selectsSingleExactRecipeIgnoringCaseAndWhitespace() {
        assertEquals(1, QuestCraftRecipeSelector.uniqueExactName(
                Arrays.asList("Stone", "  Stone Axe ", "Axe"), "stone axe"));
    }

    @Test
    void rejectsPartialAndAmbiguousRecipeMatches() {
        assertEquals(-1, QuestCraftRecipeSelector.uniqueExactName(
                Arrays.asList("Stone Axe Head", "Axe"), "Stone Axe"));
        assertEquals(-1, QuestCraftRecipeSelector.uniqueExactName(
                Arrays.asList("Stone Axe", "stone axe"), "Stone Axe"));
    }
}
