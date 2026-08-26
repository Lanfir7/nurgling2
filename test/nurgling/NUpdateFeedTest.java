package nurgling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NUpdateFeedTest {
    @Test
    void defaultUrlIsLanfirNextReleaseVer() {
        assertEquals(NUpdateFeed.SOURCE_RELEASE_DIR + "ver", NUpdateFeed.DEFAULT_BASEURL);
        assertTrue(NUpdateFeed.DEFAULT_BASEURL.contains("/next/release/"));
        assertFalse(NUpdateFeed.DEFAULT_BASEURL.toLowerCase().contains("katodiy"));
        assertFalse(NUpdateFeed.DEFAULT_BASEURL.toLowerCase().contains("aleksandrsvoboda"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://raw.githubusercontent.com/Katodiy/nurgling2/master/ver",
            "https://raw.githubusercontent.com/aleksandrsvoboda/nurgling-release/stable/ver",
            "https://raw.githubusercontent.com/aleksandrsvoboda/nurgling-release/latest/ver"
    })
    void migratesForeignAndEmptyUrlsToLanfir(String current) {
        assertEquals(NUpdateFeed.DEFAULT_BASEURL, NUpdateFeed.migrateBaseUrl(current));
    }

    @Test
    void keepsAlreadyNextReleaseUrl() {
        assertEquals(NUpdateFeed.DEFAULT_BASEURL, NUpdateFeed.migrateBaseUrl(NUpdateFeed.DEFAULT_BASEURL));
    }

    @Test
    void migratesMasterAndReleaseRepoToNext() {
        assertEquals(NUpdateFeed.DEFAULT_BASEURL,
                NUpdateFeed.migrateBaseUrl("https://raw.githubusercontent.com/Lanfir7/nurgling2/master/release/ver"));
        assertEquals(NUpdateFeed.DEFAULT_BASEURL,
                NUpdateFeed.migrateBaseUrl("https://raw.githubusercontent.com/Lanfir7/nurgling-release/latest/ver"));
    }

    @Test
    void needsUpdateOnlyWhenVersionsDiffer() {
        assertFalse(NUpdateFeed.needsUpdate("2.103.145", "2.103.145"));
        assertTrue(NUpdateFeed.needsUpdate("2.103.145", "2.100.21"));
        assertFalse(NUpdateFeed.needsUpdate(null, "2.103.145"));
    }

    @Test
    void keepsUnrelatedCustomUrl() {
        String custom = "https://example.com/my-ver";
        assertEquals(custom, NUpdateFeed.migrateBaseUrl(custom));
    }
}
