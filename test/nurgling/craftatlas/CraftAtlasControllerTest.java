package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasControllerTest {
    @Test
    void oneProducerNavigatesAndManyProducersRequestsChoiceWithoutChangingResults() {
        CraftAtlasEntry axe = recipe("axe", "axe-out", "glue-one");
        CraftAtlasEntry glue = recipe("glue-recipe", "glue-one", null);
        CraftAtlasEntry many1 = recipe("many-1", "glue-many", null);
        CraftAtlasEntry many2 = recipe("many-2", "glue-many", null);
        CraftAtlasController controller = new CraftAtlasController(
                CraftAtlasSnapshot.of(1, Arrays.asList(axe, glue, many1, many2)), null);
        controller.select("axe");
        List<String> before = ids(controller.state().results);
        controller.openIngredient("glue-one");
        assertEquals("glue-recipe", controller.state().selected.recipeResource);
        assertEquals(before, ids(controller.state().results));
        controller.openIngredient("glue-many");
        assertEquals(2, controller.state().choices.size());
    }

    @Test
    void cycleIsReportedAndSkillShowsDescription() {
        CraftAtlasEntry a = recipe("a", "out-a", "out-b");
        CraftAtlasEntry b = recipe("b", "out-b", "out-a");
        CraftAtlasController controller = new CraftAtlasController(CraftAtlasSnapshot.of(1, Arrays.asList(a, b)), null);
        controller.select("a");
        controller.openIngredient("out-b");
        controller.openIngredient("out-a");
        assertEquals("out-a", controller.state().cycleResource);
        CraftAtlasEntry.Requirement skill = new CraftAtlasEntry.Requirement(
                CraftAtlasEntry.RequirementKind.SKILL, null, "Carpentry", "Learn Carpentry");
        controller.openRequirement(skill);
        assertEquals(skill, controller.state().requirementDescription);
    }

    private CraftAtlasEntry recipe(String id, String output, String input) {
        CraftAtlasEntry.Builder b = CraftAtlasEntry.builder(id, id).output(output).availability(CraftAtlasEntry.Availability.OPEN);
        if(input != null) b.input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                new CraftAtlasEntry.IngredientOption(input, input))));
        return b.build();
    }

    private List<String> ids(List<CraftAtlasEntry> entries) {
        return entries.stream().map(e -> e.recipeResource).collect(Collectors.toList());
    }
}
