package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikiReferenceCatalogTest {
    @Test
    void parsesReferenceRecipeWithIngredientsRequirementsAndBonuses() throws Exception {
        String json = "{\"entries\":[{" +
                "\"id\":\"wiki:taproot_lacing\",\"name\":\"Taproot Lacing\"," +
                "\"output\":\"wiki-item:taproot_lacing\",\"categories\":[\"gildings\"]," +
                "\"inputs\":[{\"resource\":\"wiki-item:spindly_taproot\",\"name\":\"Spindly Taproot\",\"quantity\":1}]," +
                "\"requirements\":[{\"kind\":\"SKILL\",\"name\":\"Foraging\",\"description\":\"Wiki requirement\"}]," +
                "\"bonuses\":[{\"resource\":\"gild:survival\",\"name\":\"Survival\",\"value\":2}]}]}";

        List<CraftAtlasEntry> entries = WikiReferenceCatalog.parse(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)));

        CraftAtlasEntry entry = entries.get(0);
        assertEquals(CraftAtlasEntry.Availability.REFERENCE_ONLY, entry.availability);
        assertEquals("gildings", entry.categories.get(0));
        assertEquals("Spindly Taproot", entry.inputs.get(0).options.get(0).name);
        assertEquals(CraftAtlasEntry.RequirementKind.SKILL, entry.requirements.get(0).kind);
        assertEquals(2.0, entry.bonuses.get(0).value);
    }

    @Test
    void stableWikiResourcesIgnoreCaseAndPunctuation() {
        assertEquals("wiki-item:taproot-lacing", WikiReferenceCatalog.itemResource("Taproot Lacing"));
        assertEquals(WikiReferenceCatalog.itemResource("Fish Glue"), WikiReferenceCatalog.itemResource("fish-glue"));
    }

    @Test
    void bundledSnapshotContainsFoodGildingAndIntermediateRecipes() {
        List<CraftAtlasEntry> entries = WikiReferenceCatalog.loadBundled();
        assertTrue(entries.size() >= 700);
        assertTrue(has(entries, "Taproot Lacing", "gildings"));
        assertTrue(has(entries, "Apple Pie", "foods"));
        assertTrue(has(entries, "Unbaked Apple Pie", "foods"));
        CraftAtlasSnapshot snapshot = new MenuCraftCatalog(null, null).rebuild();
        assertTrue(snapshot.entries.size() >= 700);
        CraftAtlasEntry applePie = find(snapshot.entries, "Apple Pie");
        assertTrue(applePie.requirements.stream().anyMatch(requirement ->
                requirement.kind == CraftAtlasEntry.RequirementKind.STATION && "Oven".equals(requirement.name)));
        List<CraftAtlasEntry> producers = new CraftRecipeGraph(snapshot)
                .producers(applePie.inputs.get(0).options.get(0).resource);
        assertEquals("Unbaked Apple Pie", producers.get(0).displayName);
    }

    private boolean has(List<CraftAtlasEntry> entries, String name, String category) {
        for(CraftAtlasEntry entry : entries)
            if(name.equals(entry.displayName) && entry.categories.contains(category)) return true;
        return false;
    }

    private CraftAtlasEntry find(List<CraftAtlasEntry> entries, String name) {
        for(CraftAtlasEntry entry : entries) if(name.equals(entry.displayName)) return entry;
        throw new AssertionError(name);
    }
}
