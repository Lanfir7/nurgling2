package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;

class PathFinderAdditionalObstacleTest {
    @Test
    void syntheticObstacleUnderTheStartCellResolvesWithoutATargetDummy() {
        Gob obstacle = new Gob(null, Coord2d.of(10, 10), -1);

        Gob resolved = PathFinder.resolveObstacle(
                -1, null, Collections.singletonList(obstacle), id -> null);

        assertSame(obstacle, resolved);
    }
}
