package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockResourceMapperTest {
    @Test
    void quartzResolvesToExactMineTile() {
        assertEquals(Collections.singleton("gfx/tiles/rocks/quartz"),
                RockResourceMapper.getTileResourcesForItem("Quartz"));
    }

    @Test
    void oreDisplayNameResolvesToCanonicalMineTile() {
        assertEquals(Collections.singleton("gfx/tiles/rocks/ilmenite"),
                RockResourceMapper.getTileResourcesForItem("Heavy Earth"));
        assertEquals(Collections.singleton("gfx/tiles/rocks/hematite"),
                RockResourceMapper.getTileResourcesForItem("Bloodstone"));
    }

    @Test
    void oreDisplayNamesMatchTerrainSearchAliases() {
        assertEquals(Collections.singleton("gfx/tiles/rocks/argentite"),
                RockResourceMapper.getTileResourcesForItem("Silvershine"));
        assertEquals(Collections.singleton("gfx/tiles/rocks/quartz"),
                RockResourceMapper.getTileResourcesForItem("Quarryartz"));
        assertEquals(Collections.singleton("gfx/tiles/rocks/halite"),
                RockResourceMapper.getTileResourcesForItem("Rock Salt"));
    }

    @Test
    void unknownItemHasNoRockTiles() {
        assertTrue(RockResourceMapper.getTileResourcesForItem("Stone Axe").isEmpty());
    }
}
