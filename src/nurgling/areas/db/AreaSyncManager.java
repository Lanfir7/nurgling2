package nurgling.areas.db;

import nurgling.areas.NArea;
import nurgling.areas.sync.ZoneSyncClient;
import nurgling.areas.storage.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер синхронизации зон с сервером.
 * Обеспечивает защиту от дублей и конфликтов.
 */
public class AreaSyncManager {
    private static AreaSyncManager instance;
    private ZoneSyncClient syncClient;
    @SuppressWarnings("unused")
    private String serverUrl; // Хранится для возможного использования в будущем
    private String zoneSync;
    private boolean enabled = false;
    
    // Кэш для отслеживания синхронизированных зон
    private final Map<String, Long> syncedZones = new ConcurrentHashMap<>(); // uuid -> last_updated
    private long lastSyncTime = 0;
    private static final long SYNC_THROTTLE_MS = 5000; // Минимальный интервал между синхронизациями (5 секунд)
    
    // Для защиты от дублей: отслеживаем зоны по UUID
    private final Map<String, Integer> uuidToAreaId = new ConcurrentHashMap<>(); // uuid -> area_id
    
    private AreaSyncManager() {
        // Инициализация будет выполнена при первом использовании
    }
    
    public static synchronized AreaSyncManager getInstance() {
        if (instance == null) {
            instance = new AreaSyncManager();
        }
        return instance;
    }
    
    /**
     * Инициализирует синхронизацию
     */
    public void initialize(String serverUrl, String zoneSync) {
        this.serverUrl = serverUrl;
        this.zoneSync = zoneSync;
        
        if (serverUrl != null && !serverUrl.isEmpty() && 
            zoneSync != null && !zoneSync.isEmpty()) {
            this.syncClient = new ZoneSyncClient(serverUrl, zoneSync);
            this.enabled = true;
            System.out.println("AreaSyncManager: Initialized with server: " + serverUrl + ", zone_sync: " + zoneSync);
            
            // Синхронизируем время с сервером при инициализации
            syncClient.syncTime();
        } else {
            this.enabled = false;
            System.out.println("AreaSyncManager: Sync disabled (server URL or zone_sync not set)");
        }
    }
    
    /**
     * Проверяет, включена ли синхронизация
     */
    public boolean isEnabled() {
        return enabled && syncClient != null;
    }
    
    /**
     * Синхронизирует все зоны с сервером (push + pull)
     */
    public void syncAll(Collection<NArea> localAreas, AreaDBManager dbManager) {
        if (!isEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Throttling - не синхронизируем слишком часто
        if (currentTime - lastSyncTime < SYNC_THROTTLE_MS) {
            return;
        }
        
        // Получаем интервал синхронизации из настроек
        Object intervalObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncIntervalMinutes);
        int intervalMinutes = 5; // По умолчанию 5 минут
        if (intervalObj instanceof Integer) {
            intervalMinutes = (Integer) intervalObj;
        } else if (intervalObj instanceof Number) {
            intervalMinutes = ((Number) intervalObj).intValue();
        }
        // Ограничиваем диапазон 1-20 минут
        if (intervalMinutes < 1) intervalMinutes = 1;
        if (intervalMinutes > 20) intervalMinutes = 20;
        
        long syncIntervalMs = intervalMinutes * 60 * 1000; // Конвертируем в миллисекунды
        
        // Проверяем, прошло ли достаточно времени с последней синхронизации
        if (lastSyncTime > 0 && (currentTime - lastSyncTime) < syncIntervalMs) {
            return; // Еще не время синхронизировать
        }
        
        // Проверяем доступность сервера
        if (!syncClient.checkHealth()) {
            System.err.println("AreaSyncManager: Server is not available, skipping sync");
            return;
        }
        
        // Периодически синхронизируем время с сервером (каждые 10 синхронизаций или раз в час)
        // Это помогает компенсировать дрифт времени
        if (lastSyncTime == 0 || (currentTime - lastSyncTime) > 3600000) { // Раз в час
            syncClient.syncTime();
        }
        
        System.out.println("AreaSyncManager: Starting sync (interval: " + intervalMinutes + " min)...");
        System.out.println("AreaSyncManager: Synced zones cache size: " + syncedZones.size());
        
        // Загружаем актуальные данные из БД перед синхронизацией
        // Это гарантирует, что у нас актуальные значения lastUpdated
        Collection<NArea> freshAreas = new ArrayList<>();
        try {
            Map<Integer, NArea> dbAreas = dbManager.loadAllAreas();
            freshAreas = dbAreas.values();
            System.out.println("AreaSyncManager: Loaded " + freshAreas.size() + " areas from DB for sync");
            
            // Выводим информацию о зонах для отладки
            for (NArea area : freshAreas) {
                Long lastSynced = syncedZones.get(area.uuid);
                System.out.println("AreaSyncManager: Zone " + area.id + " (" + area.name + ") - " +
                                 "lastUpdated: " + area.lastUpdated + 
                                 (area.uuid != null ? ", UUID: " + area.uuid.substring(0, Math.min(8, area.uuid.length())) : ", UUID: null") +
                                 (lastSynced != null ? ", lastSynced: " + lastSynced + 
                                  (area.lastUpdated > lastSynced ? " (NEEDS SYNC)" : " (synced)") : " (never synced)"));
            }
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to load fresh areas from DB, using provided areas: " + e.getMessage());
            e.printStackTrace();
            freshAreas = localAreas; // Fallback на переданные зоны
        }
        
        // 1. Push: отправляем измененные локальные зоны на сервер
        pushLocalChanges(freshAreas);
        
        // 2. Pull: получаем обновления с сервера
        pullServerUpdates(freshAreas, dbManager);
        
        lastSyncTime = currentTime;
        System.out.println("AreaSyncManager: Sync completed");
    }
    
    /**
     * Отправляет измененные локальные зоны на сервер
     */
    private void pushLocalChanges(Collection<NArea> localAreas) {
        if (localAreas == null || localAreas.isEmpty()) {
            return;
        }
        
        int pushed = 0;
        int skipped = 0;
        int conflicts = 0;
        
        for (NArea area : localAreas) {
            // Генерируем UUID если его нет
            if (area.uuid == null || area.uuid.isEmpty()) {
                area.uuid = UUID.randomUUID().toString();
                area.lastUpdated = System.currentTimeMillis();
            }
            
            // Устанавливаем zone_sync если его нет
            if (area.zoneSync == null || area.zoneSync.isEmpty()) {
                area.zoneSync = this.zoneSync;
            }
            
            // Проверяем, нужно ли отправлять
            // Отправляем если:
            // 1. Зона еще не синхронизировалась (нет в кэше)
            // 2. Зона была изменена после последней синхронизации (lastUpdated > lastSynced)
            // 3. Если lastUpdated очень свежий (менее 5 секунд назад) - возможно зона только что была изменена
            Long lastSynced = syncedZones.get(area.uuid);
            long currentTime = System.currentTimeMillis();
            
            boolean shouldPush = false;
            String reason = "";
            
            if (lastSynced == null) {
                // Зона еще не синхронизировалась - ВСЕГДА отправляем
                shouldPush = true;
                reason = "never synced (UUID: " + (area.uuid != null ? area.uuid.substring(0, Math.min(8, area.uuid.length())) : "null") + ")";
            } else if (area.lastUpdated > lastSynced) {
                // Зона была изменена после последней синхронизации - отправляем
                shouldPush = true;
                long diff = area.lastUpdated - lastSynced;
                reason = "changed (lastUpdated: " + area.lastUpdated + " > lastSynced: " + lastSynced + ", diff: " + diff + " ms)";
            } else if ((currentTime - area.lastUpdated) < 5000) {
                // lastUpdated очень свежий (менее 5 секунд) - возможно зона только что была изменена
                // Отправляем, даже если lastSynced больше (может быть проблема с синхронизацией времени)
                shouldPush = true;
                reason = "recently changed (lastUpdated: " + area.lastUpdated + " is recent, lastSynced: " + lastSynced + ", time diff: " + (currentTime - area.lastUpdated) + " ms)";
            } else if ((currentTime - lastSynced) < SYNC_THROTTLE_MS) {
                // Недавно синхронизировалась и не изменилась - пропускаем
                System.out.println("AreaSyncManager: Skipping zone " + area.id + " (" + area.name + ") - recently synced (lastSynced: " + lastSynced + ", lastUpdated: " + area.lastUpdated + ")");
                skipped++;
                continue;
            } else {
                // Прошло достаточно времени, но зона не изменилась - не отправляем
                shouldPush = false;
                reason = "no changes (lastUpdated: " + area.lastUpdated + " <= lastSynced: " + lastSynced + ", age: " + (currentTime - area.lastUpdated) + " ms)";
            }
            
            if (!shouldPush) {
                System.out.println("AreaSyncManager: Skipping zone " + area.id + " (" + area.name + ") - " + reason);
                skipped++;
                continue;
            }
            
            System.out.println("AreaSyncManager: Pushing zone " + area.id + " (" + area.name + ") - " + reason + 
                             " (UUID: " + (area.uuid != null ? area.uuid.substring(0, Math.min(8, area.uuid.length())) : "null") + ")");
            
            // Отправляем на сервер
            boolean success = syncClient.pushZone(area);
            if (success) {
                syncedZones.put(area.uuid, area.lastUpdated);
                uuidToAreaId.put(area.uuid, area.id);
                pushed++;
            } else {
                conflicts++;
            }
        }
        
        if (pushed > 0 || conflicts > 0) {
            System.out.println("AreaSyncManager: Pushed " + pushed + " zones, " + 
                             skipped + " skipped, " + conflicts + " conflicts");
        }
    }
    
    /**
     * Получает обновления с сервера и применяет их
     */
    private void pullServerUpdates(Collection<NArea> localAreas, AreaDBManager dbManager) {
        // Определяем время последней синхронизации (берем время минус 1 минута для надежности)
        long updatedAfter = lastSyncTime > 0 ? lastSyncTime - 60000 : 0;
        
        // Получаем зоны с сервера
        List<NArea> serverZones = syncClient.pullZones(updatedAfter);
        
        if (serverZones == null || serverZones.isEmpty()) {
            return;
        }
        
        System.out.println("AreaSyncManager: Received " + serverZones.size() + " zones from server");
        
        // Создаем карту локальных зон по UUID для быстрого поиска
        Map<String, NArea> localByUuid = new HashMap<>();
        for (NArea area : localAreas) {
            if (area.uuid != null && !area.uuid.isEmpty()) {
                localByUuid.put(area.uuid, area);
            }
        }
        
        int merged = 0;
        int created = 0;
        int skipped = 0;
        
        for (NArea serverZone : serverZones) {
            if (serverZone.uuid == null || serverZone.uuid.isEmpty()) {
                skipped++;
                continue;
            }
            
            NArea localZone = localByUuid.get(serverZone.uuid);
            
            if (localZone == null) {
                // Новая зона с сервера - создаем локально
                // Нужно найти свободный ID
                int newId = findNextAvailableId(localAreas);
                serverZone.id = newId;
                serverZone.synced = true;
                
                try {
                    dbManager.saveArea(serverZone);
                    uuidToAreaId.put(serverZone.uuid, serverZone.id);
                    syncedZones.put(serverZone.uuid, serverZone.lastUpdated);
                    created++;
                } catch (Exception e) {
                    System.err.println("AreaSyncManager: Failed to create zone from server: " + e.getMessage());
                }
            } else {
                // Зона существует локально - разрешаем конфликт
                if (resolveConflict(localZone, serverZone, dbManager)) {
                    merged++;
                } else {
                    skipped++;
                }
            }
        }
        
        if (merged > 0 || created > 0) {
            System.out.println("AreaSyncManager: Merged " + merged + " zones, created " + 
                             created + " new zones, skipped " + skipped);
        }
    }
    
    /**
     * Разрешает конфликт между локальной и серверной версией зоны
     * @return true если конфликт разрешен, false если пропущено
     */
    private boolean resolveConflict(NArea local, NArea server, AreaDBManager dbManager) {
        // Защита от дублей: если UUID уже используется другой зоной, пропускаем
        Integer existingId = uuidToAreaId.get(server.uuid);
        if (existingId != null && existingId != local.id) {
            System.err.println("AreaSyncManager: UUID conflict detected: " + server.uuid + 
                            " is used by area " + existingId + ", skipping");
            return false;
        }
        
        // Правило: принимаем более новую версию
        if (server.lastUpdated > local.lastUpdated) {
            // Сервер новее - обновляем локальную зону
            mergeZoneData(local, server);
            try {
                dbManager.saveArea(local);
                syncedZones.put(local.uuid, local.lastUpdated);
                return true;
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Failed to merge zone: " + e.getMessage());
                return false;
            }
        } else if (local.lastUpdated > server.lastUpdated) {
            // Локальная версия новее - отправляем на сервер
            syncClient.pushZone(local);
            syncedZones.put(local.uuid, local.lastUpdated);
            return true;
        } else {
            // Времена равны - пропускаем (уже синхронизировано)
            return false;
        }
    }
    
    /**
     * Объединяет данные серверной зоны в локальную
     */
    private void mergeZoneData(NArea local, NArea server) {
        // Обновляем основные поля
        local.name = server.name;
        local.path = server.path;
        local.color = server.color;
        local.hide = server.hide;
        local.lastUpdated = server.lastUpdated;
        local.zoneSync = server.zoneSync;
        local.synced = true;
        
        // Обновляем пространственные данные (если есть)
        if (server.space != null && server.space.space != null && !server.space.space.isEmpty()) {
            local.space = server.space;
        }
        
        // Обновляем специализации
        if (server.spec != null && !server.spec.isEmpty()) {
            local.spec = server.spec;
        }
        
        // Обновляем входы/выходы
        if (server.jin != null) {
            local.jin = server.jin;
        }
        if (server.jout != null) {
            local.jout = server.jout;
        }
    }
    
    /**
     * Находит следующий доступный ID для новой зоны
     */
    private int findNextAvailableId(Collection<NArea> areas) {
        int maxId = 0;
        for (NArea area : areas) {
            if (area.id > maxId) {
                maxId = area.id;
            }
        }
        return maxId + 1;
    }
    
    /**
     * Отправляет одну зону на сервер (для немедленной синхронизации)
     */
    public void pushZone(NArea area) {
        if (!isEnabled() || area == null) {
            return;
        }
        
        // Генерируем UUID если его нет
        if (area.uuid == null || area.uuid.isEmpty()) {
            area.uuid = UUID.randomUUID().toString();
        }
        
        // Устанавливаем zone_sync если его нет
        if (area.zoneSync == null || area.zoneSync.isEmpty()) {
            area.zoneSync = this.zoneSync;
        }
        
        area.lastUpdated = System.currentTimeMillis();
        
        boolean success = syncClient.pushZone(area);
        if (success) {
            syncedZones.put(area.uuid, area.lastUpdated);
            uuidToAreaId.put(area.uuid, area.id);
        }
    }
    
    /**
     * Удаляет зону на сервере
     */
    public void deleteZone(NArea area) {
        if (!isEnabled() || area == null || area.uuid == null || area.uuid.isEmpty()) {
            return;
        }
        
        syncClient.deleteZone(area.uuid);
        syncedZones.remove(area.uuid);
        uuidToAreaId.remove(area.uuid);
    }
    
    /**
     * Загружает UUID зон из БД для предотвращения дублей
     * НЕ загружает syncedZones, так как updated_at в БД не означает, что зона была синхронизирована с сервером
     */
    public void loadUuidMapping(DatabaseConnectionManager poolManager) {
        uuidToAreaId.clear();
        // НЕ очищаем syncedZones - он будет заполняться при реальной синхронизации
        // Это гарантирует, что измененные зоны будут отправлены на сервер
        
        try {
            Connection conn = poolManager.getConnection();
            String sql = "SELECT id, global_id FROM areas WHERE global_id IS NOT NULL AND global_id != ''";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    int areaId = rs.getInt("id");
                    String uuid = rs.getString("global_id");
                    
                    if (uuid != null && !uuid.isEmpty()) {
                        uuidToAreaId.put(uuid, areaId);
                        // НЕ добавляем в syncedZones - это будет сделано при реальной синхронизации
                    }
                }
            }
            
            System.out.println("AreaSyncManager: Loaded " + uuidToAreaId.size() + " UUID mappings from DB (syncedZones cache is empty, will be filled during sync)");
        } catch (SQLException e) {
            System.err.println("AreaSyncManager: Failed to load UUID mapping: " + e.getMessage());
        }
    }
    
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        if (syncClient != null) {
            syncClient.setServerUrl(serverUrl);
        }
    }
    
    public void setZoneSync(String zoneSync) {
        this.zoneSync = zoneSync;
        if (syncClient != null) {
            syncClient.setZoneSync(zoneSync);
        }
    }
}
