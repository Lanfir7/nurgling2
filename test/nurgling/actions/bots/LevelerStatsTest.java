package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LevelerStatsTest {
    @Test
    void remainingWorkPrefersFillOverDig() {
        assertEquals(26609, LevelerStats.remainingWork(26609, 10));
        assertEquals(1200, LevelerStats.remainingWork(0, 1200));
        assertEquals(0, LevelerStats.remainingWork(0, 0));
    }

    @Test
    void countsOnlyDecreasesOnSameFlag() {
        LevelerStats stats = new LevelerStats(0);
        stats.noteRemaining(100);
        stats.noteRemaining(80);
        stats.noteRemaining(500);
        stats.noteRemaining(450);
        assertEquals(70, stats.processed());
        assertEquals(450, stats.lastRemaining());
    }

    @Test
    void speedAndEtaFromAverageSinceStart() {
        assertEquals(120.0, LevelerStats.unitsPerMinute(60, 30_000), 0.001);
        assertEquals(30_000L, LevelerStats.etaMs(60, 120.0));
        assertNull(LevelerStats.etaMs(60, 0));
        assertEquals(0L, LevelerStats.etaMs(0, 120.0));
    }

    @Test
    void formatsRateAndDuration() {
        assertEquals("-", LevelerStats.formatRate(0));
        assertEquals("120", LevelerStats.formatRate(120.4));
        assertEquals("1.5", LevelerStats.formatRate(1.5));
        assertEquals("-", LevelerStats.formatDuration(null));
        assertEquals("2h 3m", LevelerStats.formatDuration(7380_000L));
        assertEquals("1m 5s", LevelerStats.formatDuration(65_000L));
        assertEquals("9s", LevelerStats.formatDuration(9_000L));
    }
}
