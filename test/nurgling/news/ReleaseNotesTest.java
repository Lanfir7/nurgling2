package nurgling.news;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseNotesTest {
    @AfterEach
    void restorePreferences() {
        ReleaseNotes.setPreferenceStoreForTests(null);
    }

    @Test
    void parsesContractAndUsesLanguageFallback() {
        List<ReleaseNotes.Entry> entries = ReleaseNotes.parse("{\"schema\":1,\"releases\":[{"
                + "\"id\":\"2.1.0\",\"date\":\"2026-09-13\","
                + "\"title\":{\"ru\":\"Заголовок\",\"en\":\"Title\"},"
                + "\"summary\":{\"ru\":[\"Один\"],\"en\":[\"One\",\"Two\"]},"
                + "\"details\":{\"ru\":[],\"en\":[\"More\"]}}]}");

        assertEquals(1, entries.size());
        ReleaseNotes.Entry entry = entries.get(0);
        assertEquals("Заголовок", entry.displayTitle("ru"));
        assertEquals("Title", entry.displayTitle("de"));
        assertEquals(Arrays.asList("Один"), entry.summary("ru"));
        assertEquals(Arrays.asList("More"), entry.details("ru"));
    }

    @Test
    void limitsSummaryToThreeBullets() {
        List<ReleaseNotes.Entry> entries = ReleaseNotes.parse("{\"schema\":1,\"releases\":[{"
                + "\"id\":\"2\",\"date\":\"2026-09-13\",\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]},\"details\":{}}]}");

        assertEquals(3, entries.get(0).summary("en").size());
    }

    @Test
    void rejectsBadDocumentsAndSkipsBadEntries() {
        assertTrue(ReleaseNotes.parse(null).isEmpty());
        assertTrue(ReleaseNotes.parse("{\"schema\":2,\"releases\":[]}").isEmpty());
        assertTrue(ReleaseNotes.parse("not json").isEmpty());
        assertFalse(ReleaseNotes.parse("{\"schema\":1,\"releases\":[{}, {\"id\":\"1\",\"date\":\"2026-09-13\",\"title\":{\"en\":\"OK\"}}]}").isEmpty());
    }

    @Test
    void unreadStatePersistsAcrossTheInMemoryCacheRestart() {
        MemoryPreferences preferences = new MemoryPreferences();
        ReleaseNotes.setPreferenceStoreForTests(preferences);

        assertTrue(ReleaseNotes.isUnread("2.1.0"));
        ReleaseNotes.markSeen("2.1.0");
        assertFalse(ReleaseNotes.isUnread("2.1.0"));

        ReleaseNotes.setPreferenceStoreForTests(preferences);
        assertFalse(ReleaseNotes.isUnread("2.1.0"));
        assertTrue(ReleaseNotes.isUnread("2.2.0"));
        assertFalse(ReleaseNotes.isUnread(null));
        assertFalse(ReleaseNotes.isUnread(""));
    }

    private static final class MemoryPreferences implements ReleaseNotes.PreferenceStore {
        private String value;

        @Override
        public String get(String key, String fallback) {
            return value == null ? fallback : value;
        }

        @Override
        public void put(String key, String value) {
            this.value = value;
        }
    }
}
