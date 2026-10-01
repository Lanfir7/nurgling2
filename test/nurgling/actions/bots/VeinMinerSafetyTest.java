package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void aCompletedSupportMaskOverridesTheNominalCircle() {
        Coord2d center = new Coord2d(5.5, 5.5);
        boolean[][] youngTreeMask = {{true}};
        assertTrue(VeinMiner.supportCoversOrCircle(new Coord(0, 0),
                new Coord(0, 0), youngTreeMask, center,
                "gfx/terobjs/trees/towercap", 1));
        assertFalse(VeinMiner.supportCoversOrCircle(new Coord(1, 0),
                new Coord(0, 0), youngTreeMask, center,
                "gfx/terobjs/trees/towercap", 1));
        assertFalse(VeinMiner.supportCoversOrCircle(new Coord(1, 0),
                new Coord(0, 0), youngTreeMask, center,
                "gfx/terobjs/minebeam", 1));
    }

    @Test
    void fallbackOnlyAcceptsBuiltExactStaticSupports() {
        Coord tile = new Coord(0, 0);
        Coord2d center = new Coord2d(5.5, 5.5);
        assertTrue(VeinMiner.supportCoversOrCircle(tile, null, null, center,
                "gfx/terobjs/minebeam", 1));
        assertFalse(VeinMiner.supportCoversOrCircle(tile, null, null, center,
                "gfx/terobjs/minebeam", -1));
        assertFalse(VeinMiner.supportCoversOrCircle(tile, null, null, center,
                "gfx/terobjs/trees/towercap", 1));
        assertFalse(VeinMiner.supportCoversOrCircle(tile, null, null, center,
                "gfx/terobjs/minebeam-construction", 1));
    }

    @Test
    void safeMarkerMustBeOnTheRequestedTile() {
        assertTrue(VeinMiner.markerMatchesTile(new Coord(0, 0), new Coord2d(5.5, 5.5)));
        assertFalse(VeinMiner.markerMatchesTile(new Coord(1, 0), new Coord2d(5.5, 5.5)));
        assertFalse(VeinMiner.markerMatchesTile(new Coord(0, 0), new Coord2d(11, 5.5)));
    }

    @Test
    void seedFinishedRequiresDifferentNonNullName() {
        assertFalse(VeinMiner.seedFinished(null, "gfx/tiles/rock"));
        assertFalse(VeinMiner.seedFinished("gfx/tiles/rock", null));
        assertFalse(VeinMiner.seedFinished("gfx/tiles/rock", "gfx/tiles/rock"));
        assertTrue(VeinMiner.seedFinished("gfx/tiles/rock", "gfx/tiles/cave"));
    }

    @Test
    void inSupportRadiusUsesTileCentre() {
        haven.Coord2d gob = new haven.Coord2d(5 * 11 + 5.5, 5 * 11 + 5.5);
        assertTrue(VeinMiner.inSupportRadius(new Coord(5, 5), gob, 100));
        assertTrue(VeinMiner.inSupportRadius(new Coord(8, 5), gob, 100));
        assertFalse(VeinMiner.inSupportRadius(new Coord(20, 5), gob, 100));
        assertFalse(VeinMiner.inSupportRadius(null, gob, 100));
        assertFalse(VeinMiner.inSupportRadius(new Coord(5, 5), null, 100));
    }

    @Test
    void supportRadiusForKnownSupports() {
        assertEquals(150, VeinMiner.supportRadiusFor("gfx/terobjs/minebeam"));
        assertEquals(125, VeinMiner.supportRadiusFor("gfx/terobjs/column"));
        assertEquals(330, VeinMiner.supportRadiusFor("gfx/terobjs/monumentalcolumn"));
        assertEquals(100, VeinMiner.supportRadiusFor("gfx/terobjs/minesupport"));
        assertEquals(92, VeinMiner.supportRadiusFor("gfx/terobjs/map/naturalminesupport"));
        assertEquals(-1, VeinMiner.supportRadiusFor("gfx/terobjs/trees/towercap"));
        assertEquals(-1, VeinMiner.supportRadiusFor("gfx/terobjs/minebeam-construction"));
        assertEquals(-1, VeinMiner.supportRadiusFor("gfx/terobjs/cheeserack"));
        assertEquals(-1, VeinMiner.supportRadiusFor(null));
    }
}
