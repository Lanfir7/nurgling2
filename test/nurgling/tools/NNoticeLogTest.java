package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NNoticeLogTest {

    @Test
    void seqStartsAtZeroAndGrowsMonotonically() {
        NNoticeLog log = new NNoticeLog();
        assertEquals(0, log.seq());

        log.add("one");
        assertEquals(1, log.seq());
        log.add("two");
        assertEquals(2, log.seq());
        log.add(null);
        assertEquals(2, log.seq());
        log.add("");
        assertEquals(2, log.seq());
    }

    @Test
    void capacityEvictsOldestWhileSeqKeepsIncreasing() {
        NNoticeLog log = new NNoticeLog();
        for (int i = 1; i <= 64; i++) {
            log.add("id=" + String.format("%02d", i) + ";");
        }
        assertEquals(64, log.seq());
        assertTrue(log.contains(0, "id=01;"));
        assertTrue(log.contains(0, "id=64;"));

        log.add("id=65;");
        assertEquals(65, log.seq());
        assertFalse(log.contains(0, "id=01;"));
        assertTrue(log.contains(0, "id=02;"));
        assertTrue(log.contains(0, "id=65;"));
    }

    @Test
    void matchingIsCaseInsensitive() {
        NNoticeLog log = new NNoticeLog();
        log.add("You opened a Natural Cave Gallery.");

        assertTrue(log.contains(0, "cave gallery"));
        assertTrue(log.contains(0, "CAVE GALLERY"));
        assertTrue(log.contains(0, "Cave Gallery"));
    }

    @Test
    void nullAddIsIgnoredAndContainsDoesNotThrowOnNulls() {
        NNoticeLog log = new NNoticeLog();
        log.add(null);
        assertEquals(0, log.seq());
        assertFalse(log.contains(0, "anything"));

        log.add("opened a cave gallery");
        assertFalse(log.contains(0, (String[]) null));
        assertFalse(log.contains(0));
        assertFalse(log.contains(0, (String) null));
        assertTrue(log.contains(0, "cave", null, "gallery"));
        assertFalse(log.contains(0, null, ""));
    }

    @Test
    void containsExcludesMessagesAtOrBeforeTheMark() {
        NNoticeLog log = new NNoticeLog();
        log.add("old notice");
        long mark = log.seq();

        assertFalse(log.contains(mark, "old notice"));
        assertFalse(log.contains(mark, "old"));

        log.add("new notice");
        assertTrue(log.contains(mark, "new notice"));
        assertFalse(log.contains(mark, "old notice"));
        assertTrue(log.contains(mark - 1, "old notice"));
    }

    @Test
    void containsMatchesAnySubstringInNewMessages() {
        NNoticeLog log = new NNoticeLog();
        log.add("opened a natural cave gallery");
        long afterFirst = log.seq();

        assertTrue(log.contains(0, "cave", "gallery"));
        assertTrue(log.contains(0, "cave", "dragon"));
        assertFalse(log.contains(0, "mine", "dragon"));

        log.add("cave");
        log.add("gallery");
        assertTrue(log.contains(afterFirst, "cave", "gallery"));
        assertTrue(log.contains(afterFirst, "cave"));
        assertTrue(log.contains(afterFirst, "gallery"));
    }
}
