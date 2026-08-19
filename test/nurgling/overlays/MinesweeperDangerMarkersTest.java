package nurgling.overlays;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinesweeperDangerMarkersTest {

    @Test
    void greenOnlyOnMineableNeighborsOfFreshBlank() {
        Coord blank = new Coord(5, 5);
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(6, 5));
        mineable.add(new Coord(5, 6));
        mineable.add(new Coord(4, 4));

        Set<Coord> green = MinesweeperDangerMarkers.greenFromFreshBlanks(
                Set.of(blank), mineable);

        assertEquals(Set.of(new Coord(6, 5), new Coord(5, 6), new Coord(4, 4)), green);
        assertFalse(green.contains(blank));
        assertFalse(green.contains(new Coord(5, 4)));
    }

    @Test
    void noGreenWhenBlankHasNoMineableNeighbors() {
        Set<Coord> green = MinesweeperDangerMarkers.greenFromFreshBlanks(
                Set.of(new Coord(0, 0)), Set.of());
        assertTrue(green.isEmpty());
    }
}
