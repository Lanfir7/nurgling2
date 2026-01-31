package nurgling.db.service;

import nurgling.NConfig;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.LocalTimerDao;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Service for local resource timers (Postgres only).
 * Provides CRUD operations for timer storage in database.
 * All times are stored as UTC milliseconds to ensure timezone-independent operation.
 */
public class LocalTimerService {
    private final DatabaseManager databaseManager;
    private final LocalTimerDao localTimerDao = new LocalTimerDao();

    public LocalTimerService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Returns true if local timers are available (Postgres enabled and DB ready).
     */
    public boolean isAvailable() {
        if (databaseManager == null || !databaseManager.isReady()) {
            return false;
        }
        Boolean ndb = (Boolean) NConfig.get(NConfig.Key.ndbenable);
        Boolean postgres = (Boolean) NConfig.get(NConfig.Key.postgres);
        return Boolean.TRUE.equals(ndb) && Boolean.TRUE.equals(postgres);
    }

    /**
     * Insert or update a timer. Uses UPSERT to handle conflicts.
     * @param profile The profile/world identifier
     * @param resourceId Unique resource identifier (e.g., "res_123_456_789_gfx_terobjs_...")
     * @param segmentId Map segment ID
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param resourceName Display name of the resource
     * @param resourceType Resource type path (e.g., "gfx/terobjs/map/tarpit")
     * @param startTimeUtc Start time in UTC milliseconds (from Instant.now().toEpochMilli())
     * @param durationMs Duration in milliseconds
     * @param description User-friendly description
     */
    public void upsert(String profile, String resourceId, long segmentId, int tileX, int tileY,
                       String resourceName, String resourceType, long startTimeUtc, long durationMs,
                       String description) {
        if (!isAvailable()) {
            return;
        }
        try {
            databaseManager.executeOperation(adapter -> {
                localTimerDao.upsert(adapter, profile, resourceId, segmentId, tileX, tileY,
                    resourceName, resourceType, startTimeUtc, durationMs, description);
                return null;
            });
        } catch (SQLException e) {
            System.err.println("LocalTimerService: upsert failed: " + e.getMessage());
        }
    }

    /**
     * Delete a timer by profile and resource_id.
     */
    public int deleteByResourceId(String profile, String resourceId) {
        if (!isAvailable()) {
            return 0;
        }
        try {
            return databaseManager.executeOperation(adapter ->
                localTimerDao.deleteByResourceId(adapter, profile, resourceId)
            );
        } catch (SQLException e) {
            System.err.println("LocalTimerService: deleteByResourceId failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Delete all expired timers for a profile.
     */
    public int deleteExpired(String profile) {
        if (!isAvailable()) {
            return 0;
        }
        try {
            return databaseManager.executeOperation(adapter ->
                localTimerDao.deleteExpired(adapter, profile)
            );
        } catch (SQLException e) {
            System.err.println("LocalTimerService: deleteExpired failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Load all non-expired timers for a profile.
     */
    public List<LocalTimerDao.LocalTimerData> findAllByProfile(String profile) {
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        try {
            return databaseManager.executeOperation(adapter ->
                localTimerDao.findAllByProfile(adapter, profile)
            );
        } catch (SQLException e) {
            System.err.println("LocalTimerService: findAllByProfile failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Find a single timer by profile and resource_id.
     */
    public LocalTimerDao.LocalTimerData findByResourceId(String profile, String resourceId) {
        if (!isAvailable()) {
            return null;
        }
        try {
            return databaseManager.executeOperation(adapter ->
                localTimerDao.findByResourceId(adapter, profile, resourceId)
            );
        } catch (SQLException e) {
            System.err.println("LocalTimerService: findByResourceId failed: " + e.getMessage());
            return null;
        }
    }
}
