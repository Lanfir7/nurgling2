package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredAreaGridGenerationTest {
    @Test
    void changingOneGridLeavesTheOtherRevision() {
        ExploredArea area = new ExploredArea(null);
        area.updateExploredTiles(Coord.z, Coord.of(1, 1), 5);
        long first = area.gridGeneration(0, 0, 5, false);

        area.updateExploredTiles(Coord.of(100, 0), Coord.of(101, 1), 5);

        assertEquals(first, area.gridGeneration(0, 0, 5, false));
        assertTrue(area.gridGeneration(1, 0, 5, false) > 0);
        assertEquals(0, area.gridGeneration(2, 0, 5, false));
    }

    @Test
    void sessionLayerHasItsOwnRevision() {
        ExploredArea area = new ExploredArea(null);
        area.startSession();
        area.updateExploredTiles(Coord.z, Coord.of(1, 1), 5);

        assertTrue(area.gridGeneration(0, 0, 5, false) > 0);
        assertTrue(area.gridGeneration(0, 0, 5, true) > 0);
        assertEquals(0, area.gridGeneration(1, 0, 5, true));
    }
}
