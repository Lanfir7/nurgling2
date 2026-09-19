package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerHelpersTest {

    @Test
    void neighborsStillLoadingWhenAdjacentTileUnloaded() {
        Coord mined = new Coord(5, 5);
        Map<Coord, String> tiles = new HashMap<>();
        tiles.put(new Coord(5, 4), null);
        Function<Coord, String> tileName = tiles::get;

        assertTrue(VeinMiner.neighborsStillLoading(Collections.singletonList(mined), tileName));
    }

    @Test
    void neighborsStillLoadingIgnoresMinedNeighbours() {
        Coord a = new Coord(5, 5);
        Coord b = new Coord(5, 6);
        Map<Coord, String> tiles = new HashMap<>();
        tiles.put(new Coord(5, 7), null);
        Function<Coord, String> tileName = tiles::get;

        assertTrue(VeinMiner.neighborsStillLoading(Arrays.asList(a, b), tileName));
    }

    @Test
    void neighborsStillLoadingFalseWhenAllNeighboursKnown() {
        Coord mined = new Coord(5, 5);
        Map<Coord, String> tiles = new HashMap<>();
        for (int[] d : VeinWorklist.NEIGHBORS) {
            tiles.put(new Coord(5 + d[0], 5 + d[1]), "gfx/tiles/rock");
        }
        Function<Coord, String> tileName = c -> tiles.get(c);

        assertFalse(VeinMiner.neighborsStillLoading(Collections.singletonList(mined), tileName));
    }

    @Test
    void seedWaitCompleteWhenSeedCaptured() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        cap.offer(new Coord(1, 2), null);

        assertTrue(VeinMiner.seedWaitComplete(cap, "mine"));
    }

    @Test
    void neighborsStillLoadingReturnsFalseForNullInputs() {
        assertFalse(VeinMiner.neighborsStillLoading(null, c -> "tile"));
        assertFalse(VeinMiner.neighborsStillLoading(Collections.singletonList(new Coord(0, 0)), null));
    }

    @Test
    void seedWaitCompleteWhenCursorLeavesMine() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();

        assertFalse(VeinMiner.seedWaitComplete(cap, null));
        assertFalse(VeinMiner.seedWaitComplete(cap, "mine"));
        assertTrue(VeinMiner.seedWaitComplete(cap, "arw"));
        assertTrue(VeinMiner.seedWaitComplete(cap, "hand"));
    }
}
