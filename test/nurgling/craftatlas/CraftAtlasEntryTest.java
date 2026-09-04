package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasEntryTest {
    @Test
    void keepsAlternativesInsideOneInputSlot() {
        CraftAtlasEntry.InputSlot slot = new CraftAtlasEntry.InputSlot(2, false, Arrays.asList(
                new CraftAtlasEntry.IngredientOption("gfx/invobjs/glue", "Glue"),
                new CraftAtlasEntry.IngredientOption("gfx/invobjs/fishglue", "Fish Glue")));
        assertEquals(2, slot.quantity);
        assertEquals(2, slot.options.size());
        assertThrows(UnsupportedOperationException.class,
                () -> slot.options.add(new CraftAtlasEntry.IngredientOption("x", "X")));
    }

    @Test
    void requirementsAreNotConsumableInputs() {
        CraftAtlasEntry e = CraftAtlasEntry.builder("paginae/craft/testaxe", "Test Axe")
                .input(new CraftAtlasEntry.InputSlot(1, false,
                        Collections.singletonList(new CraftAtlasEntry.IngredientOption("gfx/invobjs/glue", "Glue"))))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "gfx/terobjs/workbench", "Workbench", null))
                .build();
        assertEquals(1, e.inputs.size());
        assertEquals(CraftAtlasEntry.RequirementKind.STATION, e.requirements.get(0).kind);
    }

    @Test
    void snapshotRejectsDuplicateRecipeResources() {
        CraftAtlasEntry first = CraftAtlasEntry.builder("same", "First").build();
        CraftAtlasEntry second = CraftAtlasEntry.builder("same", "Second").build();
        assertThrows(IllegalArgumentException.class,
                () -> CraftAtlasSnapshot.of(1, Arrays.asList(first, second)));
    }
}
