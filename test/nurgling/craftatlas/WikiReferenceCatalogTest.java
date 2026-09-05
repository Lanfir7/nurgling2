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
                "\"requirements\":[{\"kind\":\"SKILL\",\"name\":\"Sewing\",\"description\":\"Wiki requirement\"}]," +
                "\"bonuses\":[{\"resource\":\"gild:chance\",\"name\":\"Gild chance: 45%-95%\"}," +
                "{\"resource\":\"gild:survival\",\"name\":\"Survival\",\"value\":2}]}]}";

        List<CraftAtlasEntry> entries = WikiReferenceCatalog.parse(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)));

        CraftAtlasEntry entry = entries.get(0);
        assertEquals(CraftAtlasEntry.Availability.REFERENCE_ONLY, entry.availability);
        assertEquals("gildings", entry.categories.get(0));
        assertEquals("Spindly Taproot", entry.inputs.get(0).options.get(0).name);
        assertEquals(CraftAtlasEntry.RequirementKind.SKILL, entry.requirements.get(0).kind);
        assertEquals("gfx/hud/chr/sewing", entry.requirements.get(0).resource);
        assertTrue(entry.qualityModifiers.isEmpty());
        assertEquals(0.45, entry.gilding.pmin);
        assertEquals(0.95, entry.gilding.pmax);
        assertEquals(2.0, entry.bonuses.get(0).value);
    }

    @Test
    void parsesExplicitGildingAttributesWithGameIconResources() throws Exception {
        String json = "{\"entries\":[{\"id\":\"wiki:extra-stitches\",\"name\":\"Extra Stitches\"," +
                "\"categories\":[\"gildings\"],\"gilding\":{\"min\":0.4,\"max\":1.0," +
                "\"attributes\":[\"Agility\",\"Charisma\",\"Sewing\"]}}]}";

        CraftAtlasEntry entry = WikiReferenceCatalog.parse(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8))).get(0);

        assertEquals(3, entry.gilding.attributes.size());
        assertEquals("gfx/hud/chr/agi", entry.gilding.attributes.get(0).resource);
        assertEquals("gfx/hud/chr/sewing", entry.gilding.attributes.get(2).resource);
    }

    @Test
    void parsesGildableEquipmentCategoryAndSlots() throws Exception {
        String json = "{\"entries\":[{\"id\":\"wiki:bunny-slippers\",\"name\":\"Bunny Slippers\"," +
                "\"categories\":[\"equipment\",\"equipment-shoes\"],\"equipmentSlots\":[\"11R\"]," +
                "\"gilding\":{\"min\":0.05,\"max\":0.2,\"attributes\":[\"Agility\"]}}]}";

        CraftAtlasEntry entry = WikiReferenceCatalog.parse(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8))).get(0);

        assertTrue(entry.categories.contains("equipment"));
        assertTrue(entry.categories.contains("equipment-shoes"));
        assertEquals("11R", entry.equipmentSlots.get(0));
        assertEquals("Agility", entry.gilding.attributes.get(0).name);
    }

    @Test
    void parsesCuriosityStudyMetrics() throws Exception {
        String json = "{\"entries\":[{\"id\":\"wiki:adder-fang\",\"name\":\"Adder Fang\"," +
                "\"categories\":[\"curiosities\"],\"curiosity\":{" +
                "\"learningPoints\":5000,\"studyMinutes\":1969,\"mentalWeight\":14}}]}";

        CraftAtlasEntry entry = WikiReferenceCatalog.parse(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8))).get(0);

        assertNotNull(entry.curiosity);
        assertEquals(5000, entry.curiosity.learningPoints);
        assertEquals(32.0 + 49.0 / 60.0, entry.curiosity.studyHours(), 0.0001);
        assertEquals(152.36, entry.curiosity.lpPerHour(), 0.01);
        assertEquals(10.88, entry.curiosity.lpPerHourPerWeight(), 0.01);
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
        assertTrue(has(entries, "Adder Fang", "curiosities"));
        CraftAtlasSnapshot snapshot = new MenuCraftCatalog(null, null).rebuild();
        assertTrue(snapshot.entries.size() >= 700);
        CraftAtlasEntry applePie = find(snapshot.entries, "Apple Pie");
        assertTrue(applePie.requirements.stream().anyMatch(requirement ->
                requirement.kind == CraftAtlasEntry.RequirementKind.STATION && "Oven".equals(requirement.name)));
        List<CraftAtlasEntry> producers = new CraftRecipeGraph(snapshot)
                .producers(applePie.inputs.get(0).options.get(0).resource);
        assertEquals("Unbaked Apple Pie", producers.get(0).displayName);
        CraftAtlasEntry extraStitches = find(snapshot.entries, "Extra Stitches");
        assertEquals(3, extraStitches.gilding.attributes.size());
        assertEquals("Agility", extraStitches.gilding.attributes.get(0).name);
        CraftAtlasEntry bonePins = find(snapshot.entries, "Bone Pins");
        assertTrue(bonePins.requirements.stream().anyMatch(requirement ->
                requirement.kind == CraftAtlasEntry.RequirementKind.SKILL && "Sewing".equals(requirement.name)));
        CraftAtlasEntry slippers = find(snapshot.entries, "Bunny Slippers");
        assertTrue(slippers.categories.contains("equipment-shoes"));
        assertEquals("11R", slippers.equipmentSlots.get(0));
        assertEquals("Rabbit Fur", slippers.inputs.get(0).options.get(0).name);
        assertTrue(slippers.requirements.stream().anyMatch(requirement ->
                requirement.kind == CraftAtlasEntry.RequirementKind.SKILL && "Hunting".equals(requirement.name)));
        CraftAtlasEntry amulet = find(snapshot.entries, "Adderfang Amulet");
        assertTrue(amulet.categories.contains("equipment"));
        assertEquals("2L", amulet.equipmentSlots.get(0));
        assertEquals(5, amulet.inputs.get(0).quantity);
        assertEquals("Adder Fang", amulet.inputs.get(0).options.get(0).name);
        assertTrue(amulet.bonuses.stream().anyMatch(bonus ->
                "Intelligence".equals(bonus.name) && Double.valueOf(3).equals(bonus.value)));
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
