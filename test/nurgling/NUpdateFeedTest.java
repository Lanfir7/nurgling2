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
    void defaultUrlIsLanfirReleaseStableVer() {
        assertEquals(
                "https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/ver",
                NUpdateFeed.DEFAULT_BASEURL);
        assertTrue(NUpdateFeed.DEFAULT_BASEURL.contains("Lanfir7"));
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
    void keepsAlreadyLanfirUrl() {
        String own = "https://raw.githubusercontent.com/Lanfir7/nurgling-release/latest/ver";
        assertEquals(own, NUpdateFeed.migrateBaseUrl(own));
    }

    @Test
    void keepsUnrelatedCustomUrl() {
        String custom = "https://example.com/my-ver";
        assertEquals(custom, NUpdateFeed.migrateBaseUrl(custom));
    }
}
