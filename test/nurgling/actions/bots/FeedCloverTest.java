package nurgling.actions.bots;

import haven.Coord2d;
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

    @Test
    void feedsWithinHalfTileOfHitboxContact() {
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(16.5, 0), 0));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(16.6, 0), 0));
        assertTrue(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(0, 12.5), 0));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(0, 12.6), 0));
        assertTrue(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(14.8, 10.8), 0));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0, HORSE, Coord2d.of(16.5, 12.5), 0));
    }

    @Test
    void feedRangeUsesRotatedHitboxes() {
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0,
                HORSE, Coord2d.of(0, 16.5), Math.PI / 2));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0,
                HORSE, Coord2d.of(0, 16.6), Math.PI / 2));
    }

    @Test
    void feedRangeMatchesAsymmetricHitboxOrientation() {
        NHitBox asymmetric = new NHitBox(Coord2d.of(-10, -4), Coord2d.of(7, 4), true);
        Coord2d player = Coord2d.of(0, 0);
        assertTrue(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0,
                asymmetric, Coord2d.of(15.5, 0), 0));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0,
                asymmetric, Coord2d.of(15.6, 0), 0));
        assertFalse(FeedClover.hitboxesWithinFeedRange(PLAYER, player, 0,
                asymmetric, Coord2d.of(18.5, 0), 0));
    }

    @Test
    void feedRangeIsNullSafe() {
        assertFalse(FeedClover.hitboxesWithinFeedRange(null, Coord2d.of(0, 0), 0,
                HORSE, Coord2d.of(0, 0), 0));
    }

    @Test
    void stoppedPlayerRetriesUntilContactBeforeActivating() throws InterruptedException {
        FakeContactDriver driver = new FakeContactDriver(3);

        assertEquals(FeedClover.FeedContactResult.ACTIVATED, FeedClover.pursueAndActivate(driver));
        assertEquals(3, driver.approaches);
        assertEquals(1, driver.activations);
    }

    @Test
    void neverActivatesWithoutContact() throws InterruptedException {
        FakeContactDriver driver = new FakeContactDriver(FeedClover.CLOSE_IN_STEPS + 1);

        assertEquals(FeedClover.FeedContactResult.EXHAUSTED, FeedClover.pursueAndActivate(driver));
        assertEquals(FeedClover.CLOSE_IN_STEPS, driver.approaches);
        assertEquals(0, driver.activations);
    }

    @Test
    void activatesWhenFinalApproachReachesContact() throws InterruptedException {
        FakeContactDriver driver = new FakeContactDriver(FeedClover.CLOSE_IN_STEPS);

        assertEquals(FeedClover.FeedContactResult.ACTIVATED, FeedClover.pursueAndActivate(driver));
        assertEquals(FeedClover.CLOSE_IN_STEPS, driver.approaches);
        assertEquals(1, driver.activations);
    }

    @Test
    void missingPlayerStopsPursuitWithoutActivation() throws InterruptedException {
        FakeContactDriver driver = new FakeContactDriver(0);
        driver.playerPresent = false;

        assertEquals(FeedClover.FeedContactResult.PLAYER_GONE, FeedClover.pursueAndActivate(driver));
        assertEquals(0, driver.approaches);
        assertEquals(0, driver.activations);
    }

    private static final class FakeContactDriver implements FeedClover.FeedContactDriver {
        final int contactAfterApproaches;
        int approaches;
        int activations;
        boolean playerPresent = true;

        FakeContactDriver(int contactAfterApproaches) {
            this.contactAfterApproaches = contactAfterApproaches;
        }

        @Override
        public boolean animalPresent() {
            return true;
        }

        @Override
        public boolean playerPresent() {
            return playerPresent;
        }

        @Override
        public boolean activateIfTouching() {
            if (approaches < contactAfterApproaches)
                return false;
            activations++;
            return true;
        }

        @Override
        public void approach() {
            approaches++;
        }
    }
}
