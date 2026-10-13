package nurgling.db.service;

import nurgling.db.dao.LocalTimerDao;
import nurgling.timers.Timer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimerSyncLegacyBridgeTest {
    @Test void gridAnchoredOldSharedRowKeepsIdentityAndClockOffset() {
        String fullDescription = "D".repeat(400);
        LocalTimerDao.LocalTimerData row = new LocalTimerDao.LocalTimerData(
            1, "world", "res_g_77_3_4_clay", 91, 103, 204,
            "Clay Pit", "gfx/terobjs/mm/claypit", 12_000, 60_000,
            fullDescription, 77L, 3, 4, null, null);

        Timer timer = TimerSyncService.fromLegacyRow("world", row, 2_000);
        assertEquals(Timer.resourceId("world", 77, 3, 4, row.getResourceType()), timer.id);
        assertEquals(10_000, timer.startedAt);
        assertEquals(77, timer.gridId);
        assertEquals(3, timer.ox);
        assertEquals(4, timer.oy);
        assertEquals("Clay Pit", timer.resourceName);
        assertEquals(fullDescription, timer.description);
        assertEquals(400, timer.name.length());
        assertTrue(timer.shared);
        assertNotNull(timer.legacyTc);
    }

    @Test void unanchoredOldSharedRowRemainsResolvableAfterImport() {
        LocalTimerDao.LocalTimerData row = new LocalTimerDao.LocalTimerData(
            2, "world", "res_91_103_204_clay", 91, 103, 204,
            "Clay Pit", "clay", 12_000, 60_000,
            "", null, null, null, null, null);

        Timer timer = TimerSyncService.fromLegacyRow("world", row, 0);
        assertEquals(row.getResourceId(), timer.id);
        assertFalse(timer.hasLocation());
        assertEquals(91, timer.legacySeg);
        assertEquals(103, timer.legacyTc.x);
        assertEquals(204, timer.legacyTc.y);
    }
}
