package nurgling.actions;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderBlockedStartTest {
    @Test
    void findsNearestFreeCellOutsideDenseObstacleCluster() {
        Coord start = Coord.of(2, 2);

        Coord exit = PathFinder.nearestFreeCell(start, 5, 5,
                cell -> cell.x == 0 || cell.x == 4 || cell.y == 0 || cell.y == 4);

        assertNotNull(exit);
        assertTrue(exit.x == 0 || exit.x == 4 || exit.y == 0 || exit.y == 4);
        assertEquals(2, Math.max(Math.abs(exit.x - start.x), Math.abs(exit.y - start.y)));
    }
}
