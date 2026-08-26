package nurgling.actions.bots;

import haven.Coord2d;
import haven.MCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedCloverTest {
    @Test
    void wildHorseMatchesGobPath() {
        assertTrue(FeedClover.isWildHorse("gfx/kritter/horse/horse"));
        assertFalse(FeedClover.isWildHorse("gfx/kritter/horse/stallion"));
        assertFalse(FeedClover.isWildHorse("gfx/kritter/horse/mare"));
        assertFalse(FeedClover.isWildHorse("gfx/kritter/horse/foal"));
        assertFalse(FeedClover.isWildHorse("gfx/kritter/cattle/cattle"));
        assertFalse(FeedClover.isWildHorse(null));
    }

    @Test
    void feedReachIsOneTile() {
        assertEquals(MCache.tilesz.x, FeedClover.FEED_REACH);
    }

    @Test
    void feedsWhenCloserThanOneTile() {
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(FeedClover.closeEnoughToFeed(player, Coord2d.of(0, 0)));
        assertTrue(FeedClover.closeEnoughToFeed(player, Coord2d.of(10.9, 0)));
    }

    @Test
    void doesNotFeedAtOneTileOrFarther() {
        Coord2d player = Coord2d.of(0, 0);
        assertFalse(FeedClover.closeEnoughToFeed(player, Coord2d.of(MCache.tilesz.x, 0)));
        assertFalse(FeedClover.closeEnoughToFeed(player, Coord2d.of(22, 0)));
    }

    @Test
    void closeEnoughToFeedNullSafe() {
        assertFalse(FeedClover.closeEnoughToFeed(null, Coord2d.of(0, 0)));
        assertFalse(FeedClover.closeEnoughToFeed(Coord2d.of(0, 0), null));
    }
}
