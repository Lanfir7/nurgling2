package nurgling.db.service;

import nurgling.areas.NArea;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.AreaDao;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service layer for area operations with sync support
 */
public class AreaService {
    private final DatabaseManager databaseManager;
    private final AreaDao areaDao;

    // Sync state
    private volatile Timestamp lastSyncTime = null;
    private volatile boolean syncEnabled = false;
    private ScheduledExecutorService syncScheduler = null;
    private AreaSyncCallback syncCallback = null;

    /**
     * Callback interface for area sync events
     */
    public interface AreaSyncCallback {
        void onAreasUpdated(List<NArea> updatedAreas);
        void onAreaDeleted(int areaId);
        void onFullSync(Map<Integer, NArea> allAreas);
    }

    public AreaService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.areaDao = new AreaDao();
    }

    /**
     * Save an area to database
     */
    public void saveArea(NArea area, String profile) throws SQLException {
        JSONObject json = area.toJson();
        
        // Extract data (space, in, out, spec)
        JSONObject dataJson = new JSONObject();
        if (json.has("space")) dataJson.put("space", json.get("space"));
        if (json.has("in")) dataJson.put("in", json.get("in"));
        if (json.has("out")) dataJson.put("out", json.get("out"));
        if (json.has("spec")) dataJson.put("spec", json.get("spec"));

        databaseManager.executeOperation(adapter -> {
            areaDao.saveArea(adapter,
                area.id,
                area.name,
                area.path,
                area.hide,
                area.color.getRed(),
                area.color.getGreen(),
                area.color.getBlue(),
                area.color.getAlpha(),
                dataJson.toString(),
                profile);
            return null;
        });
    }

    /**
     * Save area asynchronously
     */
    public CompletableFuture<Void> saveAreaAsync(NArea area, String profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveArea(area, profile);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save area", e);
            }
        });
    }

    /**
     * Load all areas for a profile
     */
    public Map<Integer, NArea> loadAreas(String profile) throws SQLException {
        Map<Integer, NArea> areas = new HashMap<>();

        List<AreaDao.AreaData> areaDataList = databaseManager.executeOperation(
            adapter -> areaDao.loadAreasByProfile(adapter, profile)
        );

        for (AreaDao.AreaData data : areaDataList) {
            NArea area = convertToNArea(data);
            areas.put(area.id, area);
        }

        return areas;
    }

    /**
     * Load areas asynchronously
     */
    public CompletableFuture<Map<Integer, NArea>> loadAreasAsync(String profile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadAreas(profile);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load areas", e);
            }
        });
    }

    /**
     * Delete an area
     */
    public void deleteArea(int areaId, String profile) throws SQLException {
        databaseManager.executeOperation(adapter -> {
            areaDao.deleteArea(adapter, areaId, profile);
            return null;
        });
    }

    /**
     * Delete area asynchronously
     */
    public CompletableFuture<Void> deleteAreaAsync(int areaId, String profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                deleteArea(areaId, profile);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete area", e);
            }
        });
    }

    /**
     * Export areas from file to database (batch operation)
     * Updates local version after successful save to prevent sync overwrite
     */
    public int exportAreasToDatabase(Map<Integer, NArea> areas, String profile) throws SQLException {
        // Make a copy to avoid ConcurrentModificationException
        List<NArea> areasCopy = new ArrayList<>(areas.values());
        
        return databaseManager.executeOperation(adapter -> {
            int count = 0;
            for (NArea area : areasCopy) {
                // Extract data (space, in, out, spec)
                org.json.JSONObject json = area.toJson();
                org.json.JSONObject dataJson = new org.json.JSONObject();
                if (json.has("space")) dataJson.put("space", json.get("space"));
                if (json.has("in")) dataJson.put("in", json.get("in"));
                if (json.has("out")) dataJson.put("out", json.get("out"));
                if (json.has("spec")) dataJson.put("spec", json.get("spec"));

                int oldVersion = area.version;
                int newVersion = areaDao.saveArea(adapter,
                    area.id,
                    area.name,
                    area.path,
                    area.hide,
                    area.color.getRed(),
                    area.color.getGreen(),
                    area.color.getBlue(),
                    area.color.getAlpha(),
                    dataJson.toString(),
                    profile);
                // Update local version to prevent sync from re-downloading
                area.version = newVersion;
                if (newVersion != oldVersion) {
                    System.out.println("Area " + area.id + " saved: local v" + oldVersion + " -> v" + newVersion);
                }
                count++;
            }
            return count;
        });
    }

    /**
     * Result of import operation
     */
    public static class ImportResult {
        private final int importedCount;
        private final int skippedCount;
        private final int errorCount;

        public ImportResult(int importedCount, int skippedCount, int errorCount) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
            this.errorCount = errorCount;
        }

        public int getImportedCount() { return importedCount; }
        public int getSkippedCount() { return skippedCount; }
        public int getErrorCount() { return errorCount; }
    }

    /**
     * Import areas from server JSON file (old server structure)
     * Supports structure with uuid, zone_sync, last_update fields
     */
    public CompletableFuture<ImportResult> importAreasFromServerJsonAsync(java.io.File jsonFile, String profile) {
        return CompletableFuture.supplyAsync(() -> {
            int importedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            try {
                // Read JSON file
                StringBuilder contentBuilder = new StringBuilder();
                java.nio.file.Files.lines(java.nio.file.Paths.get(jsonFile.getAbsolutePath()), 
                    java.nio.charset.StandardCharsets.UTF_8)
                    .forEach(s -> contentBuilder.append(s).append("\n"));
                
                String content = contentBuilder.toString().trim();
                if (content.isEmpty()) {
                    System.err.println("Import: JSON file is empty");
                    return new ImportResult(0, 0, 1);
                }

                // Parse JSON - can be array or object with "areas" or "zones" array
                JSONArray areasArray;
                if (content.startsWith("[")) {
                    // Direct array
                    areasArray = new JSONArray(content);
                } else if (content.startsWith("{")) {
                    // Object with "areas" or "zones" field
                    JSONObject main = new JSONObject(content);
                    if (main.has("zones")) {
                        // Server format with "zones" field
                        areasArray = main.getJSONArray("zones");
                    } else if (main.has("areas")) {
                        areasArray = main.getJSONArray("areas");
                    } else {
                        // Single area object
                        areasArray = new JSONArray();
                        areasArray.put(main);
                    }
                } else {
                    System.err.println("Import: Invalid JSON format");
                    return new ImportResult(0, 0, 1);
                }

                // Process each area
                for (int i = 0; i < areasArray.length(); i++) {
                    try {
                        JSONObject areaJson = areasArray.getJSONObject(i);
                        
                        // Skip deleted areas
                        if (areaJson.has("deleted") && areaJson.getBoolean("deleted")) {
                            skippedCount++;
                            continue;
                        }

                        // Convert old server structure to NArea format
                        JSONObject nareaJson = convertServerAreaToNArea(areaJson);
                        
                        // Create NArea from JSON
                        NArea area = new NArea(nareaJson);
                        
                        // Import to database
                        try {
                            saveArea(area, profile);
                            importedCount++;
                        } catch (SQLException e) {
                            System.err.println("Import: SQL error importing area " + i + ": " + e.getMessage());
                            errorCount++;
                        }
                        
                    } catch (Exception e) {
                        System.err.println("Import: Failed to import area " + i + ": " + e.getMessage());
                        e.printStackTrace();
                        errorCount++;
                    }
                }

            } catch (Exception e) {
                System.err.println("Import: Failed to read JSON file: " + e.getMessage());
                e.printStackTrace();
                return new ImportResult(0, 0, 1);
            }

            return new ImportResult(importedCount, skippedCount, errorCount);
        });
    }

    /**
     * Convert server area JSON structure to NArea JSON format
     * Handles old server structure with uuid, zone_sync, last_update, in_zone, out_zone
     * Server format: color, space, spec, in_zone, out_zone are JSON strings, not objects
     */
    private JSONObject convertServerAreaToNArea(JSONObject serverArea) {
        JSONObject nareaJson = new JSONObject();

        // ID - can be int or uuid string (server uses UUID strings)
        // ID is REQUIRED for NArea constructor, so we must always generate one
        int areaId;
        if (serverArea.has("id")) {
            Object idObj = serverArea.get("id");
            if (idObj instanceof Integer) {
                areaId = (Integer) idObj;
            } else if (idObj instanceof String) {
                // UUID string - use hash as int id
                String uuidStr = (String) idObj;
                // Use consistent hash to generate int id from UUID
                areaId = Math.abs(uuidStr.hashCode());
            } else {
                // Fallback: generate from uuid or name
                areaId = generateIdFromArea(serverArea);
            }
        } else {
            // No id field - generate from uuid or name
            areaId = generateIdFromArea(serverArea);
        }
        nareaJson.put("id", areaId);

        // Name - required field
        if (serverArea.has("name")) {
            nareaJson.put("name", serverArea.getString("name"));
        } else {
            // Default name if missing
            nareaJson.put("name", "Imported Area " + areaId);
        }

        // Path
        if (serverArea.has("path")) {
            nareaJson.put("path", serverArea.getString("path"));
        }

        // Hide
        if (serverArea.has("hide")) {
            nareaJson.put("hide", serverArea.getBoolean("hide"));
        }

        // Color - server format: JSON string like "{\"a\": 56, \"b\": 65, \"g\": 194, \"r\": 194}"
        if (serverArea.has("color")) {
            Object colorObj = serverArea.get("color");
            if (colorObj instanceof JSONObject) {
                nareaJson.put("color", colorObj);
            } else if (colorObj instanceof String) {
                // Parse JSON string
                try {
                    nareaJson.put("color", new JSONObject((String) colorObj));
                } catch (Exception e) {
                    // Default color if parsing fails
                    JSONObject defaultColor = new JSONObject();
                    defaultColor.put("r", 194);
                    defaultColor.put("g", 194);
                    defaultColor.put("b", 65);
                    defaultColor.put("a", 56);
                    nareaJson.put("color", defaultColor);
                }
            }
        }

        // Space - server format: JSON string like "[{...}]" - REQUIRED field
        // Server uses "grid_id" but NArea expects "id" in space elements
        if (serverArea.has("space")) {
            Object spaceObj = serverArea.get("space");
            JSONArray spaceArray = null;
            
            if (spaceObj instanceof JSONArray) {
                spaceArray = (JSONArray) spaceObj;
            } else if (spaceObj instanceof String) {
                // Parse JSON string
                try {
                    spaceArray = new JSONArray((String) spaceObj);
                } catch (Exception e) {
                    System.err.println("Import: Failed to parse space JSON: " + e.getMessage());
                    spaceArray = new JSONArray();
                }
            } else if (spaceObj instanceof JSONObject) {
                // Single object - wrap in array
                spaceArray = new JSONArray();
                spaceArray.put(spaceObj);
            }
            
            // Convert grid_id to id in each space element
            if (spaceArray != null) {
                JSONArray convertedSpace = new JSONArray();
                for (int j = 0; j < spaceArray.length(); j++) {
                    JSONObject spaceElement = spaceArray.getJSONObject(j);
                    JSONObject convertedElement = new JSONObject();
                    
                    // Copy all fields
                    for (String key : spaceElement.keySet()) {
                        if (key.equals("grid_id")) {
                            // Convert grid_id to id
                            convertedElement.put("id", spaceElement.get(key));
                        } else {
                            convertedElement.put(key, spaceElement.get(key));
                        }
                    }
                    
                    // If no grid_id and no id, try to use grid_id from elsewhere or generate
                    if (!convertedElement.has("id") && !spaceElement.has("grid_id")) {
                        // Try to generate id from coordinates
                        if (spaceElement.has("begin_x") && spaceElement.has("begin_y")) {
                            long generatedId = ((long) spaceElement.getInt("begin_x") << 32) | spaceElement.getInt("begin_y");
                            convertedElement.put("id", generatedId);
                        }
                    }
                    
                    convertedSpace.put(convertedElement);
                }
                nareaJson.put("space", convertedSpace);
            } else {
                nareaJson.put("space", new JSONArray());
            }
        } else {
            // Space is required - use empty array as fallback
            nareaJson.put("space", new JSONArray());
        }

        // Spec - server format: JSON string like "[{...}]"
        if (serverArea.has("spec")) {
            Object specObj = serverArea.get("spec");
            if (specObj instanceof JSONArray) {
                nareaJson.put("spec", specObj);
            } else if (specObj instanceof String) {
                // Parse JSON string
                try {
                    nareaJson.put("spec", new JSONArray((String) specObj));
                } catch (Exception e) {
                    System.err.println("Import: Failed to parse spec JSON: " + e.getMessage());
                    nareaJson.put("spec", new JSONArray());
                }
            } else if (specObj instanceof JSONObject) {
                nareaJson.put("spec", specObj);
            }
        }

        // in_zone -> in - server format: JSON string like "[{...}]"
        if (serverArea.has("in_zone")) {
            Object inZoneObj = serverArea.get("in_zone");
            if (inZoneObj instanceof JSONArray) {
                nareaJson.put("in", inZoneObj);
            } else if (inZoneObj instanceof String) {
                // Parse JSON string
                try {
                    nareaJson.put("in", new JSONArray((String) inZoneObj));
                } catch (Exception e) {
                    System.err.println("Import: Failed to parse in_zone JSON: " + e.getMessage());
                    nareaJson.put("in", new JSONArray());
                }
            } else if (inZoneObj instanceof JSONObject) {
                nareaJson.put("in", inZoneObj);
            }
        } else if (serverArea.has("in")) {
            nareaJson.put("in", serverArea.get("in"));
        }

        // out_zone -> out - server format: JSON string like "[{...}]"
        if (serverArea.has("out_zone")) {
            Object outZoneObj = serverArea.get("out_zone");
            if (outZoneObj instanceof JSONArray) {
                nareaJson.put("out", outZoneObj);
            } else if (outZoneObj instanceof String) {
                // Parse JSON string
                try {
                    nareaJson.put("out", new JSONArray((String) outZoneObj));
                } catch (Exception e) {
                    System.err.println("Import: Failed to parse out_zone JSON: " + e.getMessage());
                    nareaJson.put("out", new JSONArray());
                }
            } else if (outZoneObj instanceof JSONObject) {
                nareaJson.put("out", outZoneObj);
            }
        } else if (serverArea.has("out")) {
            nareaJson.put("out", serverArea.get("out"));
        }

        // Version (if exists, otherwise default to 1)
        if (serverArea.has("version")) {
            nareaJson.put("version", serverArea.getInt("version"));
        } else {
            nareaJson.put("version", 1);
        }

        return nareaJson;
    }

    /**
     * Generate area ID from available fields (uuid, name, etc.)
     */
    private int generateIdFromArea(JSONObject serverArea) {
        // Try to use uuid first
        if (serverArea.has("uuid")) {
            Object uuidObj = serverArea.get("uuid");
            if (uuidObj instanceof String) {
                return Math.abs(((String) uuidObj).hashCode());
            }
        }
        // Fallback to name + path
        String name = serverArea.optString("name", "");
        String path = serverArea.optString("path", "");
        String combined = name + path;
        if (!combined.isEmpty()) {
            return Math.abs(combined.hashCode());
        }
        // Last resort: use current timestamp hash
        return Math.abs((int) System.currentTimeMillis());
    }

    /**
     * Export areas asynchronously with automatic retry on failure
     */
    public CompletableFuture<Integer> exportAreasToDatabaseAsync(Map<Integer, NArea> areas, String profile) {
        // Make a copy to avoid ConcurrentModificationException
        List<NArea> areasCopy = new ArrayList<>(areas.values());
        
        return databaseManager.executeWithRetry(adapter -> {
            int count = 0;
            for (NArea area : areasCopy) {
                // Extract data (space, in, out, spec)
                org.json.JSONObject json = area.toJson();
                org.json.JSONObject dataJson = new org.json.JSONObject();
                if (json.has("space")) dataJson.put("space", json.get("space"));
                if (json.has("in")) dataJson.put("in", json.get("in"));
                if (json.has("out")) dataJson.put("out", json.get("out"));
                if (json.has("spec")) dataJson.put("spec", json.get("spec"));

                int oldVersion = area.version;
                int newVersion = areaDao.saveArea(adapter,
                    area.id,
                    area.name,
                    area.path,
                    area.hide,
                    area.color.getRed(),
                    area.color.getGreen(),
                    area.color.getBlue(),
                    area.color.getAlpha(),
                    dataJson.toString(),
                    profile);
                // Update local version to prevent sync from re-downloading
                area.version = newVersion;
                if (newVersion != oldVersion) {
                    System.out.println("Area " + area.id + " saved: local v" + oldVersion + " -> v" + newVersion);
                }
                count++;
            }
            return count;
        }, "Export " + areasCopy.size() + " areas");
    }

    /**
     * Check for updates since last sync
     */
    public List<NArea> checkForUpdates(String profile) throws SQLException {
        List<NArea> updatedAreas = new ArrayList<>();

        // Guard: ensure database is ready
        if (!databaseManager.isReady()) {
            return updatedAreas;
        }

        if (lastSyncTime == null) {
            // First sync - return all areas
            Map<Integer, NArea> allAreas = loadAreas(profile);
            updatedAreas.addAll(allAreas.values());
            
            // Update last sync time
            Timestamp latest = databaseManager.executeOperation(
                adapter -> areaDao.getLastUpdateTime(adapter, profile)
            );
            if (latest != null) {
                lastSyncTime = latest;
            } else {
                lastSyncTime = new Timestamp(System.currentTimeMillis());
            }
        } else {
            // Check for changes since last sync
            final Timestamp syncTime = lastSyncTime;
            List<AreaDao.AreaData> changedAreas = databaseManager.executeOperation(
                adapter -> areaDao.getAreasUpdatedAfter(adapter, profile, syncTime)
            );

            for (AreaDao.AreaData data : changedAreas) {
                updatedAreas.add(convertToNArea(data));
            }

            // Update last sync time
            if (!changedAreas.isEmpty()) {
                Timestamp latest = databaseManager.executeOperation(
                    adapter -> areaDao.getLastUpdateTime(adapter, profile)
                );
                if (latest != null) {
                    lastSyncTime = latest;
                }
            }
        }

        return updatedAreas;
    }

    /**
     * Start periodic sync
     */
    public void startSync(String profile, long intervalSeconds, AreaSyncCallback callback) {
        if (syncEnabled) {
            stopSync();
        }

        this.syncCallback = callback;
        this.syncEnabled = true;
        this.syncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Area-Sync-Worker");
            t.setDaemon(true);  // Daemon thread won't prevent JVM shutdown
            return t;
        });

        syncScheduler.scheduleAtFixedRate(() -> {
            if (!syncEnabled) return;

            // Check if database is ready
            if (!databaseManager.isReady()) {
                return;
            }

            // Get current genus dynamically (not the one from startup)
            String currentProfile = getCurrentProfile();
            if (currentProfile == null || currentProfile.isEmpty()) {
                // Don't sync without a valid profile/genus
                return;
            }

            try {
                // Get local area versions from cache
                java.util.Map<Integer, Integer> localVersions = getLocalAreaVersions();
                
                // Check for areas with higher version in DB
                List<NArea> updates = checkForUpdatesWithVersions(currentProfile, localVersions);
                if (!updates.isEmpty() && syncCallback != null) {
                    syncCallback.onAreasUpdated(updates);
                }
            } catch (Exception e) {
                // Silently ignore "no such table" errors - table might not be created yet
                String msg = e.getMessage();
                if (msg != null && !msg.contains("no such table") && !msg.contains("no such column")) {
                    System.err.println("Area sync error: " + msg);
                }
            }
        }, 5, intervalSeconds, TimeUnit.SECONDS); // Initial delay of 5 seconds to allow migrations to run

        System.out.println("Area sync started with interval: " + intervalSeconds + " seconds");
    }

    /**
     * Stop periodic sync
     */
    public void stopSync() {
        syncEnabled = false;
        if (syncScheduler != null) {
            syncScheduler.shutdown();
            try {
                syncScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                syncScheduler.shutdownNow();
            }
            syncScheduler = null;
        }
        syncCallback = null;
        System.out.println("Area sync stopped");
    }

    /**
     * Force full sync
     */
    public void forceFullSync(String profile) throws SQLException {
        lastSyncTime = null;
        Map<Integer, NArea> allAreas = loadAreas(profile);
        if (syncCallback != null) {
            syncCallback.onFullSync(allAreas);
        }
    }

    /**
     * Convert AreaData to NArea
     */
    private NArea convertToNArea(AreaDao.AreaData data) {
        JSONObject json = new JSONObject();
        json.put("id", data.getId());
        json.put("name", data.getName());
        json.put("path", data.getPath());
        json.put("hide", data.isHide());
        json.put("version", data.getVersion());

        JSONObject color = new JSONObject();
        color.put("r", data.getColorR());
        color.put("g", data.getColorG());
        color.put("b", data.getColorB());
        color.put("a", data.getColorA());
        json.put("color", color);

        // Parse and add the stored data (space, in, out, spec)
        JSONObject storedData = new JSONObject(data.getData());
        if (storedData.has("space")) json.put("space", storedData.get("space"));
        if (storedData.has("in")) json.put("in", storedData.get("in"));
        if (storedData.has("out")) json.put("out", storedData.get("out"));
        if (storedData.has("spec")) json.put("spec", storedData.get("spec"));

        return new NArea(json);
    }

    /**
     * Check for updates based on version comparison
     * Returns only areas where DB version > local version
     */
    public List<NArea> checkForUpdatesWithVersions(String profile, java.util.Map<Integer, Integer> localVersions) throws SQLException {
        List<NArea> updatedAreas = new ArrayList<>();

        // Guard: ensure database is ready
        if (!databaseManager.isReady()) {
            return updatedAreas;
        }

        // Get all area versions from DB
        java.util.Map<Integer, Integer> dbVersions;
        try {
            dbVersions = databaseManager.executeOperation(
                adapter -> areaDao.getAllAreaVersions(adapter, profile)
            );
        } catch (Exception e) {
            System.err.println("Area sync: Failed to get DB versions: " + e.getMessage());
            return updatedAreas;
        }

        // Find areas that need updating (DB version > local version or new areas)
        List<Integer> areasToLoad = new ArrayList<>();
        for (java.util.Map.Entry<Integer, Integer> entry : dbVersions.entrySet()) {
            int areaId = entry.getKey();
            int dbVersion = entry.getValue();
            int localVersion = localVersions.getOrDefault(areaId, 0);

            if (dbVersion > localVersion) {
                System.out.println("Area sync: area " + areaId + " DB v" + dbVersion + " > local v" + localVersion);
                areasToLoad.add(areaId);
            }
        }

        // Load full area data for areas that need updating
        if (!areasToLoad.isEmpty()) {
            System.out.println("Area sync: loading " + areasToLoad.size() + " areas from DB (new or updated)");
            for (int areaId : areasToLoad) {
                AreaDao.AreaData data = databaseManager.executeOperation(
                    adapter -> areaDao.loadArea(adapter, areaId, profile)
                );
                if (data != null) {
                    updatedAreas.add(convertToNArea(data));
                }
            }
        }

        return updatedAreas;
    }

    /**
     * Check if sync is running
     */
    public boolean isSyncRunning() {
        return syncEnabled;
    }

    /**
     * Get last sync time
     */
    public Timestamp getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * Reset sync state (for testing or reconnection)
     */
    public void resetSyncState() {
        lastSyncTime = null;
    }

    /**
     * Get versions of areas from local cache (MCache.areas)
     */
    private java.util.Map<Integer, Integer> getLocalAreaVersions() {
        java.util.Map<Integer, Integer> versions = new java.util.HashMap<>();
        try {
            if (nurgling.NUtils.getGameUI() != null && 
                nurgling.NUtils.getGameUI().map != null &&
                nurgling.NUtils.getGameUI().map.glob != null && 
                nurgling.NUtils.getGameUI().map.glob.map != null) {
                
                java.util.Map<Integer, NArea> areas = nurgling.NUtils.getGameUI().map.glob.map.areas;
                if (areas != null) {
                    for (java.util.Map.Entry<Integer, NArea> entry : areas.entrySet()) {
                        versions.put(entry.getKey(), entry.getValue().version);
                    }
                }

                // Mark locally deleted areas with MAX_VALUE so sync never tries to reload them
                if (nurgling.NUtils.getGameUI().map instanceof nurgling.NMapView) {
                    nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
                    for (int deletedId : mapView.locallyDeletedAreas) {
                        versions.put(deletedId, Integer.MAX_VALUE);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors when getting local versions
        }
        return versions;
    }

    /**
     * Get current profile (genus) dynamically from GameUI
     */
    private String getCurrentProfile() {
        try {
            if (nurgling.NUtils.getGameUI() != null) {
                String genus = nurgling.NUtils.getGameUI().getGenus();
                if (genus != null && !genus.isEmpty()) {
                    return genus;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null; // Return null if no valid genus
    }
}
