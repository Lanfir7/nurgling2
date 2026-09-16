package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MenuCraftCatalogTest {
    @Test
    void pageGildingMetadataPreservesEquipmentSlotCapacity() {
        CraftAtlasEntry.Gilding gilding = new CraftAtlasEntry.Gilding(0.2, 0.5,
                Collections.singletonList(new CraftAtlasEntry.AttributeRef("gfx/hud/chr/agi", "Agility")), 2);
        MenuCraftCatalog.PageRecord page = new MenuCraftCatalog.PageRecord("gildable-coat", "Gildable Coat",
                Collections.singletonList("equipment"), Collections.<CraftAtlasEntry.Bonus>emptyList(), gilding,
                Collections.<CraftAtlasEntry.AttributeRef>emptyList());

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1, Collections.singletonList(page),
                Collections.<String, CraftAtlasObservation>emptyMap()).byRecipe("gildable-coat");

        assertEquals(2, entry.gilding.slots);
        assertEquals("Agility", entry.gilding.attributes.get(0).name);
    }

    @Test
    void stationKeysComeFromInjectedBundledReferences() {
        CraftAtlasEntry.Requirement anvil = new CraftAtlasEntry.Requirement(
                CraftAtlasEntry.RequirementKind.STATION, "gfx/invobjs/anvil", "Anvil", null);
        CraftAtlasEntry entry = CraftAtlasEntry.builder("paginae/craft/bar", "Bar")
                .requirement(anvil)
                .build();
        MenuCraftCatalog catalog = new MenuCraftCatalog(null, null, Collections.singletonList(entry));

        assertEquals(Collections.singleton(CraftAtlasQualityFormula.key(anvil)), catalog.stationKeys());
    }

    @Test
    void observationAddsRequirementAndKeepsRecipeOpen() {
        CraftAtlasObservation observed = new CraftAtlasObservation("testaxe", "Test Axe",
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/glue", "Glue", 1, false)),
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/axe", "Axe", 1, false)),
                Arrays.asList(new CraftAtlasObservation.RequirementResource("gfx/terobjs/workbench", "Workbench")),
                Collections.<CraftAtlasObservation.BonusResource>emptyList());
        Map<String, CraftAtlasObservation> observations = new LinkedHashMap<>();
        observations.put("testaxe", observed);
        CraftAtlasSnapshot snapshot = MenuCraftCatalog.fromRecords(7,
                Arrays.asList(new MenuCraftCatalog.PageRecord("testaxe", "Test Axe")), observations);
        CraftAtlasEntry e = snapshot.byRecipe("testaxe");
        assertEquals(CraftAtlasEntry.Availability.OPEN, e.availability);
        assertEquals(CraftAtlasEntry.RequirementKind.STATION, e.requirements.get(0).kind);
        assertEquals(1, e.inputs.size());
        assertTrue(e.inputsObserved);
    }

    @Test
    void observationOnlyProducerIsUnavailableButNavigable() {
        CraftAtlasObservation observed = new CraftAtlasObservation("glue", "Glue",
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/glue", "Glue", 1, false)),
                Collections.<CraftAtlasObservation.RequirementResource>emptyList(),
                Collections.<CraftAtlasObservation.BonusResource>emptyList());
        CraftAtlasSnapshot snapshot = MenuCraftCatalog.fromRecords(1, Collections.<MenuCraftCatalog.PageRecord>emptyList(),
                Collections.singletonMap("glue", observed));
        assertEquals(CraftAtlasEntry.Availability.UNAVAILABLE_NOW, snapshot.byRecipe("glue").availability);
        assertEquals(1, new CraftRecipeGraph(snapshot).producers("gfx/invobjs/glue").size());
    }

    @Test
    void referenceDataAddsUnknownRecipesAndFillsMissingLiveDetails() {
        CraftAtlasEntry wikiGlue = CraftAtlasEntry.builder("wiki:glue", "Glue")
                .output(WikiReferenceCatalog.itemResource("Glue"))
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .category("gildings")
                .input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption(WikiReferenceCatalog.itemResource("Bone"), "Bone"))))
                .build();
        CraftAtlasEntry wikiUnknown = CraftAtlasEntry.builder("wiki:unknown", "Unknown Gilding")
                .output(WikiReferenceCatalog.itemResource("Unknown Gilding"))
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY).category("gildings").build();

        CraftAtlasSnapshot snapshot = MenuCraftCatalog.fromRecords(9,
                Collections.singletonList(new MenuCraftCatalog.PageRecord("paginae/craft/glue", "Glue")),
                Collections.<String, CraftAtlasObservation>emptyMap(), Arrays.asList(wikiGlue, wikiUnknown));

        CraftAtlasEntry live = snapshot.byRecipe("paginae/craft/glue");
        assertEquals(CraftAtlasEntry.Availability.OPEN, live.availability);
        assertEquals("Bone", live.inputs.get(0).options.get(0).name);
        assertFalse(live.inputsObserved);
        assertNull(snapshot.byRecipe("wiki:glue"));
        assertEquals(CraftAtlasEntry.Availability.REFERENCE_ONLY,
                snapshot.byRecipe("wiki:unknown").availability);
    }

    @Test
    void liveAndWikiCopiesOfTheSameRequirementAreCollapsedAcrossKinds() {
        CraftAtlasObservation observed = new CraftAtlasObservation("bronze", "Bronze Bar",
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Arrays.asList(
                        new CraftAtlasObservation.RequirementResource("gfx/invobjs/smithshammer", "Smithy's Hammer"),
                        new CraftAtlasObservation.RequirementResource("gfx/invobjs/anvil", "Anvil")),
                Collections.<CraftAtlasObservation.BonusResource>emptyList());
        CraftAtlasEntry reference = CraftAtlasEntry.builder("wiki:bronze", "Bronze Bar")
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "wiki-item:smithy-s-hammer", "Smithy's Hammer", "Ring of Brodgar"))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "wiki-item:anvil", "Anvil", "Ring of Brodgar"))
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .build();

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1,
                Collections.singletonList(new MenuCraftCatalog.PageRecord("bronze", "Bronze Bar")),
                Collections.singletonMap("bronze", observed), Collections.singletonList(reference))
                .byRecipe("bronze");

        assertEquals(2, entry.requirements.size());
        assertEquals("Smithy's Hammer", entry.requirements.get(0).name);
        assertEquals("Anvil", entry.requirements.get(1).name);
    }

    @Test
    void observedQualityModifiersNeverBecomeItemEffects() {
        CraftAtlasObservation observed = new CraftAtlasObservation("bonepins", "Bone Pins",
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Collections.singletonList(new CraftAtlasObservation.Item("gfx/invobjs/bonepins", "Bone Pins", 1, false)),
                Collections.<CraftAtlasObservation.RequirementResource>emptyList(),
                Collections.<CraftAtlasObservation.BonusResource>emptyList(),
                Collections.singletonList(new CraftAtlasObservation.AttributeResource("gfx/hud/chr/sewing", "Sewing")));

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1,
                Collections.singletonList(new MenuCraftCatalog.PageRecord("bonepins", "Bone Pins")),
                Collections.singletonMap("bonepins", observed)).byRecipe("bonepins");

        assertEquals("Sewing", entry.qualityModifiers.get(0).name);
        assertTrue(entry.bonuses.isEmpty());
    }

    @Test
    void liveAndObservedCopiesOfTheSameQualitySkillAreCollapsed() {
        CraftAtlasEntry.AttributeRef sewing = new CraftAtlasEntry.AttributeRef("gfx/hud/chr/sewing", "Sewing");
        MenuCraftCatalog.PageRecord page = new MenuCraftCatalog.PageRecord("bonepins", "Bone Pins",
                Collections.singletonList("gildings"), Collections.<CraftAtlasEntry.Bonus>emptyList(), null,
                Collections.singletonList(sewing));
        CraftAtlasObservation observed = new CraftAtlasObservation("bonepins", "Bone Pins",
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Collections.singletonList(new CraftAtlasObservation.Item("gfx/invobjs/bonepins", "Bone Pins", 1, false)),
                Collections.<CraftAtlasObservation.RequirementResource>emptyList(),
                Collections.<CraftAtlasObservation.BonusResource>emptyList(),
                Collections.singletonList(new CraftAtlasObservation.AttributeResource("gfx/hud/chr/sewing", "Sewing")));

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1, Collections.singletonList(page),
                Collections.singletonMap("bonepins", observed)).byRecipe("bonepins");

        assertEquals(1, entry.qualityModifiers.size());
    }

    @Test
    void knownFoodCollapsesGameAndWikiNamesForTheSameFep() {
        MenuCraftCatalog.PageRecord page = new MenuCraftCatalog.PageRecord("liveronions", "Liver & Onions",
                Collections.singletonList("foods"), Collections.singletonList(
                new CraftAtlasEntry.Bonus("food:intelligence +1", "Intelligence +1", 1.0)), null,
                Collections.<CraftAtlasEntry.AttributeRef>emptyList());
        CraftAtlasEntry reference = CraftAtlasEntry.builder("wiki:liver-onions", "Liver & Onions")
                .category("foods")
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/int", "Intelligence", 1.0))
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .build();

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1, Collections.singletonList(page),
                Collections.<String, CraftAtlasObservation>emptyMap(), Collections.singletonList(reference))
                .byRecipe("liveronions");

        assertEquals(1, entry.bonuses.size());
    }

    @Test
    void genericMeatObservationYieldsToWikiAnimalMeat() {
        CraftAtlasObservation observed = new CraftAtlasObservation("badgerbotillo", "Badger Botillo",
                Arrays.asList(
                        new CraftAtlasObservation.Item("gfx/invobjs/meat-raw", "Meat", 2, false),
                        new CraftAtlasObservation.Item("gfx/invobjs/intestines", "Intestines", 1, false)),
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/sausage-badger", "Badger Botillo", 1, false)),
                Collections.<CraftAtlasObservation.RequirementResource>emptyList(),
                Collections.<CraftAtlasObservation.BonusResource>emptyList());
        CraftAtlasEntry wiki = CraftAtlasEntry.builder("wiki:badger-botillo", "Badger Botillo")
                .availability(CraftAtlasEntry.Availability.REFERENCE_ONLY)
                .input(new CraftAtlasEntry.InputSlot(2, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption("wiki-item:raw-badger", "Raw Badger"))))
                .input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption("wiki-item:intestines", "Intestines"))))
                .build();

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1,
                Collections.singletonList(new MenuCraftCatalog.PageRecord("badgerbotillo", "Badger Botillo")),
                Collections.singletonMap("badgerbotillo", observed), Collections.singletonList(wiki))
                .byRecipe("badgerbotillo");

        assertEquals("Raw Badger", entry.inputs.get(0).options.get(0).name);
        assertEquals(2, entry.inputs.get(0).quantity);
        assertEquals("Intestines", entry.inputs.get(1).options.get(0).name);
        assertTrue(entry.inputsObserved);
    }

    @Test
    void currentPageFoodBonusReplacesAnOlderObservedCopy() {
        MenuCraftCatalog.PageRecord page = new MenuCraftCatalog.PageRecord("liveronions", "Liver & Onions",
                Collections.singletonList("foods"), Collections.singletonList(
                new CraftAtlasEntry.Bonus("gfx/hud/chr/psy", "Psyche", 3.0)), null,
                Collections.<CraftAtlasEntry.AttributeRef>emptyList());
        CraftAtlasObservation observed = new CraftAtlasObservation("liveronions", "Liver & Onions",
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Collections.<CraftAtlasObservation.Item>emptyList(),
                Collections.<CraftAtlasObservation.RequirementResource>emptyList(),
                Collections.singletonList(new CraftAtlasObservation.BonusResource(
                        "food:psyche +1", "Psyche +1", 3.0)));

        CraftAtlasEntry entry = MenuCraftCatalog.fromRecords(1, Collections.singletonList(page),
                Collections.singletonMap("liveronions", observed)).byRecipe("liveronions");

        assertEquals(1, entry.bonuses.size());
        assertEquals("Psyche", entry.bonuses.get(0).name);
    }
}
