package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasIngredientChoicesTest {
    @Test
    void choicesExposeOptionalAndGroupedActionsBeforeQualitySortedStock() {
        Candidate inventory = new Candidate("inv", "Linen Cloth", 20, 3,
                Source.INVENTORY, "Inventory");
        Candidate storage = new Candidate("db", "Hemp Cloth", 50, 8,
                Source.STORAGE, "Chest");

        List<CraftAtlasIngredientChoices.Choice> choices = CraftAtlasIngredientChoices.choices(
                Arrays.asList(inventory, storage), true, true);

        assertEquals(4, choices.size());
        assertEquals(Selection.Mode.IGNORED, choices.get(0).selection.mode);
        assertEquals(Selection.Mode.ALL, choices.get(1).selection.mode);
        assertSame(storage, choices.get(2).candidate);
        assertSame(inventory, choices.get(3).candidate);
    }

    @Test
    void candidateLabelShowsInventoryMarkerQualityAmountAndLocation() {
        Candidate inventory = new Candidate("inv", "Linen Cloth", 42.5, 6,
                Source.INVENTORY, "Inventory");

        String label = CraftAtlasIngredientChoices.candidateLabel(inventory);

        assertTrue(label.startsWith("★ "));
        assertTrue(label.contains("Linen Cloth"));
        assertTrue(label.contains("Q42.5"));
        assertTrue(label.contains("×6"));
        assertTrue(label.contains("Inventory"));
    }
}
