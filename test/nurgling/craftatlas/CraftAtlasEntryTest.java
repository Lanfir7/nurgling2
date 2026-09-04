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

    @Test
    void gildingChanceAndCraftQualitySkillsAreSeparateFromEffectsAndRequirements() {
        CraftAtlasEntry.AttributeRef stealth = new CraftAtlasEntry.AttributeRef("gfx/hud/chr/stealth", "Stealth");
        CraftAtlasEntry.AttributeRef sewing = new CraftAtlasEntry.AttributeRef("gfx/hud/chr/sewing", "Sewing");
        CraftAtlasEntry entry = CraftAtlasEntry.builder("bonepins", "Bone Pins")
                .gilding(new CraftAtlasEntry.Gilding(0.35, 1.0, Collections.singletonList(stealth)))
                .qualityModifier(sewing)
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/stealth", "Stealth", 5.0))
                .build();

        assertEquals(0.35, entry.gilding.pmin);
        assertEquals("Stealth", entry.gilding.attributes.get(0).name);
        assertEquals("Sewing", entry.qualityModifiers.get(0).name);
        assertEquals(1, entry.bonuses.size());
        assertTrue(entry.requirements.isEmpty());
    }

    @Test
    void keepsEquipmentSlotsAsReferenceMetadata() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("bunny-slippers", "Bunny Slippers")
                .equipmentSlot("11R")
                .build();

        assertEquals(Collections.singletonList("11R"), entry.equipmentSlots);
        assertThrows(UnsupportedOperationException.class, () -> entry.equipmentSlots.add("11L"));
    }
}
