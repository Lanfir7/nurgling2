package nurgling.actions.bots;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProspectMineTest {
    @Test
    void snapshotRcKeepsStartWhenLivePositionChanges() {
        Coord2d live = Coord2d.of(110, 220);
        Coord2d snap = ProspectMine.snapshotRc(live);
        assertNotSame(live, snap);

        live.x = 999;
        live.y = 888;

        assertEquals(110, snap.x);
        assertEquals(220, snap.y);
    }

    @Test
    void snapshotRcNullSafe() {
        assertNull(ProspectMine.snapshotRc(null));
    }
}
