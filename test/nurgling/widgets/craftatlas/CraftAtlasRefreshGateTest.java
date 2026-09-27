package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasRefreshGateTest {
    @Test
    void hiddenRevisionChurnProducesOneRefreshOnShowAndNoneOnUnchangedReopen() {
        CraftAtlasRefreshGate gate = new CraftAtlasRefreshGate();

        assertFalse(gate.shouldRefresh(false, 1, 1, 0));
        assertFalse(gate.shouldRefresh(false, 2, 3, 10));
        assertFalse(gate.shouldRefresh(false, 4, 5, 20));
        assertTrue(gate.shouldRefresh(true, 4, 5, 30));
        gate.completed(4, 5, false, 30);
        assertFalse(gate.shouldRefresh(true, 4, 5, 40));
        assertFalse(gate.shouldRefresh(false, 4, 5, 50));
        assertFalse(gate.shouldRefresh(true, 4, 5, 60));
        assertTrue(gate.shouldRefresh(true, 5, 5, 70));
    }

    @Test
    void incompleteCatalogRetriesOnlyWhileVisibleAndAfterCooldown() {
        CraftAtlasRefreshGate gate = new CraftAtlasRefreshGate();
        gate.completed(7, 11, true, 100);

        assertFalse(gate.shouldRefresh(true, 7, 11, 1_000_000_099L));
        assertFalse(gate.shouldRefresh(false, 7, 11, 1_000_000_100L));
        assertTrue(gate.shouldRefresh(true, 7, 11, 1_000_000_100L));
        gate.completed(7, 11, false, 1_000_000_100L);
        assertFalse(gate.shouldRefresh(true, 7, 11, 3_000_000_000L));
    }

    @Test
    void replacingMenuForcesRefreshEvenWhenRevisionMatches() {
        CraftAtlasRefreshGate gate = new CraftAtlasRefreshGate();
        gate.completed(2, 3, false, 0);
        gate.invalidate();
        assertFalse(gate.shouldRefresh(false, 2, 3, 1));
        assertTrue(gate.shouldRefresh(true, 2, 3, 2));
    }
}
