package nurgling.db;

import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeLocationResolver;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTrackingPolicyTest {
    @Test
    void indoorHomeAllowsStorageTracking() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .markManual(-77L, new LinkedHashSet<>(Collections.singletonList(101L)), "Tent");
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                Collections.emptyList(), Collections.emptyList(), null, false,
                registry, 101L, -77L, true);

        assertTrue(StorageTrackingPolicy.shouldTrack(status));
    }

    @Test
    void nonHomeLocationRejectsStorageTracking() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                Collections.emptyList(), Collections.emptyList(), null, false,
                HomeInteriorRegistry.empty(), 101L, -77L, true);

        assertFalse(StorageTrackingPolicy.shouldTrack(status));
    }

    @Test
    void storageSessionMustStartAndFinishAtHome() {
        assertTrue(StorageTrackingPolicy.canPersistSession(true, true));
        assertFalse(StorageTrackingPolicy.canPersistSession(false, true));
        assertFalse(StorageTrackingPolicy.canPersistSession(true, false));
    }
}
