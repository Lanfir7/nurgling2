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

    @Test
    void workstationGobResourcesKeepGenericStationKeys() {
        assertEquals("station:pow", CraftAtlasQualityFormula.key(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/pow", null)));
        assertEquals("station:htable", CraftAtlasQualityFormula.key(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/htable", null)));
        assertEquals("station:tarkiln", CraftAtlasQualityFormula.key(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/tarkiln", null)));
    }

    @Test
    void cauldronAndAnvilApplyTheirFactorsBeforeTheCharacterSoftcap() {
        CraftAtlasEntry cauldron = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/cauldron", "Metal Cauldron"));
        CraftAtlasEntry anvil = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/anvil", "Anvil"));
        Map<String, Double> cauldronQualities = new LinkedHashMap<>();
        cauldronQualities.put("station:cauldron", 200.0);
        cauldronQualities.put(CraftAtlasQualityFormula.CAULDRON_WATER, 40.0);
        Map<String, Double> anvilQualities = new LinkedHashMap<>();
        anvilQualities.put("station:anvil", 200.0);
        anvilQualities.put("tool:smithy-hammer", 50.0);

        assertEquals(105.0, CraftAtlasQualityFormula.result(cauldron, 100.0, cauldronQualities), 0.0001);
        assertEquals(92.5, CraftAtlasQualityFormula.softcap(105.0, 80.0), 0.0001);
        assertEquals(115.625, CraftAtlasQualityFormula.result(anvil, 100.0, anvilQualities), 0.0001);
        assertEquals(97.8125, CraftAtlasQualityFormula.softcap(115.625, 80.0), 0.0001);
    }

    @Test
    void clayAndGenericCauldronsUseSeparateStationQualityValues() {
        CraftAtlasEntry clay = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/claycauldron", "Clay Cauldron"));
        CraftAtlasEntry generic = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "wiki-item:cauldron", "Cauldron"));
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:cauldron", 200.0);
        qualities.put("station:clay-cauldron", 160.0);
        qualities.put(CraftAtlasQualityFormula.CAULDRON_WATER, 40.0);

        assertEquals(97.5, CraftAtlasQualityFormula.result(clay, 100.0, qualities), 0.0001);
        assertEquals(105.0, CraftAtlasQualityFormula.result(generic, 100.0, qualities), 0.0001);
        qualities.put(CraftAtlasQualityFormula.GENERIC_CAULDRON_TYPE, 1.0);
        assertEquals(97.5, CraftAtlasQualityFormula.result(generic, 100.0, qualities), 0.0001);
        qualities.put(CraftAtlasQualityFormula.CAULDRON_WATER, 1.0);
        assertEquals(95.125, CraftAtlasQualityFormula.result(clay, 100.0, qualities), 0.0001);
    }

    @Test
    void explicitlyNamedMetalCauldronIgnoresTheGenericClaySelector() {
        CraftAtlasEntry metal = recipeWith(
                requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/cauldron", "Metal Cauldron"));
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:cauldron", 200.0);
        qualities.put("station:clay-cauldron", 20.0);
        qualities.put(CraftAtlasQualityFormula.CAULDRON_WATER, 40.0);
        qualities.put(CraftAtlasQualityFormula.GENERIC_CAULDRON_TYPE, 1.0);

        assertFalse(CraftAtlasQualityFormula.hasGenericCauldron(metal));
        assertEquals(105.0, CraftAtlasQualityFormula.result(metal, 100.0, qualities), 0.0001);
    }

    @Test
    void knownProcessingStationsUseCanonicalAliasesRegardlessOfRequirementKind() {
        String[][] aliases = {
                { "wiki-item:loom", "Loom", "station:loom" },
                { "gfx/terobjs/sswheel", "Spinning Wheel", "station:spinning-wheel" },
                { "wiki-item:churn", "Churn", "station:churn" },
                { "wiki-item:meatgrinder", "Meatgrinder", "station:meatgrinder" },
                { "gfx/terobjs/potterswheel", "Potter's Wheel", "station:potters-wheel" },
                { "wiki-item:winepress", "Extraction Press", "station:extraction-press" }
        };
        for(String[] alias : aliases) {
            CraftAtlasEntry.Requirement requirement = requirement(CraftAtlasEntry.RequirementKind.TOOL, alias[0], alias[1]);
            CraftAtlasEntry entry = recipeWith(requirement);
            assertEquals(alias[2], CraftAtlasQualityFormula.key(requirement));
            assertEquals(125.0, CraftAtlasQualityFormula.result(entry, 100.0,
                    java.util.Collections.singletonMap(alias[2], 200.0)), 0.0001);
        }
    }

    @Test
    void legacyStationAndToolQualityKeysMigrateToCanonicalStations() {
        assertEquals("station:meatgrinder",
                CraftAtlasQualityFormula.canonicalStoredKey("tool:wiki-item-meatgrinder"));
        assertEquals("station:loom",
                CraftAtlasQualityFormula.canonicalStoredKey("station:wiki-item-loom"));
        assertEquals("station:anvil", CraftAtlasQualityFormula.canonicalStoredKey("station:anvil"));
    }

    @Test
    void bundledCauldronsWithWaterVolumesStillUseTheCauldronFormula() {
        int checked = 0;
        Map<String, Double> qualities = new LinkedHashMap<>();
        qualities.put("station:cauldron", 200.0);
        qualities.put(CraftAtlasQualityFormula.CAULDRON_WATER, 40.0);
        for(CraftAtlasEntry entry : WikiReferenceCatalog.loadBundled()) {
            for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
                if(requirement.resource == null || !requirement.resource.startsWith("wiki-item:cauldron-")) continue;
                assertEquals("station:cauldron", CraftAtlasQualityFormula.key(requirement), requirement.resource);
                assertTrue(CraftAtlasQualityFormula.hasGenericCauldron(entry), requirement.resource);
                assertEquals(105.0, CraftAtlasQualityFormula.result(entry, 100.0, qualities), 0.0001,
                        requirement.resource);
                assertTrue(CraftAtlasQualityFormula.factors(entry).stream().anyMatch(factor ->
                        CraftAtlasQualityFormula.CAULDRON_WATER.equals(factor.key)));
                checked++;
            }
        }
        assertTrue(checked >= 10, "Expected the bundled cauldron recipes with water-volume qualifiers");
    }

    @Test
    void gobAndWikiStationAliasesMigrateUsingTheSameIdentityAsRecipes() {
        for(String prefix : new String[] { "tool:", "station:" }) {
            for(String alias : new String[] { "swheel", "sswheel", "spinningwheel", "wiki-item-swheel", "wiki-item-spinning-wheel" })
                assertEquals("station:spinning-wheel", CraftAtlasQualityFormula.canonicalStoredKey(prefix + alias));
            for(String alias : new String[] { "winepress", "extractionpress", "wiki-item-winepress", "wiki-item-extraction-press" })
                assertEquals("station:extraction-press", CraftAtlasQualityFormula.canonicalStoredKey(prefix + alias));
            for(String alias : new String[] { "potterswheel", "potter-s-wheel", "wiki-item-potter-s-wheel" })
                assertEquals("station:potters-wheel", CraftAtlasQualityFormula.canonicalStoredKey(prefix + alias));
        }
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
