package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasQualityFormulaTest {
    @Test
    void anvilUsesMetalCraftWeightsForStationAndHammer() {
        CraftAtlasEntry entry = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/invobjs/anvil", "Anvil"),
                requirement(CraftAtlasEntry.RequirementKind.TOOL, "gfx/invobjs/smithshammer", "Smithy's Hammer"));
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:anvil", 50.0);
        qualities.put("tool:smithy-hammer", 80.0);

        assertEquals(83.75, CraftAtlasQualityFormula.result(entry, 100.0, qualities), 0.0001);
    }

    @Test
    void cauldronAddsPersistentWaterQualityEvenWhenServerDoesNotListWater() {
        CraftAtlasEntry entry = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/invobjs/cauldron", "Cauldron"));
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:cauldron", 50.0);
        qualities.put("context:cauldron-water", 80.0);

        assertTrue(CraftAtlasQualityFormula.factors(entry).stream()
                .anyMatch(factor -> factor.key.equals("context:cauldron-water")));
        assertEquals(91.25, CraftAtlasQualityFormula.result(entry, 100.0, qualities), 0.0001);
    }

    @Test
    void crucibleIsRequiredButDoesNotChangeNuggetQuality() {
        CraftAtlasEntry entry = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/invobjs/crucible", "Crucible"));
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:crucible", 500.0);

        assertEquals(100.0, CraftAtlasQualityFormula.result(entry, 100.0, qualities), 0.0001);
        assertFalse(CraftAtlasQualityFormula.factors(entry).get(0).affectsResult);
    }

    @Test
    void characterQualitySoftcapsAWorkstationAdjustedResult() {
        assertEquals(70.0, CraftAtlasQualityFormula.softcap(100.0, 40.0), 0.0001);
        assertEquals(35.0, CraftAtlasQualityFormula.softcap(35.0, 40.0), 0.0001);
    }

    @Test
    void anvilAlwaysExposesOneHammerQualityFactor() {
        CraftAtlasEntry entry = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/anvil", "Anvil"),
                requirement(CraftAtlasEntry.RequirementKind.STATION, "wiki-item:smithy-hammer-and-anvil",
                        "Smithy's Hammer and Anvil"));

        assertEquals(1, CraftAtlasQualityFormula.factors(entry).stream()
                .filter(factor -> factor.key.equals("station:anvil")).count());
        assertEquals(1, CraftAtlasQualityFormula.factors(entry).stream()
                .filter(factor -> factor.key.equals("tool:smithy-hammer")).count());
    }

    @Test
    void unknownWorkstationsRemainEditableButAreMarkedAsNotCalculated() {
        CraftAtlasEntry entry = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/oven", "Oven"));

        assertFalse(CraftAtlasQualityFormula.factors(entry).get(0).affectsResult);
    }

    private static CraftAtlasEntry recipeWith(CraftAtlasEntry.Requirement... requirements) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder("paginae/craft/test", "Test");
        for(CraftAtlasEntry.Requirement requirement : requirements) builder.requirement(requirement);
        return builder.build();
    }

    private static CraftAtlasEntry.Requirement requirement(CraftAtlasEntry.RequirementKind kind,
                                                            String resource, String name) {
        return new CraftAtlasEntry.Requirement(kind, resource, name, null);
    }
}
