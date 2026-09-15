package nurgling.actions.bots.forager;

import haven.Coord2d;
import haven.MCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteLookaheadTest {

    @Test
    void leavesAGobForALaterStopOnlyWhenItIsClearlyCloser() {
        Coord2d anchor = Coord2d.of(0, 0);
        Coord2d gob = Coord2d.of(MCache.tilesz.x * 20, 0);

        assertTrue(RouteLookahead.isClearlyCloser(anchor, gob, gob));
        assertFalse(RouteLookahead.isClearlyCloser(anchor,
                Coord2d.of(MCache.tilesz.x * 2, 0), gob));
    }

    @Test
    void exactSafetyMarginDoesNotDeferTheGob() {
        Coord2d anchor = Coord2d.of(0, 0);
        Coord2d gob = Coord2d.of(MCache.tilesz.x * 10, 0);
        Coord2d laterStop = Coord2d.of(MCache.tilesz.x * 2, 0);

        assertFalse(RouteLookahead.isClearlyCloser(anchor, laterStop, gob));
    }
}
