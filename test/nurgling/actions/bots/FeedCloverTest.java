package nurgling.actions.bots;

import haven.Coord2d;
import haven.MCache;
import nurgling.NHitBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedCloverTest {
    private static final NHitBox HORSE = new NHitBox(Coord2d.of(-8, -4), Coord2d.of(8, 4), true);
    private static final NHitBox PLAYER = new NHitBox(Coord2d.of(-3, -3), Coord2d.of(3, 3), true);

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
    void feedReachIsHitboxesTouchingPlusHalfTile() {
        assertEquals(8 + 3 + MCache.tilehsz.x, FeedClover.feedReach(PLAYER, HORSE), 1e-6);
    }

    @Test
    void feedReachFallsBackToTwoTilesWhenHitboxMissing() {
        assertEquals(MCache.tilesz.x * 2, FeedClover.feedReach(null, HORSE), 1e-6);
        assertEquals(MCache.tilesz.x * 2, FeedClover.feedReach(PLAYER, null), 1e-6);
    }

    @Test
    void feedsWhenCloserThanHitboxReach() {
        Coord2d player = Coord2d.of(0, 0);
        double reach = FeedClover.feedReach(PLAYER, HORSE);
        assertTrue(FeedClover.closeEnoughToFeed(player, Coord2d.of(0, 0), PLAYER, HORSE));
        assertTrue(FeedClover.closeEnoughToFeed(player, Coord2d.of(reach - 0.1, 0), PLAYER, HORSE));
    }

    @Test
    void doesNotFeedAtHitboxReachOrFarther() {
        Coord2d player = Coord2d.of(0, 0);
        double reach = FeedClover.feedReach(PLAYER, HORSE);
        assertFalse(FeedClover.closeEnoughToFeed(player, Coord2d.of(reach, 0), PLAYER, HORSE));
        assertFalse(FeedClover.closeEnoughToFeed(player, Coord2d.of(reach + 5, 0), PLAYER, HORSE));
    }

    @Test
    void closeEnoughToFeedNullSafe() {
        assertFalse(FeedClover.closeEnoughToFeed(null, Coord2d.of(0, 0), PLAYER, HORSE));
        assertFalse(FeedClover.closeEnoughToFeed(Coord2d.of(0, 0), null, PLAYER, HORSE));
    }
}
