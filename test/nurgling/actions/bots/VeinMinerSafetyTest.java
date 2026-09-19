package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerSafetyTest {
    @Test
    void supportMaskMatchesBeginOffset() {
        boolean[][] data = new boolean[][] {
                {true, false},
                {false, true}
        };
        Coord begin = new Coord(10, 20);
        assertTrue(VeinMiner.supportCovers(new Coord(10, 20), begin, data));
        assertTrue(VeinMiner.supportCovers(new Coord(11, 21), begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(11, 20), begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(9, 20), begin, data));
        assertFalse(VeinMiner.supportCovers(null, begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(10, 20), begin, null));
    }
}
