package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WormFarmStatsTest {
    @Test
    void countsWormIncreasesAndIgnoresDump() {
        WormFarmStats stats = new WormFarmStats(0);
        stats.noteWorms(0);
        stats.noteWorms(3);
        stats.noteWorms(5);
        stats.noteWorms(0);
        stats.noteWorms(2);
        assertEquals(7, stats.harvested());
    }

    @Test
    void staminaCountsOnlyDropsNotDrinks() {
        WormFarmStats stats = new WormFarmStats(0);
        stats.noteStamina(0.90);
        stats.noteStamina(0.40);
        stats.noteStamina(0.95);
        stats.noteStamina(0.70);
        assertEquals(0.75, stats.staminaBarPerMinute(60_000), 0.001);
    }

    @Test
    void ratesAndFormats() {
        WormFarmStats stats = new WormFarmStats(0);
        stats.noteWorms(6);
        assertEquals(12.0, stats.wormsPerMinute(30_000), 0.001);
        assertEquals("12.0", LevelerStats.formatRate(stats.wormsPerMinute(30_000)));
        assertEquals("1m 5s", LevelerStats.formatDuration(stats.elapsedMs(65_000)));
        assertEquals("-", WormFarmStats.formatStaminaRate(0));
        assertEquals("18%", WormFarmStats.formatStaminaRate(0.18));
        assertEquals("1.5%", WormFarmStats.formatStaminaRate(0.015));
    }
}
