package nurgling.db;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageOrphanPolicyTest {
    @Test
    void parsesCoordToStringFormat() {
        assertEquals(Coord.of(123, 456), StorageOrphanPolicy.parseGcoord("(123, 456)"));
        assertEquals(Coord.of(-1, 2), StorageOrphanPolicy.parseGcoord("(-1, 2)"));
        assertNull(StorageOrphanPolicy.parseGcoord(null));
        assertNull(StorageOrphanPolicy.parseGcoord("123,456"));
    }

    @Test
    void nearbyUsesThirtyTilesInGcoordSpace() {
        Coord player = Coord.of(0, 0);
        int near = StorageOrphanPolicy.NEAR_TILES * StorageOrphanPolicy.POSRES_PER_TILE;
        assertEquals(30, StorageOrphanPolicy.NEAR_TILES);
        assertTrue(StorageOrphanPolicy.isNearby(player, Coord.of(near, 0)));
        assertTrue(StorageOrphanPolicy.isNearby(player, Coord.of(0, near)));
        assertFalse(StorageOrphanPolicy.isNearby(player, Coord.of(near + 1, 0)));
        assertFalse(StorageOrphanPolicy.isNearby(player, null));
    }

    @Test
    void idleRequiresThreeSecondsAfterEnterAndGobActivity() {
        long entered = 10_000;
        assertFalse(StorageOrphanPolicy.isGridIdle(entered + 2999, entered, 0));
        assertTrue(StorageOrphanPolicy.isGridIdle(entered + 3000, entered, 0));
        assertFalse(StorageOrphanPolicy.isGridIdle(entered + 4000, entered, entered + 2000));
        assertTrue(StorageOrphanPolicy.isGridIdle(entered + 5000, entered, entered + 2000));
        assertFalse(StorageOrphanPolicy.isGridIdle(20_000, 0, 1));
    }

    @Test
    void purgeOnlyWhenLoadedIdleNearbyAndReallyEmpty() {
        assertTrue(StorageOrphanPolicy.shouldPurge(true, true, true, false, false));
        assertFalse(StorageOrphanPolicy.shouldPurge(false, true, true, false, false));
        assertFalse(StorageOrphanPolicy.shouldPurge(true, false, true, false, false));
        assertFalse(StorageOrphanPolicy.shouldPurge(true, true, false, false, false));
        assertFalse(StorageOrphanPolicy.shouldPurge(true, true, true, true, false));
        assertFalse(StorageOrphanPolicy.shouldPurge(true, true, true, false, true));
    }

    @Test
    void sameTileIgnoresPosresJitterInsideOneTile() {
        Coord a = Coord.of(1024, 2048);
        assertTrue(StorageOrphanPolicy.sameTile(a, Coord.of(1025, 2048)));
        assertTrue(StorageOrphanPolicy.sameTile(a, Coord.of(2047, 3071)));
        assertFalse(StorageOrphanPolicy.sameTile(a, Coord.of(2048, 2048)));
        assertFalse(StorageOrphanPolicy.sameTile(a, null));
    }

    @Test
    void gobPresentIfHashMatchesOrStockpileOccupiesTile() {
        assertTrue(StorageOrphanPolicy.gobPresent(true, false));
        assertTrue(StorageOrphanPolicy.gobPresent(false, true));
        assertFalse(StorageOrphanPolicy.gobPresent(false, false));
    }
}
