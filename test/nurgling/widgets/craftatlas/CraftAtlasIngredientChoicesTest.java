package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import nurgling.i18n.L10n;
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

    @Test
    void missingPreviouslySelectedMaterialStaysVisibleInsteadOfSwitchingMaterials() {
        Candidate hemp = new Candidate("hemp", "Hemp Cloth", 90, 4,
                Source.STORAGE, "Chest");
        Candidate oldLinen = new Candidate("linen", "Linen Cloth", 100, 1,
                Source.STORAGE, "Chest");

        CraftAtlasIngredientChoices.Choice displayed = CraftAtlasIngredientChoices.displaySelection(
                List.of(hemp), false, true, Selection.preferred(oldLinen));

        assertEquals("Linen Cloth", displayed.selection.material);
        assertNull(displayed.candidate);
        assertTrue(displayed.label.contains("Linen Cloth"));
    }

    @Test
    void emptyGroupedSlotShowsMissingInsteadOfAllMatching() {
        List<CraftAtlasIngredientChoices.Choice> choices =
                CraftAtlasIngredientChoices.choices(List.of(), false, true);

        assertEquals(1, choices.size());
        assertTrue(choices.get(0).label.contains(L10n.get("craft_atlas.material.missing")));
    }

    @Test
    void missingSelectedBatchDoesNotDisplayAHigherQualityAsSelected() {
        Candidate selected = new Candidate("linen-100", "Linen Cloth", 100, 1,
                Source.INVENTORY, "Inventory");
        Candidate higher = new Candidate("linen-120", "Linen Cloth", 120, 3,
                Source.STORAGE, "Chest");

        CraftAtlasIngredientChoices.Choice displayed = CraftAtlasIngredientChoices.displaySelection(
                List.of(higher), false, false, Selection.preferred(selected));

        assertNull(displayed.candidate);
        assertEquals("linen-100", displayed.selection.preferredCandidateId);
    }
}
