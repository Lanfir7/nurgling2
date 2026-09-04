package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MenuCraftCatalogTest {
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
        assertNull(snapshot.byRecipe("wiki:glue"));
        assertEquals(CraftAtlasEntry.Availability.REFERENCE_ONLY,
                snapshot.byRecipe("wiki:unknown").availability);
    }
}
