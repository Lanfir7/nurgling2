package nurgling.actions;

import haven.Coord2d;
import haven.MCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPfTest {
    @Test
    void defaultReachStaysOneAndHalfTiles() {
        assertEquals(MCache.tilesz.x * 1.5, DynamicPf.DEFAULT_REACH);
    }

    @Test
    void withinReachUsesCallerThreshold() {
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(DynamicPf.isWithinReach(player, Coord2d.of(10.9, 0), MCache.tilesz.x));
        assertFalse(DynamicPf.isWithinReach(player, Coord2d.of(11, 0), MCache.tilesz.x));
        assertTrue(DynamicPf.isWithinReach(player, Coord2d.of(16.4, 0), DynamicPf.DEFAULT_REACH));
        assertFalse(DynamicPf.isWithinReach(player, Coord2d.of(16.5, 0), DynamicPf.DEFAULT_REACH));
    }

    @Test
    void withinReachNullSafe() {
        assertFalse(DynamicPf.isWithinReach(null, Coord2d.of(0, 0), 11));
        assertFalse(DynamicPf.isWithinReach(Coord2d.of(0, 0), null, 11));
    }
}
