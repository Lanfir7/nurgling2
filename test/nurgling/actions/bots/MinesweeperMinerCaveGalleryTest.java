package nurgling.actions.bots;

import nurgling.tools.NNoticeLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinesweeperMinerCaveGalleryTest {

    @Test
    void alreadyLatchedStaysLatchedEvenWithoutNewNotices() {
        NNoticeLog log = new NNoticeLog();
        assertTrue(MinesweeperMiner.latchCaveGallery(log, 0, true));
        assertTrue(MinesweeperMiner.latchCaveGallery(null, 0, true));
    }

    @Test
    void detectsCaveGalleryCaseInsensitively() {
        NNoticeLog log = new NNoticeLog();
        long mark = log.seq();
        log.add("You opened a Natural Cave Gallery.");
        assertTrue(MinesweeperMiner.latchCaveGallery(log, mark, false));
    }

    @Test
    void oldMessagesBeforeMarkDoNotLatch() {
        NNoticeLog log = new NNoticeLog();
        log.add("You opened a natural cave gallery.");
        long mark = log.seq();
        assertFalse(MinesweeperMiner.latchCaveGallery(log, mark, false));
        log.add("unrelated");
        assertFalse(MinesweeperMiner.latchCaveGallery(log, mark, false));
    }

    @Test
    void nullLogDoesNotLatch() {
        assertFalse(MinesweeperMiner.latchCaveGallery(null, 0, false));
    }
}
