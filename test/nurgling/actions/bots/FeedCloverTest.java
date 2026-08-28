package nurgling.actions.bots;

import haven.Coord2d;
import nurgling.NHitBox;
import org.junit.jupiter.api.Test;

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
    void hitboxesTouchWhenBoxesMeet() {
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(FeedClover.hitboxesTouch(PLAYER, player, 0, HORSE, Coord2d.of(11.0, 0), 0));
        assertFalse(FeedClover.hitboxesTouch(PLAYER, player, 0, HORSE, Coord2d.of(11.1, 0), 0));
        assertTrue(FeedClover.hitboxesTouch(PLAYER, player, 0, HORSE, Coord2d.of(0, 7.0), 0));
        assertFalse(FeedClover.hitboxesTouch(PLAYER, player, 0, HORSE, Coord2d.of(0, 7.1), 0));
    }

    @Test
    void hitboxesTouchNullSafe() {
        assertFalse(FeedClover.hitboxesTouch(null, Coord2d.of(0, 0), 0, HORSE, Coord2d.of(0, 0), 0));
        assertFalse(FeedClover.hitboxesTouch(PLAYER, null, 0, HORSE, Coord2d.of(0, 0), 0));
        assertFalse(FeedClover.hitboxesTouch(PLAYER, Coord2d.of(0, 0), 0, HORSE, null, 0));
    }
}
