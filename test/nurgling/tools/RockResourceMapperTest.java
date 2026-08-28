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
    void unknownItemHasNoRockTiles() {
        assertTrue(RockResourceMapper.getTileResourcesForItem("Stone Axe").isEmpty());
    }
}
