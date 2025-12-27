package nurgling.areas.db;

import nurgling.areas.NArea;
import nurgling.db.DatabaseManager;
import nurgling.db.service.AreaService;

import java.util.Map;

/**
 * Адаптер для AreaSyncManager, который использует новую систему БД (DatabaseManager/AreaService)
 * вместо старой (AreaDBManager)
 */
public class AreaDBAdapter {
    private final AreaService areaService;
    
    public AreaDBAdapter(DatabaseManager databaseManager) {
        this.areaService = databaseManager.getAreaService();
    }
    
    /**
     * Загружает все зоны для текущего профиля
     */
    public Map<Integer, NArea> loadAllAreas() {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            return areaService.loadAreas(profile);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to load areas: " + e.getMessage());
            e.printStackTrace();
            return new java.util.HashMap<>();
        }
    }
    
    /**
     * Сохраняет зону без троттлинга (используется при получении обновлений с сервера)
     */
    public void saveAreaNoThrottle(NArea area) {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            areaService.saveArea(area, profile);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to save area: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Удаляет зону (soft delete)
     */
    public void deleteArea(int areaId, boolean skipServerSync) {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            areaService.softDeleteArea(areaId, profile);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to delete area: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Обновляет timestamp зоны (для сохранения server timestamp)
     */
    public void updateAreaTimestamp(int areaId, long timestamp) {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            areaService.updateAreaTimestamp(areaId, profile, timestamp);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to update timestamp: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Обновляет last_sync_at для зоны
     */
    public void updateLastSyncAt(String uuid, long syncTime) {
        try {
            areaService.updateLastSyncAt(uuid, syncTime);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to update last_sync_at: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Загружает UUID mapping для синхронизации
     */
    public void loadUuidMapping() {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            Map<String, java.util.Map.Entry<Integer, java.sql.Timestamp>> mapping = areaService.loadUuidMapping(profile);
            
            // Обновляем кэш в AreaSyncManager
            AreaSyncManager syncManager = AreaSyncManager.getInstance();
            for (Map.Entry<String, java.util.Map.Entry<Integer, java.sql.Timestamp>> entry : mapping.entrySet()) {
                String uuid = entry.getKey();
                Integer areaId = entry.getValue().getKey();
                java.sql.Timestamp lastSyncAt = entry.getValue().getValue();
                
                // Используем рефлексию для доступа к приватным полям AreaSyncManager
                try {
                    java.lang.reflect.Field uuidToAreaIdField = AreaSyncManager.class.getDeclaredField("uuidToAreaId");
                    uuidToAreaIdField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> uuidToAreaId = (Map<String, Integer>) uuidToAreaIdField.get(syncManager);
                    uuidToAreaId.put(uuid, areaId);
                    
                    if (lastSyncAt != null) {
                        java.lang.reflect.Field syncedZonesField = AreaSyncManager.class.getDeclaredField("syncedZones");
                        syncedZonesField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, Long> syncedZones = (Map<String, Long>) syncedZonesField.get(syncManager);
                        syncedZones.put(uuid, lastSyncAt.getTime());
                    }
                } catch (Exception e) {
                    System.err.println("AreaDBAdapter: Failed to update sync manager cache: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to load UUID mapping: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Получает текущий профиль (genus) из GameUI
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
        return "global"; // Default profile
    }
}

