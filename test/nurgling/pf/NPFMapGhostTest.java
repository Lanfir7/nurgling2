package nurgling.pf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPFMapGhostTest {
    @Test
    void ghostPreviewDoesNotBlockPathfinding() {
        assertFalse(NPFMap.isPathObstacle(true, false, false, true));
    }

    @Test
    void realObstacleStillBlocks() {
        assertTrue(NPFMap.isPathObstacle(true, false, false, false));
    }

    @Test
    void playerAndFollowersAreIgnored() {
        assertFalse(NPFMap.isPathObstacle(true, true, false, false));
        assertFalse(NPFMap.isPathObstacle(true, false, true, false));
        assertFalse(NPFMap.isPathObstacle(false, false, false, false));
    }
}
