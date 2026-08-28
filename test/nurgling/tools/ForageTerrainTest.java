package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForageTerrainTest {
    @Test
    void parsesLegacyTerrainByLongestKnownBiomeName() {
        assertEquals(Arrays.asList("Leaf Patch", "Shady Copse", "Green Brake", "Ox Pasture"),
                ForageTerrain.parse("Leaf Patch Shady Copse Green Brake Ox Pasture"));
    }

    @Test
    void parsesAndDisplaysExplicitCommaSeparatedTerrain() {
        assertEquals(Arrays.asList("Bog", "Fen", "Shallow Water"),
                ForageTerrain.parse("Bog, Fen, Shallow Water"));
        assertEquals("Bog, Fen, Shallow Water",
                ForageTerrain.join(Arrays.asList("Bog", "Fen", "Shallow Water")));
    }

    @Test
    void convertsBiomeAliasesAndGroupsToMapTileResources() {
        LinkedHashSet<String> resources = new LinkedHashSet<>(ForageTerrain.resourceNames(
                Arrays.asList("Green Brake", "Shallow Water", "Forest")));

        assertTrue(resources.contains("gfx/tiles/greenbrake"));
        assertTrue(resources.contains("gfx/tiles/water"));
        assertTrue(resources.contains("gfx/tiles/owater"));
        assertTrue(resources.contains("gfx/tiles/beechgrove"));
        assertTrue(resources.contains("gfx/tiles/pinebarren"));
    }
}
