package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasStoredItemsStateTest {
    @Test
    void staleCompletionCannotReplaceTheLatestStorageSnapshot() {
        CraftAtlasStoredItemsState state = new CraftAtlasStoredItemsState();
        long oldRequest = state.begin(10);
        state.cancel();
        long latestRequest = state.begin(11);

        state.complete(latestRequest, Set.of("new item"), null);
        state.complete(oldRequest, Set.of("stale item"), null);
        CraftAtlasStoredItemsState.Result result = state.take();

        assertNotNull(result);
        assertEquals(Set.of("new item"), result.names);
        assertEquals(11, result.revision);
        assertFalse(state.loading());
    }

    @Test
    void cancelingAnInFlightCheckLeavesTheFilterIdle() {
        CraftAtlasStoredItemsState state = new CraftAtlasStoredItemsState();
        state.begin(1);

        state.cancel();

        assertFalse(state.loading());
        assertNull(state.take());
    }
}
