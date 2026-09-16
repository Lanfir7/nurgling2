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
    void defaultUrlIsLanfirMasterReleaseVer() {
        assertEquals(NUpdateFeed.SOURCE_RELEASE_DIR + "ver", NUpdateFeed.DEFAULT_BASEURL);
        assertEquals("https://raw.githubusercontent.com/Lanfir7/nurgling2/master/release/ver", NUpdateFeed.DEFAULT_BASEURL);
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
    void keepsAlreadyMasterReleaseUrl() {
        assertEquals(NUpdateFeed.DEFAULT_BASEURL, NUpdateFeed.migrateBaseUrl(NUpdateFeed.DEFAULT_BASEURL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling2/master/release/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling-release/latest/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/ver",
            " https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release/ver ",
            "http://raw.githubusercontent.com/Lanfir7/nurgling2/next/ver",
            "   "
    })
    void migratesKnownLegacyFeedsToMaster(String current) {
        assertEquals(NUpdateFeed.DEFAULT_BASEURL, NUpdateFeed.migrateBaseUrl(current));
        assertEquals(NUpdateFeed.DEFAULT_BASEURL,
                NUpdateFeed.migrateBaseUrl(NUpdateFeed.migrateBaseUrl(current)));
    }

    @Test
    void needsUpdateOnlyWhenVersionsDiffer() {
        assertFalse(NUpdateFeed.needsUpdate("2.103.145", "2.103.145"));
        assertTrue(NUpdateFeed.needsUpdate("2.103.145", "2.100.21"));
        assertFalse(NUpdateFeed.needsUpdate(null, "2.103.145"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/my-ver",
            "https://example.com/Lanfir7/nurgling2/next/release/ver",
            "https://example.com/katodiy/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling2/experiment/release/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release/ver?custom=true",
            "https://raw.githubusercontent.com.evil.test/Lanfir7/nurgling2/next/release/ver",
            "https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release/other",
            "not a valid URI"
    })
    void keepsUnrelatedCustomUrl(String custom) {
        assertEquals(custom, NUpdateFeed.migrateBaseUrl(custom));
    }
}
