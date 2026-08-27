package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

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
}
