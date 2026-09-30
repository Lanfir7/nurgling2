package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VeinWorklistScanTest {
    private static final String ORE = "gfx/tiles/rocks/cassiterite";

    @Test
    void longMiningRunOnlyLooksUpTheUnminedBoundaryOncePerTile() {
        VeinWorklist list = new VeinWorklist(ORE, Coord.z);
        Set<Coord> mined = new HashSet<>();
        int side = 32;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                Coord tile = Coord.of(x, y);
                mined.add(tile);
                list.markMined(tile);
            }
        }
        Set<Coord> lookedUp = new HashSet<>();
        AtomicInteger safetyChecks = new AtomicInteger();
        list.scanVisible(tile -> {
            assertFalse(mined.contains(tile), "Already mined tiles need no map lookup");
            assertTrue(lookedUp.add(tile), "A shared neighbour needs only one lookup");
            return ORE;
        }, tile -> {
            safetyChecks.incrementAndGet();
            return true;
        });
        assertEquals((side + 2) * (side + 2) - side * side, lookedUp.size());
        assertEquals(lookedUp.size(), safetyChecks.get());

        list.scanVisible(tile -> {
            fail("Queued tiles need no repeated map lookup");
            return null;
        }, tile -> {
            fail("Queued tiles need no repeated support check");
            return false;
        });
    }

    @Test
    void otherRockTypesDoNotNeedSupportChecks() {
        VeinWorklist list = new VeinWorklist(ORE, Coord.z);
        list.scanVisible(tile -> "gfx/tiles/rocks/gneiss", tile -> {
            fail("Only matching rock needs a support check");
            return false;
        });
        assertTrue(list.isEmpty());
    }

    @Test
    void hiddenAndUnsafeBoundaryTilesAreRetriedWhenTheyBecomeAvailable() {
        VeinWorklist list = new VeinWorklist(ORE, Coord.z);
        Coord target = Coord.of(1, 0);
        list.scanVisible(tile -> null, tile -> true);
        assertTrue(list.isEmpty());
        list.scanVisible(tile -> tile.equals(target) ? ORE : null, tile -> false);
        assertTrue(list.isEmpty());
        list.scanVisible(tile -> tile.equals(target) ? ORE : null, tile -> true);
        assertEquals(target, list.takeNearest(Coord.z));
        // A selected target can become available again after a failed attempt.
        list.scanVisible(tile -> tile.equals(target) ? ORE : null, tile -> true);
        assertEquals(target, list.takeNearest(Coord.z));
        list.markMined(target);
        list.scanVisible(tile -> tile.equals(target) ? ORE : null, tile -> true);
        assertTrue(list.isEmpty());
    }
}
