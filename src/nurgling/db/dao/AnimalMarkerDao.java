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
 * Data Access Object for animal_markers table (Postgres only).
 * Used for markers created when discovering animals (e.g. Simple Routes + Discord),
 * updated with quality when a carcass is inspected.
 */
public class AnimalMarkerDao {

    public static class AnimalMarkerData {
        private final int id;
        private final String profile;
        private final long gobId;
        private final String animalType;
        private final String displayName;
        private final String iconPath;
        private final long segmentId;
        private final int tileX;
        private final int tileY;
        private final Long gridId;
        private final Integer localTileX;
        private final Integer localTileY;
        private final Double quality;
        private final Timestamp createdAt;
        private final Timestamp updatedAt;
        private final Timestamp killedAt;
        private final String killedBy;

        public AnimalMarkerData(int id, String profile, long gobId, String animalType, String displayName, String iconPath,
                                long segmentId, int tileX, int tileY, Long gridId, Integer localTileX, Integer localTileY,
                                Double quality, Timestamp createdAt, Timestamp updatedAt, Timestamp killedAt, String killedBy) {
            this.id = id;
            this.profile = profile;
            this.gobId = gobId;
            this.animalType = animalType;
            this.displayName = displayName;
            this.iconPath = iconPath;
            this.segmentId = segmentId;
            this.tileX = tileX;
            this.tileY = tileY;
            this.gridId = gridId;
            this.localTileX = localTileX;
            this.localTileY = localTileY;
            this.quality = quality;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.killedAt = killedAt;
            this.killedBy = killedBy;
        }

        public int getId() { return id; }
        public String getProfile() { return profile; }
        public long getGobId() { return gobId; }
        public String getAnimalType() { return animalType; }
        public String getDisplayName() { return displayName; }
        /** Путь ресурса иконки (gfx/invobjs/kritter/...), сохранённый при создании маркера для загрузки после перезахода. */
        public String getIconPath() { return iconPath; }
        public long getSegmentId() { return segmentId; }
        public int getTileX() { return tileX; }
        public int getTileY() { return tileY; }
        public Long getGridId() { return gridId; }
        public Integer getLocalTileX() { return localTileX; }
        public Integer getLocalTileY() { return localTileY; }
        public Double getQuality() { return quality; }
        public Timestamp getCreatedAt() { return createdAt; }
        public Timestamp getUpdatedAt() { return updatedAt; }
        public Timestamp getKilledAt() { return killedAt; }
        public String getKilledBy() { return killedBy; }
    }

    /** Интерпретирует killed_at из БД как UTC (сервер +0), чтобы «убита X назад» не давала смещение из-за часового пояса клиента (Киев +2). */
    private static Timestamp parseKilledAtUtc(ResultSet rs) throws SQLException {
        try {
            Timestamp t = rs.getTimestamp("killed_at", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            return t;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Insert a new animal marker. Only runs on Postgres.
     * Uses ON CONFLICT (profile, gob_id) DO NOTHING to avoid duplicates.
     * iconPath — путь ресурса иконки (gfx/invobjs/kritter/...) для загрузки после перезахода; может быть null.
     */
    public void insert(DatabaseAdapter adapter, String profile, long gobId, String animalType, String displayName, String iconPath,
                      long segmentId, int tileX, int tileY, Long gridId, Integer localTileX, Integer localTileY) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return;
        }
        String sql = "INSERT INTO animal_markers (profile, gob_id, animal_type, display_name, icon_path, segment_id, tile_x, tile_y, grid_id, local_tile_x, local_tile_y) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (profile, gob_id) DO NOTHING";
        adapter.executeUpdate(sql, profile, gobId, animalType != null ? animalType : "", displayName != null ? displayName : "",
            iconPath != null ? iconPath : "", segmentId, tileX, tileY, gridId != null ? gridId : 0, localTileX != null ? localTileX : 0, localTileY != null ? localTileY : 0);
    }

    /**
     * Update quality and killed_at/killed_by for a marker by gob_id (when carcass is inspected). Only updates if quality is still NULL.
     */
    public int updateQualityByGobId(DatabaseAdapter adapter, String profile, long gobId, double quality, String killedBy) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return 0;
        }
        String sql = "UPDATE animal_markers SET quality = ?, killed_at = CURRENT_TIMESTAMP, killed_by = ?, updated_at = CURRENT_TIMESTAMP WHERE profile = ? AND gob_id = ? AND quality IS NULL";
        int rows = adapter.executeUpdate(sql, quality, killedBy != null ? killedBy : "", profile, gobId);
        if (rows == 0) {
            System.err.println("AnimalMarkerDao: updateQualityByGobId matched 0 rows (profile=" + profile + " gob_id=" + gobId + "). Check profile/gob_id in DB or if quality already set.");
        }
        return rows;
    }

    public int updateLocation(DatabaseAdapter adapter, String profile, long gobId,
                              long segmentId, int tileX, int tileY) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return 0;
        }
        String sql = "UPDATE animal_markers SET segment_id = ?, tile_x = ?, tile_y = ?, updated_at = CURRENT_TIMESTAMP "
                   + "WHERE profile = ? AND gob_id = ?";
        return adapter.executeUpdate(sql, segmentId, tileX, tileY, profile, gobId);
    }

    /**
     * Delete an animal marker by profile and gob_id (e.g. when user removes mark via Shift+RMB).
     */
    public int deleteByGobId(DatabaseAdapter adapter, String profile, long gobId) throws SQLException {
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return 0;
        }
        String sql = "DELETE FROM animal_markers WHERE profile = ? AND gob_id = ?";
        return adapter.executeUpdate(sql, profile, gobId);
    }

    /**
     * Load all animal markers for a profile.
     */
    public List<AnimalMarkerData> findAllByProfile(DatabaseAdapter adapter, String profile) throws SQLException {
        List<AnimalMarkerData> list = new ArrayList<>();
        if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
            return list;
        }
        if (!adapter.tableExists("animal_markers")) {
            return list;
        }
        String sql = "SELECT id, profile, gob_id, animal_type, display_name, icon_path, segment_id, tile_x, tile_y, grid_id, local_tile_x, local_tile_y, quality, created_at, updated_at, killed_at, killed_by " +
            "FROM animal_markers WHERE profile = ? ORDER BY id";
        try (ResultSet rs = adapter.executeQuery(sql, profile)) {
            while (rs.next()) {
                list.add(new AnimalMarkerData(
                    rs.getInt("id"),
                    rs.getString("profile"),
                    rs.getLong("gob_id"),
                    rs.getString("animal_type"),
                    rs.getString("display_name"),
                    rs.getString("icon_path"),
                    rs.getLong("segment_id"),
                    rs.getInt("tile_x"),
                    rs.getInt("tile_y"),
                    rs.getObject("grid_id") != null ? rs.getLong("grid_id") : null,
                    rs.getObject("local_tile_x") != null ? rs.getInt("local_tile_x") : null,
                    rs.getObject("local_tile_y") != null ? rs.getInt("local_tile_y") : null,
                    rs.getObject("quality") != null ? rs.getDouble("quality") : null,
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("updated_at"),
                    parseKilledAtUtc(rs),
                    rs.getString("killed_by")
                ));
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("icon_path") || e.getMessage().contains("killed_at") || e.getMessage().contains("killed_by") || e.getMessage().contains("column"))) {
                String sqlOld = "SELECT id, profile, gob_id, animal_type, display_name, segment_id, tile_x, tile_y, grid_id, local_tile_x, local_tile_y, quality, created_at, updated_at, killed_at, killed_by " +
                    "FROM animal_markers WHERE profile = ? ORDER BY id";
                try (ResultSet rs = adapter.executeQuery(sqlOld, profile)) {
                    while (rs.next()) {
                        list.add(new AnimalMarkerData(
                            rs.getInt("id"),
                            rs.getString("profile"),
                            rs.getLong("gob_id"),
                            rs.getString("animal_type"),
                            rs.getString("display_name"),
                            null,
                            rs.getLong("segment_id"),
                            rs.getInt("tile_x"),
                            rs.getInt("tile_y"),
                            rs.getObject("grid_id") != null ? rs.getLong("grid_id") : null,
                            rs.getObject("local_tile_x") != null ? rs.getInt("local_tile_x") : null,
                            rs.getObject("local_tile_y") != null ? rs.getInt("local_tile_y") : null,
                            rs.getObject("quality") != null ? rs.getDouble("quality") : null,
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at"),
                            parseKilledAtUtc(rs),
                            rs.getString("killed_by")
                        ));
                    }
                } catch (SQLException e2) {
                    String sqlOld2 = "SELECT id, profile, gob_id, animal_type, display_name, segment_id, tile_x, tile_y, grid_id, local_tile_x, local_tile_y, quality, created_at, updated_at " +
                        "FROM animal_markers WHERE profile = ? ORDER BY id";
                    try (ResultSet rs = adapter.executeQuery(sqlOld2, profile)) {
                        while (rs.next()) {
                            list.add(new AnimalMarkerData(
                                rs.getInt("id"),
                                rs.getString("profile"),
                                rs.getLong("gob_id"),
                                rs.getString("animal_type"),
                                rs.getString("display_name"),
                                null,
                                rs.getLong("segment_id"),
                                rs.getInt("tile_x"),
                                rs.getInt("tile_y"),
                                rs.getObject("grid_id") != null ? rs.getLong("grid_id") : null,
                                rs.getObject("local_tile_x") != null ? rs.getInt("local_tile_x") : null,
                                rs.getObject("local_tile_y") != null ? rs.getInt("local_tile_y") : null,
                                rs.getObject("quality") != null ? rs.getDouble("quality") : null,
                                rs.getTimestamp("created_at"),
                                rs.getTimestamp("updated_at"),
                                null,
                                null
                            ));
                        }
                    }
                }
            } else if (e.getMessage() != null && (e.getMessage().contains("killed_at") || e.getMessage().contains("killed_by"))) {
                String sqlOld = "SELECT id, profile, gob_id, animal_type, display_name, segment_id, tile_x, tile_y, grid_id, local_tile_x, local_tile_y, quality, created_at, updated_at " +
                    "FROM animal_markers WHERE profile = ? ORDER BY id";
                try (ResultSet rs = adapter.executeQuery(sqlOld, profile)) {
                    while (rs.next()) {
                        list.add(new AnimalMarkerData(
                            rs.getInt("id"),
                            rs.getString("profile"),
                            rs.getLong("gob_id"),
                            rs.getString("animal_type"),
                            rs.getString("display_name"),
                            null,
                            rs.getLong("segment_id"),
                            rs.getInt("tile_x"),
                            rs.getInt("tile_y"),
                            rs.getObject("grid_id") != null ? rs.getLong("grid_id") : null,
                            rs.getObject("local_tile_x") != null ? rs.getInt("local_tile_x") : null,
                            rs.getObject("local_tile_y") != null ? rs.getInt("local_tile_y") : null,
                            rs.getObject("quality") != null ? rs.getDouble("quality") : null,
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at"),
                            null,
                            null
                        ));
                    }
                }
            } else {
                throw e;
            }
        }
        return list;
    }
}
