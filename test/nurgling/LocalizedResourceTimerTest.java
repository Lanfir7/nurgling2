package nurgling;

import haven.Coord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedResourceTimerTest {
    private static final long READY_MS = 15 * 60 * 1000L;
    private static final long AUTO_REMOVE_MS = 30 * 60 * 1000L;

    @Test
    void ordinaryTimerNeverAutoRemovesEvenWhenReady() {
        long start = System.currentTimeMillis() - READY_MS - 1000;
        LocalizedResourceTimer timer = new LocalizedResourceTimer(
                "res_1_0_0_tarpit", 1L, new Coord(0, 0), "Tar Pit",
                "gfx/terobjs/map/tarpit", start, READY_MS, "Tar Pit");

        assertTrue(timer.isExpired());
        assertFalse(timer.shouldAutoRemove());
        assertFalse(timer.shouldPersist());
        assertNull(timer.getIconRes());
    }

    @Test
    void boughPyreStillCountingMustPersistAcrossReload() {
        long start = System.currentTimeMillis() - 5 * 60 * 1000L;
        LocalizedResourceTimer timer = pyre(start);

        assertFalse(timer.isExpired());
        assertTrue(timer.shouldPersist());

        LocalizedResourceTimer loaded = new LocalizedResourceTimer(new JSONObject(timer.toJson().toString(2)));
        assertFalse(loaded.isExpired());
        assertTrue(loaded.shouldPersist());
        assertEquals(AUTO_REMOVE_MS, loaded.getAutoRemoveAfterMs());
        assertEquals(LocalizedResourceTimer.BOUGH_PYRE_ICON, loaded.getIconRes());
    }

    @Test
    void boughPyreIsReadyButKeptUntilThirtyMinutes() {
        long start = System.currentTimeMillis() - READY_MS - 1000;
        LocalizedResourceTimer timer = pyre(start);

        assertTrue(timer.isExpired());
        assertFalse(timer.shouldAutoRemove());
        assertTrue(timer.shouldPersist());
        assertEquals("Ready", timer.getFormattedRemainingTime());
        assertEquals("nurgling/bots/icons/boughpyre/u", timer.getIconRes());
    }

    @Test
    void boughPyreAutoRemovesAfterThirtyMinutes() {
        long start = System.currentTimeMillis() - AUTO_REMOVE_MS - 1000;
        LocalizedResourceTimer timer = pyre(start);

        assertTrue(timer.isExpired());
        assertTrue(timer.shouldAutoRemove());
        assertFalse(timer.shouldPersist());
    }

    @Test
    void boughPyreJsonRoundtripKeepsEphemeralFields() {
        long start = 1_700_000_000_000L;
        LocalizedResourceTimer original = pyre(start);
        LocalizedResourceTimer loaded = new LocalizedResourceTimer(original.toJson());

        assertEquals(original.getResourceId(), loaded.getResourceId());
        assertEquals(AUTO_REMOVE_MS, loaded.getAutoRemoveAfterMs());
        assertEquals("nurgling/bots/icons/boughpyre/u", loaded.getIconRes());
        assertEquals(LocalizedResourceTimer.BOUGH_PYRE_TYPE, loaded.getResourceType());
    }

    @Test
    void jsonWithoutNewFieldsStaysPermanent() {
        JSONObject json = new JSONObject();
        json.put("resourceId", "res_1_2_3_tarpit");
        json.put("segmentId", 1L);
        json.put("tileX", 2);
        json.put("tileY", 3);
        json.put("resourceName", "Tar Pit");
        json.put("resourceType", "gfx/terobjs/map/tarpit");
        json.put("startTime", 1L);
        json.put("duration", READY_MS);
        json.put("description", "Tar Pit");

        LocalizedResourceTimer loaded = new LocalizedResourceTimer(json);
        assertEquals(0L, loaded.getAutoRemoveAfterMs());
        assertNull(loaded.getIconRes());
        assertFalse(loaded.shouldAutoRemove());
    }

    @Test
    void relocatesWithSegmentMergeLikeMapFileMarkers() {
        long start = System.currentTimeMillis();
        LocalizedResourceTimer timer = pyre(start);
        haven.Coord soff = new haven.Coord(1, 0);
        LocalizedResourceTimer moved = timer.relocated(99L, soff.mul(haven.MCache.cmaps));

        assertEquals(99L, moved.getSegmentId());
        assertEquals(new Coord(10 - haven.MCache.cmaps.x, 20), moved.getTileCoords());
        assertEquals(timer.getStartTime(), moved.getStartTime());
        assertEquals(timer.getDuration(), moved.getDuration());
        assertEquals(timer.getIconRes(), moved.getIconRes());
        assertTrue(moved.getResourceId().contains("99"));
    }

    private static LocalizedResourceTimer pyre(long start) {
        return new LocalizedResourceTimer(
                "res_1_10_20_nurgling_boughpyre", 1L, new Coord(10, 20), "Bough Pyre",
                LocalizedResourceTimer.BOUGH_PYRE_TYPE, start, READY_MS, "Bough Pyre",
                AUTO_REMOVE_MS, LocalizedResourceTimer.BOUGH_PYRE_ICON);
    }
}
