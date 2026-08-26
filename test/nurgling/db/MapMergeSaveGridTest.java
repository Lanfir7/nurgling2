package nurgling.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMergeSaveGridTest {

    @Test
    void newerIncomingReplacesLocal() {
        assertTrue(MapMerge.saveIncomingGrid(10L, 20L));
        assertTrue(MapMerge.acceptIncomingGrid(10L, 20L, false));
    }

    @Test
    void olderAnchorIsAcceptedButNotSaved() {
        assertTrue(MapMerge.acceptIncomingGrid(20L, 10L, true));
        assertFalse(MapMerge.saveIncomingGrid(20L, 10L));
    }

    @Test
    void olderNonAnchorIsRejected() {
        assertFalse(MapMerge.acceptIncomingGrid(20L, 10L, false));
        assertFalse(MapMerge.saveIncomingGrid(20L, 10L));
    }

    @Test
    void missingLocalIsSaved() {
        assertTrue(MapMerge.saveIncomingGrid(null, 1L));
        assertTrue(MapMerge.acceptIncomingGrid(null, 1L, false));
    }
}
