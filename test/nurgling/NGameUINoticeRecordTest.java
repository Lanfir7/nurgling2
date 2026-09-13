package nurgling;

import nurgling.tools.NNoticeLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NGameUINoticeRecordTest {

    @Test
    void recordAddsNoticeTextForLaterContains() {
        NNoticeLog log = new NNoticeLog();
        long mark = log.seq();
        NGameUINotices.record(log, "You opened a Natural Cave Gallery.");
        assertTrue(log.contains(mark, "cave gallery"));
        assertEquals(1, log.seq());
    }

    @Test
    void recordIgnoresNullLogNullTextAndEmpty() {
        NNoticeLog log = new NNoticeLog();
        NGameUINotices.record(null, "You opened a Natural Cave Gallery.");
        NGameUINotices.record(log, null);
        NGameUINotices.record(log, "");
        assertEquals(0, log.seq());
        assertFalse(log.contains(0, "cave gallery"));
    }
}
