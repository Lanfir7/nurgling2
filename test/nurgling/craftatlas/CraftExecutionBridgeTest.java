package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CraftExecutionBridgeTest {
    @Test
    void openReResolvesAndGuardsPendingUntilCompletion() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger resolves = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        CraftExecutionBridge bridge = new CraftExecutionBridge(resource -> {
            resolves.incrementAndGet();
            return "open".equals(resource) ? calls::incrementAndGet : null;
        }, now::get);
        assertTrue(bridge.open("open", CraftAtlasEntry.Availability.OPEN));
        assertFalse(bridge.open("open", CraftAtlasEntry.Availability.OPEN));
        assertEquals(1, calls.get());
        bridge.completed();
        assertTrue(bridge.open("open", CraftAtlasEntry.Availability.OPEN));
        bridge.completed();
        assertFalse(bridge.open("missing", CraftAtlasEntry.Availability.OPEN));
        assertFalse(bridge.open("open", CraftAtlasEntry.Availability.REFERENCE_ONLY));
        assertTrue(resolves.get() >= 3);
    }

    @Test
    void timeoutReleasesRejectedAction() {
        AtomicLong now = new AtomicLong();
        CraftExecutionBridge bridge = new CraftExecutionBridge(resource -> () -> { }, now::get);
        assertTrue(bridge.open("open", CraftAtlasEntry.Availability.OPEN));
        now.set(CraftExecutionBridge.TIMEOUT_NANOS + 1);
        assertTrue(bridge.open("open", CraftAtlasEntry.Availability.OPEN));
    }
}
