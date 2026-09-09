package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementSweepTest {

    @Test
    void scanCoordinatesRunFromTheDragStartCorner() {
        assertArrayEquals(new int[]{4, 3, 2, 1}, PlacementSweep.axis(1, 4, 9, 2));
        assertArrayEquals(new int[]{1, 2, 3, 4}, PlacementSweep.axis(1, 4, 2, 9));
    }

    @Test
    void ordersSlotsFromTheCornerWhereSelectionStarted() throws Exception {
        List<Coord2d> slots = Arrays.asList(
                Coord2d.of(0, 0), Coord2d.of(11, 0),
                Coord2d.of(0, 11), Coord2d.of(11, 11));

        assertEquals(Arrays.asList(
                        Coord2d.of(11, 11), Coord2d.of(11, 0), Coord2d.of(0, 11)),
                PlacementSweep.order(slots, Coord.of(2, 2), Coord.of(0, 0), 3));
        assertEquals(Arrays.asList(
                        Coord2d.of(11, 0), Coord2d.of(11, 11), Coord2d.of(0, 0)),
                PlacementSweep.order(slots, Coord.of(2, 0), Coord.of(0, 2), 3));
        assertEquals(Arrays.asList(
                        Coord2d.of(0, 11), Coord2d.of(0, 0), Coord2d.of(11, 11)),
                PlacementSweep.order(slots, Coord.of(0, 2), Coord.of(2, 0), 3));
    }

    @Test
    void limitsGhostsToTheNumberOfObjectsBeingMoved() throws Exception {
        List<Coord2d> slots = Arrays.asList(
                Coord2d.of(0, 0), Coord2d.of(11, 0), Coord2d.of(22, 0));

        assertEquals(Arrays.asList(Coord2d.of(0, 0), Coord2d.of(11, 0)),
                PlacementSweep.order(slots, Coord.of(0, 0), Coord.of(2, 0), 2));
        assertEquals(java.util.Collections.emptyList(),
                PlacementSweep.order(slots, Coord.of(0, 0), Coord.of(2, 0), 0));
    }
}
