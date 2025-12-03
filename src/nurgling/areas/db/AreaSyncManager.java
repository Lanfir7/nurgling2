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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
    
    // ExecutorService для фоновых задач синхронизации
    private ExecutorService syncExecutor;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    
    // Кэш для проверки доступности сервера (обновляется в фоне)
    private volatile boolean serverHealthCached = false;
    private volatile long lastHealthCheck = 0;
    private static final long HEALTH_CHECK_CACHE_MS = 30000; // Кэшируем результат на 30 секунд
    
    private AreaSyncManager() {
        // Создаем ExecutorService с одним потоком для синхронизации
        syncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AreaSyncThread");
            t.setDaemon(true); // Поток-демон, не будет блокировать завершение приложения
            return t;
        });
    }
    
    /**
     * Останавливает ExecutorService (вызывается при завершении работы)
     */
    public void shutdown() {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdown();
        }
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
     * Синхронизирует все зоны с сервером (push + pull) - запускается в фоновом потоке
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
        
        // Получаем интервал синхронизации из настроек (в секундах)
        Object intervalObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncIntervalMinutes);
        int intervalSeconds = 300; // По умолчанию 300 секунд (5 минут)
        if (intervalObj instanceof Integer) {
            intervalSeconds = (Integer) intervalObj;
        } else if (intervalObj instanceof Number) {
            intervalSeconds = ((Number) intervalObj).intValue();
        }
        
        // Все значения хранятся в секундах (начиная с новой версии)
        // Ограничиваем диапазон 5-1200 секунд (5 сек - 20 минут)
        if (intervalSeconds < 5) intervalSeconds = 5;
        if (intervalSeconds > 1200) intervalSeconds = 1200;
        
        long syncIntervalMs = intervalSeconds * 1000; // Конвертируем секунды в миллисекунды
        
        // Отладочный вывод для проверки интервала
        // if (lastSyncTime > 0) {
        //     long timeSinceLastSync = currentTime - lastSyncTime;
        //     System.out.println("AreaSyncManager: Sync interval: " + intervalSeconds + " sec (" + syncIntervalMs + " ms), " +
        //                      "time since last sync: " + timeSinceLastSync + " ms, " +
        //                      "need to sync: " + (timeSinceLastSync >= syncIntervalMs));
        // }
        
        // Проверяем, прошло ли достаточно времени с последней синхронизации
        if (lastSyncTime > 0 && (currentTime - lastSyncTime) < syncIntervalMs) {
            return; // Еще не время синхронизировать
        }
        
        // Проверяем, не выполняется ли уже синхронизация
        if (syncInProgress.get()) {
            return; // Синхронизация уже выполняется
        }
        
        // Запускаем синхронизацию в фоновом потоке
        final Collection<NArea> areasToSync = new ArrayList<>(localAreas);
        final AreaDBManager manager = dbManager;
        final int finalIntervalSeconds = intervalSeconds;
        
        syncInProgress.set(true);
        syncExecutor.submit(() -> {
            try {
                syncAllInternal(areasToSync, manager, finalIntervalSeconds);
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Error during sync: " + e.getMessage());
                e.printStackTrace();
            } finally {
                syncInProgress.set(false);
            }
        });
    }
    
    /**
     * Внутренний метод синхронизации (выполняется в фоновом потоке)
     */
    private void syncAllInternal(Collection<NArea> localAreas, AreaDBManager dbManager, int intervalSeconds) {
        long currentTime = System.currentTimeMillis();
        
        // Проверяем доступность сервера (используем кэш или проверяем в фоне)
        if (!checkHealthCached()) {
            System.err.println("AreaSyncManager: Server is not available, skipping sync");
            return;
        }
        
        // Периодически синхронизируем время с сервером (каждые 10 синхронизаций или раз в час)
        // Это помогает компенсировать дрифт времени
        if (lastSyncTime == 0 || (currentTime - lastSyncTime) > 3600000) { // Раз в час
            syncClient.syncTime();
        }
        
        // Форматируем интервал для вывода
        String intervalStr;
        if (intervalSeconds < 60) {
            intervalStr = intervalSeconds + " sec";
        } else {
            int minutes = intervalSeconds / 60;
            int remainingSeconds = intervalSeconds % 60;
            if (remainingSeconds == 0) {
                intervalStr = minutes + " min";
            } else {
                intervalStr = minutes + " min " + remainingSeconds + " sec";
            }
        }
        System.out.println("AreaSyncManager: Starting sync (interval: " + intervalStr + ")...");
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
        
        // ВАЖНО: Сначала получаем обновления с сервера (pull),
        // чтобы удалить зоны, которые были удалены на сервере.
        // Это предотвращает отправку зон, которые уже удалены на сервере.
        pullServerUpdates(freshAreas, dbManager);
        
        // После получения обновлений с сервера, загружаем зоны снова,
        // так как некоторые зоны могли быть удалены
        try {
            Map<Integer, NArea> dbAreas = dbManager.loadAllAreas();
            freshAreas = dbAreas.values();
            System.out.println("AreaSyncManager: Reloaded " + freshAreas.size() + " areas from DB after pull");
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to reload areas from DB after pull: " + e.getMessage());
        }
        
        // Теперь отправляем локальные изменения (push)
        pushLocalChanges(freshAreas, dbManager);
        
        // ВАЖНО: Обновляем визуальное отображение зон в игре после синхронизации
        // Это нужно для того, чтобы удаленные зоны исчезли, а новые/обновленные зоны появились
        refreshVisualZones();
        
        lastSyncTime = System.currentTimeMillis();
        System.out.println("AreaSyncManager: Sync completed");
    }
    
    /**
     * Проверяет доступность сервера с кэшированием результата
     */
    private boolean checkHealthCached() {
        long currentTime = System.currentTimeMillis();
        
        // Если кэш актуален, используем его
        if ((currentTime - lastHealthCheck) < HEALTH_CHECK_CACHE_MS && lastHealthCheck > 0) {
            return serverHealthCached;
        }
        
        // Обновляем кэш в фоне
        lastHealthCheck = currentTime;
        serverHealthCached = syncClient.checkHealth();
        return serverHealthCached;
    }
    
    /**
     * Отправляет измененные локальные зоны на сервер
     */
    private void pushLocalChanges(Collection<NArea> localAreas, AreaDBManager dbManager) {
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
            } else if ((currentTime - area.lastUpdated) < 60000) {
                // lastUpdated очень свежий (менее 1 минуты) - возможно зона только что была изменена
                // Отправляем, даже если lastSynced больше (может быть проблема с синхронизацией времени)
                // Это важно, так как после изменения зоны lastUpdated обновляется, но может быть загружено старое значение из БД
                shouldPush = true;
                reason = "recently changed (lastUpdated: " + area.lastUpdated + " is recent, lastSynced: " + lastSynced + ", time diff: " + (currentTime - area.lastUpdated) + " ms)";
            } else if ((currentTime - lastSynced) < SYNC_THROTTLE_MS) {
                // Недавно синхронизировалась и не изменилась - пропускаем
                System.out.println("AreaSyncManager: Skipping zone " + area.id + " (" + area.name + ") - recently synced (lastSynced: " + lastSynced + ", lastUpdated: " + area.lastUpdated + ")");
                skipped++;
                continue;
            } else {
                // Прошло достаточно времени, но зона не изменилась
                // ВАЖНО: Проверяем, не была ли зона изменена после последней синхронизации
                // Если lastUpdated в БД новее, чем lastSynced, значит зона была изменена
                // Но если lastSynced больше lastUpdated, это может быть из-за разницы времени
                // В этом случае проверяем, не была ли зона изменена недавно (менее 1 минуты назад)
                long timeSinceLastUpdate = currentTime - area.lastUpdated;
                long timeSinceLastSync = currentTime - lastSynced;
                
                // Если зона была изменена менее минуты назад, а последняя синхронизация была давно,
                // значит зона была изменена после синхронизации - отправляем
                if (timeSinceLastUpdate < 60000 && timeSinceLastSync > 60000) {
                    shouldPush = true;
                    reason = "changed after sync (lastUpdated: " + area.lastUpdated + " is recent, lastSynced: " + lastSynced + " is old)";
                } else {
                    shouldPush = false;
                    reason = "no changes (lastUpdated: " + area.lastUpdated + " <= lastSynced: " + lastSynced + ", age: " + timeSinceLastUpdate + " ms)";
                }
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
                
                // Сохраняем last_sync_at в БД
                updateLastSyncAt(area.uuid, area.lastUpdated, dbManager);
                
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
        // ВАЖНО: Для обнаружения удаленных зон нужно запросить ВСЕ зоны с сервера,
        // а не только обновленные после lastSyncTime. Иначе удаленные зоны не будут обнаружены.
        // Используем updatedAfter = 0, чтобы получить все активные зоны
        long updatedAfter = 0; // Запрашиваем все зоны для проверки удалений
        
        // Получаем зоны с сервера
        List<NArea> serverZones = syncClient.pullZones(updatedAfter);
        
        // Если сервер вернул пустой список, это нормально - просто нет зон
        if (serverZones == null) {
            serverZones = new ArrayList<>();
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
        
        // Создаем множество UUID зон, полученных с сервера
        Set<String> serverUuids = new HashSet<>();
        
        for (NArea serverZone : serverZones) {
            // Пропускаем зоны без UUID (уже отфильтровано в serverJsonToArea)
            // Также пропускаем удаленные зоны (serverJsonToArea возвращает null для них)
            if (serverZone == null || serverZone.uuid == null || serverZone.uuid.isEmpty()) {
                skipped++;
                continue;
            }
            
            serverUuids.add(serverZone.uuid);
            
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
                    
                    // Сохраняем last_sync_at в БД
                    updateLastSyncAt(serverZone.uuid, serverZone.lastUpdated, dbManager);
                    
                    created++;
                    System.out.println("AreaSyncManager: Created zone " + serverZone.id + " (" + 
                                     (serverZone.name != null ? serverZone.name : "unknown") + 
                                     ") from server (UUID: " + serverZone.uuid + ")");
                } catch (Exception e) {
                    System.err.println("AreaSyncManager: Failed to create zone from server: " + e.getMessage());
                    e.printStackTrace();
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
        
        // ВАЖНО: Проверяем, какие зоны были синхронизированы, но отсутствуют на сервере
        // Это означает, что они были удалены на сервере, и нужно удалить их локально
        // НЕ вызываем loadUuidMapping здесь, так как он может перезаписать syncedZones
        // syncedZones уже содержит актуальные данные о синхронизированных зонах из текущей сессии
        // и из предыдущей загрузки при инициализации
        
        int deleted = 0;
        for (NArea localZone : localAreas) {
            if (localZone.uuid != null && !localZone.uuid.isEmpty()) {
                // ВАЖНО: Проверяем, была ли зона синхронизирована ранее
                // Зона считается синхронизированной ТОЛЬКО если она есть в syncedZones
                // (т.е. имеет last_sync_at в БД, что означает, что она была успешно отправлена на сервер)
                // НЕ проверяем uuidToAreaId, так как он содержит все зоны с UUID, даже несинхронизированные
                boolean wasSynced = syncedZones.containsKey(localZone.uuid);
                boolean inServerUuids = serverUuids.contains(localZone.uuid);
                
                // Логируем для отладки
                if (!inServerUuids) {
                    System.out.println("AreaSyncManager: Zone " + localZone.id + " (" + localZone.name + ") not in server response. " +
                                     "wasSynced=" + wasSynced + ", UUID=" + localZone.uuid.substring(0, Math.min(8, localZone.uuid.length())));
                }
                
                // Если зона была синхронизирована (имеет last_sync_at), но её нет на сервере - значит она удалена на сервере
                // НЕ удаляем зоны, которые никогда не синхронизировались (они просто еще не были отправлены)
                if (wasSynced && !inServerUuids) {
                    // Зона была удалена на сервере - удаляем её локально
                    System.out.println("AreaSyncManager: Zone " + localZone.id + " (" + 
                                     (localZone.name != null ? localZone.name : "unknown") + 
                                     ") was deleted on server, deleting locally (UUID: " + localZone.uuid + ")");
                    try {
                        dbManager.deleteArea(localZone.id);
                        syncedZones.remove(localZone.uuid);
                        uuidToAreaId.remove(localZone.uuid);
                        deleted++;
                        
                        // ВАЖНО: Обновляем визуальное отображение зон в игре
                        updateVisualZone(localZone.id);
                    } catch (Exception e) {
                        System.err.println("AreaSyncManager: Failed to delete zone " + localZone.id + 
                                         " that was deleted on server: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
        
        if (deleted > 0) {
            System.out.println("AreaSyncManager: Deleted " + deleted + " zones that were removed on server");
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
                
                // Сохраняем last_sync_at в БД
                updateLastSyncAt(local.uuid, local.lastUpdated, dbManager);
                
                return true;
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Failed to merge zone: " + e.getMessage());
                return false;
            }
        } else if (local.lastUpdated > server.lastUpdated) {
            // Локальная версия новее - отправляем на сервер
            boolean success = syncClient.pushZone(local);
            if (success) {
                syncedZones.put(local.uuid, local.lastUpdated);
                
                // Сохраняем last_sync_at в БД
                updateLastSyncAt(local.uuid, local.lastUpdated, dbManager);
            }
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
     * Отправляет одну зону на сервер (для немедленной синхронизации) - запускается в фоновом потоке
     */
    public void pushZone(NArea area) {
        if (!isEnabled() || area == null) {
            return;
        }
        
        // Сохраняем ссылку на оригинальную зону для обновления после синхронизации
        final NArea originalArea = area;
        
        // Запускаем отправку в фоновом потоке
        syncExecutor.submit(() -> {
            try {
                // Генерируем UUID если его нет
                if (originalArea.uuid == null || originalArea.uuid.isEmpty()) {
                    originalArea.uuid = UUID.randomUUID().toString();
                }
                
                // Устанавливаем zone_sync если его нет
                if (originalArea.zoneSync == null || originalArea.zoneSync.isEmpty()) {
                    originalArea.zoneSync = this.zoneSync;
                }
                
                originalArea.lastUpdated = System.currentTimeMillis();
                
                boolean success = syncClient.pushZone(originalArea);
                if (success) {
                    syncedZones.put(originalArea.uuid, originalArea.lastUpdated);
                    uuidToAreaId.put(originalArea.uuid, originalArea.id);
                }
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Error pushing zone: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Удаляет зону на сервере
     */
    public void deleteZone(NArea area) {
        if (!isEnabled()) {
            System.out.println("AreaSyncManager: Zone synchronization is disabled, skipping deletion");
            return;
        }
        
        if (area == null) {
            System.err.println("AreaSyncManager: WARNING - Cannot delete zone: area is null");
            return;
        }
        
        if (area.uuid == null || area.uuid.isEmpty()) {
            System.err.println("AreaSyncManager: WARNING - Cannot delete zone " + area.id + 
                             " (" + (area.name != null ? area.name : "unknown") + "): UUID is null or empty");
            return;
        }
        
        System.out.println("AreaSyncManager: Deleting zone " + area.id + " (" + 
                         (area.name != null ? area.name : "unknown") + ") on server (UUID: " + area.uuid + ")");
        
        boolean success = syncClient.deleteZone(area.uuid);
        if (success) {
            System.out.println("AreaSyncManager: Successfully deleted zone " + area.id + " on server");
            syncedZones.remove(area.uuid);
            uuidToAreaId.remove(area.uuid);
        } else {
            System.err.println("AreaSyncManager: Failed to delete zone " + area.id + " on server");
        }
    }
    
    /**
     * Загружает UUID зон из БД для предотвращения дублей
     * Загружает syncedZones из last_sync_at, чтобы знать, какие зоны уже синхронизированы
     */
    public void loadUuidMapping(DatabaseConnectionManager poolManager) {
        // ВАЖНО: НЕ очищаем syncedZones, если он уже заполнен из текущей сессии
        // Очищаем только uuidToAreaId для обновления маппинга
        // syncedZones должен сохранять информацию о зонах, синхронизированных в этой сессии
        uuidToAreaId.clear();
        // НЕ очищаем syncedZones здесь, так как это может удалить информацию о зонах, синхронизированных в текущей сессии
        // syncedZones будет обновлен только для зон с last_sync_at в БД
        
        try {
            Connection conn = poolManager.getConnection();
            // Загружаем UUID и last_sync_at для заполнения кэша синхронизации
            String sql = "SELECT id, global_id, last_sync_at, updated_at FROM areas WHERE global_id IS NOT NULL AND global_id != '' AND deleted = FALSE";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                int loaded = 0;
                int synced = 0;
                
                while (rs.next()) {
                    int areaId = rs.getInt("id");
                    String uuid = rs.getString("global_id");
                    
                    if (uuid != null && !uuid.isEmpty()) {
                        uuidToAreaId.put(uuid, areaId);
                        loaded++;
                        
                        // ВАЖНО: Загружаем last_sync_at - если он есть, значит зона была синхронизирована
                        // НЕ используем fallback на updated_at, так как это приведет к удалению новых зон
                        // Новые зоны имеют UUID, но не имеют last_sync_at, поэтому они не должны попадать в syncedZones
                        java.sql.Timestamp lastSyncAt = rs.getTimestamp("last_sync_at");
                        if (lastSyncAt != null) {
                            // Используем last_sync_at как время последней синхронизации
                            // Только зоны с last_sync_at считаются синхронизированными
                            syncedZones.put(uuid, lastSyncAt.getTime());
                            synced++;
                        }
                        // Если last_sync_at нет - зона не была синхронизирована, не добавляем в syncedZones
                    }
                }
                
                System.out.println("AreaSyncManager: Loaded " + loaded + " UUID mappings from DB, " + synced + " zones marked as synced");
            }
        } catch (SQLException e) {
            System.err.println("AreaSyncManager: Failed to load UUID mapping: " + e.getMessage());
            e.printStackTrace();
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
    
    /**
     * Обновляет last_sync_at в БД для зоны после успешной синхронизации
     */
    private void updateLastSyncAt(String uuid, long syncTime, AreaDBManager dbManager) {
        if (uuid == null || uuid.isEmpty() || dbManager == null) {
            return;
        }
        
        try {
            // Получаем connection pool из AreaDBManager
            nurgling.areas.storage.DatabaseConnectionManager poolManager = dbManager.getPoolManager();
            
            if (poolManager == null) {
                return;
            }
            
            java.sql.Connection conn = poolManager.getConnection();
            String sql = "UPDATE areas SET last_sync_at = ? WHERE global_id = ?";
            
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                java.sql.Timestamp timestamp = new java.sql.Timestamp(syncTime);
                stmt.setTimestamp(1, timestamp);
                stmt.setString(2, uuid);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            // Не критично, просто логируем
            System.err.println("AreaSyncManager: Failed to update last_sync_at for zone " + uuid + ": " + e.getMessage());
        }
    }
    
    /**
     * Обновляет визуальное отображение зон в игре после синхронизации
     */
    private void refreshVisualZones() {
        try {
            // Проверяем, что игра запущена
            if (nurgling.NUtils.getGameUI() == null || nurgling.NUtils.getGameUI().map == null) {
                return;
            }
            
            nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
            
            // Загружаем актуальные зоны из БД
            Map<Integer, nurgling.areas.NArea> dbAreas = new HashMap<>();
            try {
                nurgling.areas.db.AreaDBManager areaManager = nurgling.areas.db.AreaDBManager.getInstance();
                dbAreas = areaManager.loadAllAreas();
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Failed to load areas for visual refresh: " + e.getMessage());
                return;
            }
            
            // Синхронизируем glob.map.areas с БД
            synchronized (mapView.glob.map.areas) {
                // Удаляем зоны, которых нет в БД
                Set<Integer> toRemove = new HashSet<>();
                for (Integer areaId : mapView.glob.map.areas.keySet()) {
                    if (!dbAreas.containsKey(areaId)) {
                        toRemove.add(areaId);
                    }
                }
                
                for (Integer areaId : toRemove) {
                    removeVisualZone(mapView, areaId);
                }
                
                // Добавляем/обновляем зоны из БД
                for (Map.Entry<Integer, nurgling.areas.NArea> entry : dbAreas.entrySet()) {
                    Integer areaId = entry.getKey();
                    nurgling.areas.NArea dbArea = entry.getValue();
                    
                    nurgling.areas.NArea existingArea = mapView.glob.map.areas.get(areaId);
                    
                    if (existingArea == null) {
                        // Новая зона - добавляем
                        mapView.glob.map.areas.put(areaId, dbArea);
                        mapView.createAreaLabel(areaId);
                        System.out.println("AreaSyncManager: Added zone " + areaId + " (" + dbArea.name + ") to visual display");
                    } else {
                        // Существующая зона - обновляем данные
                        updateAreaData(existingArea, dbArea);
                        
                        // Пересоздаем overlay если нужно
                        nurgling.overlays.map.NOverlay nol = mapView.nols.get(areaId);
                        if (nol != null) {
                            nol.remove();
                            mapView.nols.remove(areaId);
                        }
                        mapView.createAreaLabel(areaId);
                        
                        System.out.println("AreaSyncManager: Updated zone " + areaId + " (" + dbArea.name + ") in visual display");
                    }
                }
            }
            
            // Обновляем areas widget
            if (nurgling.NUtils.getGameUI().areas != null) {
                nurgling.NConfig.needAreasUpdate();
            }
            
            // ВАЖНО: Убеждаемся, что все зоны имеют визуальное отображение
            // Вызываем initDummys() для создания overlays для всех зон, которые еще не имеют их
            mapView.initDummys();
            
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to refresh visual zones: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Обновляет данные зоны из БД
     */
    private void updateAreaData(nurgling.areas.NArea existing, nurgling.areas.NArea fromDB) {
        existing.name = fromDB.name;
        existing.path = fromDB.path;
        existing.color = fromDB.color;
        existing.hide = fromDB.hide;
        existing.uuid = fromDB.uuid;
        existing.zoneSync = fromDB.zoneSync;
        existing.lastUpdated = fromDB.lastUpdated;
        existing.synced = fromDB.synced;
        
        // Обновляем space если изменился
        if (fromDB.space != null && fromDB.space.space != null) {
            existing.space = fromDB.space;
        }
        
        // Обновляем spec, jin, jout если изменились
        if (fromDB.spec != null) {
            existing.spec = fromDB.spec;
        }
        if (fromDB.jin != null) {
            existing.jin = fromDB.jin;
        }
        if (fromDB.jout != null) {
            existing.jout = fromDB.jout;
        }
    }
    
    /**
     * Удаляет визуальное отображение конкретной зоны
     */
    private void updateVisualZone(int areaId) {
        try {
            // Проверяем, что игра запущена
            if (nurgling.NUtils.getGameUI() == null || nurgling.NUtils.getGameUI().map == null) {
                return;
            }
            
            nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
            
            synchronized (mapView.glob.map.areas) {
                removeVisualZone(mapView, areaId);
            }
            
            // Обновляем areas widget
            if (nurgling.NUtils.getGameUI().areas != null) {
                nurgling.NConfig.needAreasUpdate();
            }
            
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to update visual zone: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Удаляет визуальное отображение зоны из mapView
     */
    private void removeVisualZone(nurgling.NMapView mapView, int areaId) {
        nurgling.areas.NArea area = mapView.glob.map.areas.get(areaId);
        if (area != null) {
            // Удаляем overlay
            nurgling.overlays.map.NOverlay nol = mapView.nols.get(areaId);
            if (nol != null) {
                nol.remove();
                mapView.nols.remove(areaId);
            }
            
            // Удаляем dummy
            if (area.gid != Long.MIN_VALUE) {
                haven.Gob dummy = mapView.dummys.get(area.gid);
                if (dummy != null) {
                    mapView.glob.oc.remove(dummy);
                    mapView.dummys.remove(area.gid);
                }
            }
            
            // Удаляем из areas widget
            if (nurgling.NUtils.getGameUI().areas != null) {
                nurgling.NUtils.getGameUI().areas.removeArea(areaId);
            }
            
            // Удаляем из glob.map.areas
            mapView.glob.map.areas.remove(areaId);
            
            System.out.println("AreaSyncManager: Removed zone " + areaId + " from visual display");
        }
    }
}
