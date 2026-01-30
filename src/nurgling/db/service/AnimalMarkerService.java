package nurgling.db.service;

import nurgling.NConfig;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.AnimalMarkerDao;

import java.sql.SQLException;
import java.util.List;

/**
 * Service for animal markers (Postgres only).
 * Used when discovering animals (ObjectTracker) and when updating quality on carcass inspect.
 */
public class AnimalMarkerService {
    private final DatabaseManager databaseManager;
    private final AnimalMarkerDao animalMarkerDao = new AnimalMarkerDao();

    public AnimalMarkerService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Returns true if animal markers are available (Postgres enabled and DB ready).
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
     * Insert a new animal marker. No-op if Postgres is not enabled or DB unavailable.
     * iconPath — путь ресурса иконки (gfx/invobjs/kritter/...) для загрузки после перезахода; может быть null.
     */
    public void insert(String profile, long gobId, String animalType, String displayName, String iconPath,
                       long segmentId, int tileX, int tileY, Long gridId, Integer localTileX, Integer localTileY) {
        if (!isAvailable()) {
            return;
        }
        try {
            databaseManager.executeOperation(adapter -> {
                animalMarkerDao.insert(adapter, profile, gobId, animalType, displayName, iconPath,
                    segmentId, tileX, tileY, gridId, localTileX, localTileY);
                return null;
            });
        } catch (SQLException e) {
            System.err.println("AnimalMarkerService: insert failed: " + e.getMessage());
        }
    }

    /**
     * Update quality and killed_at/killed_by for a marker by gob_id (when carcass is inspected).
     */
    public int updateQualityByGobId(String profile, long gobId, double quality, String killedBy) {
        if (!isAvailable()) {
            System.err.println("AnimalMarkerService: updateQualityByGobId skipped — not available");
            return 0;
        }
        try {
            int rows = databaseManager.executeOperation(adapter ->
                animalMarkerDao.updateQualityByGobId(adapter, profile, gobId, quality, killedBy)
            );
            System.err.println("AnimalMarkerService: updateQualityByGobId profile=" + profile + " gobId=" + gobId + " quality=" + quality + " killedBy=" + killedBy + " -> rows updated=" + rows);
            return rows;
        } catch (SQLException e) {
            System.err.println("AnimalMarkerService: updateQualityByGobId failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Delete an animal marker by profile and gob_id (e.g. when user removes mark via Shift+RMB).
     */
    public int deleteByGobId(String profile, long gobId) {
        if (!isAvailable()) {
            return 0;
        }
        try {
            return databaseManager.executeOperation(adapter ->
                animalMarkerDao.deleteByGobId(adapter, profile, gobId)
            );
        } catch (SQLException e) {
            System.err.println("AnimalMarkerService: deleteByGobId failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Load all animal markers for a profile.
     */
    public List<AnimalMarkerDao.AnimalMarkerData> findAllByProfile(String profile) {
        if (!isAvailable()) {
            return java.util.Collections.emptyList();
        }
        try {
            return databaseManager.executeOperation(adapter ->
                animalMarkerDao.findAllByProfile(adapter, profile)
            );
        } catch (SQLException e) {
            System.err.println("AnimalMarkerService: findAllByProfile failed: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
