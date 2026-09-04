package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftRecipeGraphTest {
    @Test
    void returnsEveryRecipeThatProducesGlue() {
        CraftRecipeGraph graph = new CraftRecipeGraph(CraftAtlasSnapshot.of(1, Arrays.asList(
                recipe("bone-glue", "gfx/invobjs/glue", null),
                recipe("fish-glue", "gfx/invobjs/glue", null))));
        List<String> ids = graph.producers("gfx/invobjs/glue").stream()
                .map(e -> e.recipeResource).sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("bone-glue", "fish-glue"), ids);
    }

    @Test
    void reportsCycleWithoutRecursing() {
        CraftRecipeGraph graph = new CraftRecipeGraph(CraftAtlasSnapshot.of(1, Arrays.asList(
                recipe("a", "out-a", "out-b"), recipe("b", "out-b", "out-a"))));
        assertEquals(CraftRecipeGraph.LinkState.CYCLE,
                graph.linkState("out-a", Arrays.asList("a", "b")));
        assertEquals(CraftRecipeGraph.LinkState.NONE, graph.linkState("missing", Collections.emptyList()));
    }

    private CraftAtlasEntry recipe(String id, String output, String input) {
        CraftAtlasEntry.Builder builder = CraftAtlasEntry.builder(id, id).output(output);
        if(input != null)
            builder.input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                    new CraftAtlasEntry.IngredientOption(input, input))));
        return builder.build();
    }
}
