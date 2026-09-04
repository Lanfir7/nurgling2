package monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NGlobalSearchItemsTest {
    @Test
    void clearingStorageSearchCacheAdvancesStorageRevision() {
        long before = NGlobalSearchItems.storageRevision();

        NGlobalSearchItems.clearQueryCache();

        assertTrue(NGlobalSearchItems.storageRevision() > before);
    }
}
