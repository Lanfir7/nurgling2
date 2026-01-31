package nurgling.db.dao;

import nurgling.db.DatabaseAdapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Data Access Object for local_timers table (Postgres only).
 * Stores resource timers (tar pit, clay pit, etc.) for sharing between clients.
 * All timestamps are stored in UTC to avoid timezone issues.
 */
public class LocalTimerDao {

    /** Calendar for interpreting timestamps as UTC from DB */
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public static class LocalTimerData {
        private final int id;
        private final String profile;
        private final String resourceId;
        private final long segmentId;
        private final int tileX;
        private final int tileY;
        private final String resourceName;
        private final String resourceType;
        private final long startTimeUtc;    // Unix timestamp in milliseconds (UTC)
        private final long durationMs;       // Duration in milliseconds
        private final String description;
        private final Timestamp createdAt;
        private final Timestamp updatedAt;

        public LocalTimerData(int id, String profile, String resourceId, long segmentId, int tileX, int tileY,
                             String resourceName, String resourceType, long startTimeUtc, long durationMs,
                             String description, Timestamp createdAt, Timestamp updatedAt) {
            this.id = id;
            this.profile = profile;
            this.resourceId = resourceId;
            this.segmentId = segmentId;
            this.tileX = tileX;
            this.tileY = tileY;
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            this.startTimeUtc = startTimeUtc;
            this.durationMs = durationMs;
            this.description = description;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public int getId() { return id; }
        public String getProfile() { return profile; }
        public String getResourceId() { return resourceId; }
        public long getSegmentId() { return segmentId; }
        public int getTileX() { return tileX; }
        public int getTileY() { return tileY; }
        public String getResourceName() { return resourceName; }
        public String getResourceType() { return resourceType; }
        /** Start time in UTC milliseconds (Instant.toEpochMilli()) */
        public long getStartTimeUtc() { return startTimeUtc; }
        /** Duration in milliseconds */
        public long getDurationMs() { return durationMs; }
        public String getDescription() { return description; }
        public Timestamp getCreatedAt() { return createdAt; }
        public Timestamp getUpdatedAt() { return updatedAt; }

        /**
         * Check if timer is expired (based on UTC time)
         */
        public boolean isExpired() {
            return getRemainingTimeMs() <= 0;
        }

        /**
         * Get remaining time in milliseconds
         */
        public long getRemainingTimeMs() {
            long now = System.currentTimeMillis();
            long elapsed = now - startTimeUtc;
            return Math.max(0, durationMs - elapsed);
        }
    }

    /**
     * Insert or update a timer. Uses UPSERT to handle conflicts on (profile, resource_id).
     * All times are stored as UTC milliseconds to avoid timezone issues.
     */
    public void upsert(DatabaseAdapter adapter, String profile, String resourceId, long segmentId,
                       int tileX, int tileY, String resourceName, String resourceType,
                       long startTimeUtc, long durationMs, String description) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return;
        }
        String sql = "INSERT INTO local_timers (profile, resource_id, segment_id, tile_x, tile_y, " +
            "resource_name, resource_type, start_time_utc, duration_ms, description) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (profile, resource_id) DO UPDATE SET " +
            "segment_id = EXCLUDED.segment_id, " +
            "tile_x = EXCLUDED.tile_x, " +
            "tile_y = EXCLUDED.tile_y, " +
            "resource_name = EXCLUDED.resource_name, " +
            "resource_type = EXCLUDED.resource_type, " +
            "start_time_utc = EXCLUDED.start_time_utc, " +
            "duration_ms = EXCLUDED.duration_ms, " +
            "description = EXCLUDED.description, " +
            "updated_at = CURRENT_TIMESTAMP";
        adapter.executeUpdate(sql, profile, resourceId, segmentId, tileX, tileY,
            resourceName != null ? resourceName : "",
            resourceType != null ? resourceType : "",
            startTimeUtc, durationMs,
            description != null ? description : "");
    }

    /**
     * Delete a timer by profile and resource_id.
     */
    public int deleteByResourceId(DatabaseAdapter adapter, String profile, String resourceId) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return 0;
        }
        String sql = "DELETE FROM local_timers WHERE profile = ? AND resource_id = ?";
        return adapter.executeUpdate(sql, profile, resourceId);
    }

    /**
     * Delete all expired timers for a profile.
     * Timer is expired when: current_time_utc > start_time_utc + duration_ms
     */
    public int deleteExpired(DatabaseAdapter adapter, String profile) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return 0;
        }
        long nowUtc = System.currentTimeMillis();
        String sql = "DELETE FROM local_timers WHERE profile = ? AND (start_time_utc + duration_ms) < ?";
        return adapter.executeUpdate(sql, profile, nowUtc);
    }

    /**
     * Load all non-expired timers for a profile.
     */
    public List<LocalTimerData> findAllByProfile(DatabaseAdapter adapter, String profile) throws SQLException {
        List<LocalTimerData> list = new ArrayList<>();
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return list;
        }
        if (!adapter.tableExists("local_timers")) {
            return list;
        }
        
        long nowUtc = System.currentTimeMillis();
        // Only select non-expired timers
        String sql = "SELECT id, profile, resource_id, segment_id, tile_x, tile_y, " +
            "resource_name, resource_type, start_time_utc, duration_ms, description, " +
            "created_at, updated_at " +
            "FROM local_timers WHERE profile = ? AND (start_time_utc + duration_ms) > ? " +
            "ORDER BY (start_time_utc + duration_ms) ASC";  // Order by expiration time
        
        try (ResultSet rs = adapter.executeQuery(sql, profile, nowUtc)) {
            while (rs.next()) {
                list.add(new LocalTimerData(
                    rs.getInt("id"),
                    rs.getString("profile"),
                    rs.getString("resource_id"),
                    rs.getLong("segment_id"),
                    rs.getInt("tile_x"),
                    rs.getInt("tile_y"),
                    rs.getString("resource_name"),
                    rs.getString("resource_type"),
                    rs.getLong("start_time_utc"),
                    rs.getLong("duration_ms"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at", UTC_CALENDAR),
                    rs.getTimestamp("updated_at", UTC_CALENDAR)
                ));
            }
        }
        return list;
    }

    /**
     * Load a single timer by profile and resource_id.
     */
    public LocalTimerData findByResourceId(DatabaseAdapter adapter, String profile, String resourceId) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return null;
        }
        if (!adapter.tableExists("local_timers")) {
            return null;
        }
        
        String sql = "SELECT id, profile, resource_id, segment_id, tile_x, tile_y, " +
            "resource_name, resource_type, start_time_utc, duration_ms, description, " +
            "created_at, updated_at " +
            "FROM local_timers WHERE profile = ? AND resource_id = ?";
        
        try (ResultSet rs = adapter.executeQuery(sql, profile, resourceId)) {
            if (rs.next()) {
                return new LocalTimerData(
                    rs.getInt("id"),
                    rs.getString("profile"),
                    rs.getString("resource_id"),
                    rs.getLong("segment_id"),
                    rs.getInt("tile_x"),
                    rs.getInt("tile_y"),
                    rs.getString("resource_name"),
                    rs.getString("resource_type"),
                    rs.getLong("start_time_utc"),
                    rs.getLong("duration_ms"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at", UTC_CALENDAR),
                    rs.getTimestamp("updated_at", UTC_CALENDAR)
                );
            }
        }
        return null;
    }
}
