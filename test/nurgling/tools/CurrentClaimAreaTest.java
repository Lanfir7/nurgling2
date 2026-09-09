package nurgling.tools;

import haven.Coord;
import haven.MCache;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentClaimAreaTest {
    @Test
    void identityIsStableWhenSessionGridCoordinatesChange() {
        boolean[] firstMask = mask(tile(10, 20), tile(11, 20));
        boolean[] secondMask = mask(tile(10, 20), tile(11, 20));

        ClaimArea first = CurrentClaimArea.capture(Coord.of(110, 220), Collections.singletonList(
                new CurrentClaimArea.GridMask(Coord.of(1, 2), 99123L, firstMask)));
        ClaimArea afterRelog = CurrentClaimArea.capture(Coord.of(-490, 720), Collections.singletonList(
                new CurrentClaimArea.GridMask(Coord.of(-5, 7), 99123L, secondMask)));

        assertEquals(first, afterRelog);
        assertTrue(afterRelog.contains(99123L, 10, 20));
        assertTrue(afterRelog.contains(99123L, 11, 20));
    }

    @Test
    void capturesOnlyTheConnectedClaimUnderThePlayer() {
        boolean[] mask = mask(tile(10, 10), tile(11, 10), tile(70, 70));

        ClaimArea area = CurrentClaimArea.capture(Coord.of(10, 10), Collections.singletonList(
                new CurrentClaimArea.GridMask(Coord.z, 77L, mask)));

        assertEquals(2, area.size());
        assertTrue(area.contains(77L, 10, 10));
        assertFalse(area.contains(77L, 70, 70));
    }

    @Test
    void followsAClaimAcrossStableMapGrids() {
        boolean[] left = mask(tile(99, 50));
        boolean[] right = mask(tile(0, 50), tile(1, 50));

        ClaimArea area = CurrentClaimArea.capture(Coord.of(99, 50), Arrays.asList(
                new CurrentClaimArea.GridMask(Coord.z, 1001L, left),
                new CurrentClaimArea.GridMask(Coord.of(1, 0), 1002L, right)));

        assertEquals(3, area.size());
        assertTrue(area.contains(1001L, 99, 50));
        assertTrue(area.contains(1002L, 0, 50));
        assertTrue(area.contains(1002L, 1, 50));
    }

    private static int tile(int x, int y) {
        return x + (y * MCache.cmaps.x);
    }

    private static boolean[] mask(int... tiles) {
        boolean[] result = new boolean[MCache.cmaps.x * MCache.cmaps.y];
        for (int tile : tiles)
            result[tile] = true;
        return result;
    }
}
