package nurgling.widgets;

import haven.TileHighlight;
import nurgling.tools.RockResourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void exactResourceSelectionDoesNotExpandRockName() {
        TerrainSearchPanel.applyResourceHighlight(Arrays.asList("gfx/tiles/rocks/quartz"));

        assertEquals(Set.of("gfx/tiles/rocks/quartz"), TileHighlight.getHighlighted());
    }

    @Test
    void questRockNamesMatchEveryTerrainSearchRockPreset() {
        Set<String> coveredPatterns = new HashSet<>();
        String[] directNames = {
                "Cassiterite", "Chalcopyrite", "Malachite", "Cinnabar", "Galena",
                "Horn Silver", "Black Coal", "Alabaster", "Apatite", "Arkose", "Basalt",
                "Breccia", "Chert", "Diabase", "Diorite", "Dolomite", "Eclogite",
                "Feldspar", "Flint", "Fluorospar", "Gabbro", "Gneiss", "Granite",
                "Graywacke", "Greenschist", "Hornblende", "Jasper", "Kyanite",
                "Limestone", "Marble", "Mica", "Microlite", "Olivine", "Orthoclase",
                "Pegmatite", "Porphyry", "Pumice", "Quartz", "Rhyolite", "Sandstone",
                "Schist", "Serpentine", "Slate", "Soapstone", "Sodalite", "Sunstone",
                "Zincspar"
        };
        for(String displayName : directNames) {
            String resourceName = normalize(displayName);
            assertQuestAndTerrainMatch(displayName, resourceName, resourceName);
            coveredPatterns.add(resourceName);
        }

        String[][] aliases = {
                {"Heavy Earth", "heavyearth", "ilmenite"},
                {"Iron Ochre", "ironochre", "limonite"},
                {"Bloodstone", "bloodstone", "hematite"},
                {"Black Ore", "blackore", "magnetite"},
                {"Silvershine", "silvershine", "argentite"},
                {"Wine Glance", "wineglance", "cuprite"},
                {"Lead Glance", "leadglance", "leadglance"},
                {"Leaf Ore", "leafore", "petzite"},
                {"Schrifterz", "schrifterz", "sylvanite"},
                {"Direvein", "direvein", "nagyagite"},
                {"Korund", "korund", "corund"},
                {"Quarryartz", "quarryartz", "quartz"},
                {"Rock Salt", "rocksalt", "halite"}
        };
        for(String[] alias : aliases) {
            assertQuestAndTerrainMatch(alias[0], alias[1], alias[2]);
            coveredPatterns.add(alias[1]);
        }

        for(TerrainSearchPanel.TerrainCategory category : TerrainSearchPanel.TerrainCategory.ALL_CATEGORIES) {
            if(!category.name.equals("Ore") && !category.name.equals("Rocks"))
                continue;
            for(TerrainSearchPanel.TerrainPreset preset : category.presets)
                assertTrue(coveredPatterns.contains(preset.searchPattern), preset.displayName);
        }
    }

    private static void assertQuestAndTerrainMatch(String displayName, String terrainPattern,
                                                   String resourceName) {
        Set<String> expected = Collections.singleton("gfx/tiles/rocks/" + resourceName);
        assertEquals(expected, RockResourceMapper.getTileResourcesForItem(displayName), displayName);
        assertEquals(expected, TerrainSearchPanel.resourceNamesForPattern(terrainPattern), terrainPattern);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
