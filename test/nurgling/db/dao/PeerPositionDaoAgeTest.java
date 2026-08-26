package nurgling.db.dao;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Age for map markers must be computed on the database clock. JDBC Timestamp#getTime on a
 * TIMESTAMP column vs CURRENT_TIMESTAMP (timestamptz) uses the JVM zone and, in Europe/Kiev,
 * reports a live row as ~3 hours old - past {@code PeerPosition.DROP_MS}, so nobody is drawn.
 */
class PeerPositionDaoAgeTest {

    @Test
    void postgresLoadComputesAgeInSql() {
        String sql = PeerPositionDao.loadByProfileSql(true);
        assertTrue(sql.contains("EXTRACT(EPOCH"), sql);
        assertFalse(sql.contains("AS db_now"), sql);
    }

    @Test
    void sqliteLoadComputesAgeInSql() {
        String sql = PeerPositionDao.loadByProfileSql(false);
        assertTrue(sql.contains("julianday"), sql);
        assertFalse(sql.contains("AS db_now"), sql);
    }

    @Test
    void jdbcMixingTimestampAndTimestamptzLooksHoursOldInKiev() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kiev"));
        try {
            Instant utcNoon = Instant.parse("2026-08-26T12:00:00Z");
            Timestamp timestamptzNow = Timestamp.from(utcNoon);
            Timestamp naiveUtcWall = Timestamp.valueOf("2026-08-26 12:00:00");
            long apparentAge = timestamptzNow.getTime() - naiveUtcWall.getTime();
            assertTrue(apparentAge >= 2 * 3600_000L,
                    "expected multi-hour JDBC skew, got " + apparentAge);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
