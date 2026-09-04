package nurgling.craftatlas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasObservationStoreTest {
    @TempDir Path temp;

    @Test
    void roundTripsCompleteObservationAndMergesByRecipeResource() throws Exception {
        Path file = temp.resolve("observed.json");
        CraftAtlasObservationStore store = new CraftAtlasObservationStore(file);
        store.record(observation("axe", "Glue", "gfx/terobjs/workbench"));
        store.record(observation("axe", "Fish Glue", "gfx/invobjs/hammer"));
        CraftAtlasObservation loaded = new CraftAtlasObservationStore(file).get("axe");
        assertEquals("Fish Glue", loaded.inputs.get(0).name);
        assertEquals("gfx/invobjs/hammer", loaded.requirements.get(0).resource);
        assertEquals(1, loaded.inputs.size());
    }

    @Test
    void corruptAndSeparateStoresDoNotLeak() throws Exception {
        Path bad = temp.resolve("bad.json");
        Files.write(bad, "bad".getBytes(StandardCharsets.UTF_8));
        assertTrue(new CraftAtlasObservationStore(bad).all().isEmpty());
        assertEquals("bad", new String(Files.readAllBytes(bad), StandardCharsets.UTF_8));
        assertTrue(new CraftAtlasObservationStore(temp.resolve("other.json")).all().isEmpty());
    }

    private CraftAtlasObservation observation(String recipe, String input, String requirement) {
        return new CraftAtlasObservation(recipe, "Axe",
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/glue", input, 2, false)),
                Arrays.asList(new CraftAtlasObservation.Item("gfx/invobjs/axe", "Axe", 1, false)),
                Arrays.asList(new CraftAtlasObservation.RequirementResource(requirement, "Requirement")),
                Collections.<CraftAtlasObservation.BonusResource>emptyList());
    }
}
