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
    void keepsEverySummaryBulletAndFullDetails() {
        List<ReleaseNotes.Entry> entries = ReleaseNotes.parse("{\"schema\":1,\"releases\":[{"
                + "\"id\":\"2\",\"date\":\"2026-09-13\",\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]},"
                + "\"details\":{\"en\":[\"detail 1\",\"detail 2\",\"detail 3\",\"detail 4\"]}}]}");

        ReleaseNotes.Entry entry = entries.get(0);
        assertEquals(Arrays.asList("1", "2", "3", "4", "5", "6"), entry.summary("en"));
        assertEquals(Arrays.asList("detail 1", "detail 2", "detail 3", "detail 4"), entry.details("en"));
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

    @Test
    void matchesDetailsIgnoresCaseAndFindsSubstring() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"2.1.0\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"Summary\"]},"
                + "\"details\":{\"en\":[\"Cart carrier waits at the dock\"]}}");
        assertTrue(entry.matchesDetails("CART", "en"));
        assertTrue(entry.matchesDetails("carrier waits", "en"));
        assertFalse(entry.matchesDetails("missing", "en"));
    }

    @Test
    void blankDetailsQueryMatchesEveryEntry() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"2.1.0\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"Summary\"]},"
                + "\"details\":{\"en\":[\"Cart\"]}}");
        assertTrue(entry.matchesDetails("", "en"));
        assertTrue(entry.matchesDetails("   ", "en"));
        assertTrue(entry.matchesDetails(null, "en"));
    }

    @Test
    void matchesDetailsIgnoresSummaryTitleIdAndDate() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"needle-id\",\"date\":\"needle-date\","
                + "\"title\":{\"en\":\"needle-title\"},"
                + "\"summary\":{\"en\":[\"needle-summary\"]},"
                + "\"details\":{\"en\":[\"unrelated\"]}}");
        assertFalse(entry.matchesDetails("needle", "en"));
    }

    @Test
    void matchesDetailsUsesLanguageFallback() {
        ReleaseNotes.Entry both = parseOne("{"
                + "\"id\":\"1\",\"date\":\"2026-09-13\","
                + "\"title\":{\"ru\":\"Заголовок\",\"en\":\"Title\"},"
                + "\"summary\":{},"
                + "\"details\":{\"ru\":[\"телега\"],\"en\":[\"cart\"]}}");
        assertTrue(both.matchesDetails("телега", "ru"));
        assertFalse(both.matchesDetails("cart", "ru"));
        assertTrue(both.matchesDetails("cart", "en"));

        ReleaseNotes.Entry enOnly = parseOne("{"
                + "\"id\":\"2\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{},"
                + "\"details\":{\"ru\":[],\"en\":[\"cart\"]}}");
        assertTrue(enOnly.matchesDetails("cart", "ru"));
    }

    private static ReleaseNotes.Entry parseOne(String jsonObject) {
        List<ReleaseNotes.Entry> entries = ReleaseNotes.parse("{\"schema\":1,\"releases\":[" + jsonObject + "]}");
        assertEquals(1, entries.size());
        return entries.get(0);
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
