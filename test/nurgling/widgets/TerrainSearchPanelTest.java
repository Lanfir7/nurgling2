package nurgling.widgets;

import haven.TileHighlight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainSearchPanelTest {
    @AfterEach
    void clearHighlight() {
        TileHighlight.clear();
    }

    @Test
    void forageTerrainSelectionReplacesPreviousTileHighlight() {
        TileHighlight.setHighlighted(Arrays.asList("gfx/tiles/old"));

        TerrainSearchPanel.applyTerrainHighlight(Arrays.asList("Bog", "Fen"));

        assertEquals(new HashSet<>(Arrays.asList("gfx/tiles/bog", "gfx/tiles/fen")),
                TileHighlight.getHighlighted());
    }

    @Test
    void exactGrassPresetDoesNotExpandToGrasslandGroup() {
        assertEquals(Set.of("gfx/tiles/grass"), TerrainSearchPanel.resourceNamesForPattern("grass"));
    }
}
