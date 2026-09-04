package nurgling.tools;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerCapacityTest {
    @Test
    void regularContainerUsesItsUpdatedFreeSpace() {
        Container container = container();
        container.initattr(Container.Space.class);
        Container.Space space = container.getattr(Container.Space.class);

        assertEquals(1, container.freeSpace());
        assertFalse(container.isFull());

        space.getRes().put(Container.Space.FREESPACE, 0);

        assertEquals(0, container.freeSpace());
        assertTrue(container.isFull());
    }

    @Test
    void tetrisCapacityUsesTheObservedItemShape() {
        Container container = container();
        container.initattr(Container.Tetris.class);
        Container.Tetris tetris = container.getattr(Container.Tetris.class);
        tetris.getRes().put(Container.Tetris.SRC, new short[3][2]);
        tetris.getRes().put(Container.Tetris.TARGET_COORD,
                new ArrayList<>(List.of(new Coord(2, 1), new Coord(1, 1))));
        tetris.getRes().put(Container.Tetris.DONE, false);

        assertEquals(2, container.freeSpace(new Coord(2, 1)));
        assertEquals(2, container.freeSpace());
        assertFalse(container.isFull());

        tetris.getRes().put(Container.Tetris.DONE, true);
        assertTrue(container.isFull());
    }

    private static Container container() {
        return new Container(new Gob(null, Coord2d.of(0, 0), 1), "test", null);
    }
}
