package nurgling.overlays.map;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiningOverlayMaskTest {
    @Test
    void supportCoveringWholeCutStillPaintsIt() {
        boolean[][] coverage = new boolean[20][20];
        for (boolean[] column : coverage)
            Arrays.fill(column, true);

        boolean[][] mask = NMiningOverlay.maskForCut(new Coord(0, 0), new Coord(2, 2),
                Collections.singletonList(new NMiningOverlay.Coverage(
                        new Coord(-10, -10), new Coord(10, 10), coverage)));

        assertTrue(mask[1][1]);
        assertTrue(mask[2][2]);
    }

    @Test
    void separateCutsDoNotShareMutableMask() {
        boolean[][] oneTile = {{true}};
        Coord cut = new Coord(0, 0);
        Coord size = new Coord(2, 2);
        boolean[][] first = NMiningOverlay.maskForCut(cut, size,
                Collections.singletonList(new NMiningOverlay.Coverage(
                        new Coord(0, 0), new Coord(1, 1), oneTile)));
        boolean[][] second = NMiningOverlay.maskForCut(cut, size,
                Collections.singletonList(new NMiningOverlay.Coverage(
                        new Coord(10, 10), new Coord(11, 11), oneTile)));

        assertNotSame(first, second);
        assertTrue(first[1][1]);
        assertFalse(second[1][1]);
    }
}
