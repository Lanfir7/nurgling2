package nurgling.db.dao;

import nurgling.db.DatabaseAdapter;
import nurgling.db.PostgresAdapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for live player positions.
 *
 * <p>The set of characters here is everyone publishing to this database, which is deliberately not
 * the same as anyone's in-game Kin list: holding the database credentials is what grants membership.
 *
 * <p>One row per character per world, keyed on (profile, char_name), so a walking player rewrites
 * their own row rather than adding to a log. The table therefore holds as many rows as the village
 * has characters and never grows, which is what lets the read below get away with fetching all of
 * them and filtering by age afterwards.
 *
 * <p>Every write stamps {@code updated_at} from the database's clock. Age is what decides whether a
 * marker is drawn live, faded, or not at all, and comparing two clients' wall clocks would make that
 * decision wrong for anyone whose machine has drifted.
 */
public class PeerPositionDao {

    /** One character's published position, as stored. */
    public static final class Row {
        public final String charName;
        public final long gid;
        public final int ox, oy;
        public final double angle;
        /** Milliseconds since this row was written, measured entirely on the database's clock. */
        public final long ageMillis;

        public Row(String charName, long gid, int ox, int oy, double angle, long ageMillis) {
            this.charName = charName;
            this.gid = gid;
            this.ox = ox;
            this.oy = oy;
            this.angle = angle;
            this.ageMillis = ageMillis;
        }
    }

    /** One character's position on its way to the database. */
    public static final class Push {
        public final String charName;
        public final long gid;
        public final int ox, oy;
        public final double angle;

        public Push(String charName, long gid, int ox, int oy, double angle) {
            this.charName = charName;
            this.gid = gid;
            this.ox = ox;
            this.oy = oy;
            this.angle = angle;
        }
    }

    /**
     * Publish positions for one profile. Every character a client is logged in as goes in a single
     * JDBC batch, so a player running five sessions still costs one round trip per tick rather than
     * five.
     */
    public void upsertBatch(DatabaseAdapter adapter, String profile, List<Push> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        String sql;
        if (adapter instanceof PostgresAdapter) {
            sql = "INSERT INTO peer_positions (profile, char_name, gid, ox, oy, angle, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (profile, char_name) DO UPDATE SET "
                + "gid = EXCLUDED.gid, ox = EXCLUDED.ox, oy = EXCLUDED.oy, "
                + "angle = EXCLUDED.angle, updated_at = CURRENT_TIMESTAMP";
        } else {
            sql = "INSERT OR REPLACE INTO peer_positions "
                + "(profile, char_name, gid, ox, oy, angle, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (Push p : rows) {
            params.add(new Object[]{profile, p.charName, p.gid, p.ox, p.oy, p.angle});
        }
        adapter.executeBatch(sql, params);
    }

    /**
     * Age in milliseconds, computed by the database.
     *
     * <p>Do not subtract JDBC {@code Timestamp#getTime()} of {@code updated_at} and
     * {@code CURRENT_TIMESTAMP}: on Postgres the column is {@code TIMESTAMP} and
     * {@code CURRENT_TIMESTAMP} is timestamptz, so the JVM zone (Europe/Kiev = UTC+3) makes a
     * live row look three hours old and {@link nurgling.PeerPosition#DROP_MS} drops it.
     */
    static String loadByProfileSql(boolean postgres) {
        if (postgres) {
            return "SELECT char_name, gid, ox, oy, angle, "
                 + "GREATEST(0, CAST(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - updated_at)) * 1000 AS BIGINT)) AS age_ms "
                 + "FROM peer_positions WHERE profile = ?";
        }
        return "SELECT char_name, gid, ox, oy, angle, "
             + "CAST(MAX(0, (julianday('now') - julianday(updated_at)) * 86400000) AS INTEGER) AS age_ms "
             + "FROM peer_positions WHERE profile = ?";
    }

    /**
     * Every position published for a world, each carrying its age.
     *
     * <p>The whole profile is fetched rather than filtered in SQL: there is one row per character, so
     * this is a few dozen rows of a handful of bytes, and a WHERE on {@code updated_at} would want an
     * index the table deliberately does not have (see migration 12). Age is an interval from the
     * database clock, never two JDBC timestamps.
     */
    public List<Row> loadByProfile(DatabaseAdapter adapter, String profile) throws SQLException {
        List<Row> ret = new ArrayList<>();
        String sql = loadByProfileSql(adapter instanceof PostgresAdapter);
        try (ResultSet rs = adapter.executeQuery(sql, profile)) {
            while (rs.next()) {
                long age = Math.max(0L, rs.getLong("age_ms"));
                ret.add(new Row(rs.getString("char_name"), rs.getLong("gid"),
                                rs.getInt("ox"), rs.getInt("oy"), rs.getDouble("angle"), age));
            }
        }
        return ret;
    }

    /** Withdraw one character's position, on logout or when sharing is switched off. */
    public void delete(DatabaseAdapter adapter, String profile, String charName) throws SQLException {
        adapter.executeUpdate("DELETE FROM peer_positions WHERE profile = ? AND char_name = ?",
                              profile, charName);
    }
}
