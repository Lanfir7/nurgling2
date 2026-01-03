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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> delayedSyncTask;
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
        // Создаем ScheduledExecutorService для отложенных задач
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AreaSyncScheduledThread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Останавливает ExecutorService (вызывается при завершении работы)
     */
    public void shutdown() {
        if (delayedSyncTask != null && !delayedSyncTask.isDone()) {
            delayedSyncTask.cancel(false);
        }
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
        }
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
            
            // Планируем отложенную синхронизацию через 10 секунд после старта
            scheduleDelayedSync();
        } else {
            this.enabled = false;
            System.out.println("AreaSyncManager: Sync disabled (server URL or zone_sync not set)");
        }
    }
    
    /**
     * Планирует отложенную синхронизацию через 10 секунд после старта
     */
    private void scheduleDelayedSync() {
        if (delayedSyncTask != null && !delayedSyncTask.isDone()) {
            delayedSyncTask.cancel(false);
        }
        
        delayedSyncTask = scheduledExecutor.schedule(() -> {
            try {
                System.out.println("AreaSyncManager: Starting delayed sync (10 seconds after startup)");
                nurgling.NGameUI gui = nurgling.NUtils.getGameUI();
                if (gui != null && gui.map != null && gui.map.glob != null && gui.map.glob.map != null) {
                    Collection<NArea> localAreas = gui.map.glob.map.areas.values();
                    nurgling.areas.db.AreaDBManager dbManager = nurgling.areas.db.AreaDBManager.getInstance();
                    syncAll(localAreas, dbManager);
                } else {
                    System.out.println("AreaSyncManager: Delayed sync skipped - game UI not ready");
                }
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Error during delayed sync: " + e.getMessage());
                e.printStackTrace();
            }
        }, 10, TimeUnit.SECONDS);
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
        
        long syncStartTime = System.currentTimeMillis();
        System.out.println("AreaSyncManager: Sync started");
        
        // Загружаем актуальные данные из БД перед синхронизацией
        // Это гарантирует, что у нас актуальные значения lastUpdated
        Collection<NArea> freshAreas = new ArrayList<>();
        try {
            Map<Integer, NArea> dbAreas = dbManager.loadAllAreas();
            freshAreas = dbAreas.values();
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to load fresh areas from DB, using provided areas: " + e.getMessage());
            e.printStackTrace();
            freshAreas = localAreas; // Fallback на переданные зоны
        }
        
        // ВАЖНО: Сначала получаем обновления с сервера (pull),
        // чтобы удалить зоны, которые были удалены на сервере.
        // Это предотвращает отправку зон, которые уже удалены на сервере.
        int[] pullStats = pullServerUpdates(freshAreas, dbManager);
        
        // После получения обновлений с сервера, загружаем зоны снова,
        // так как некоторые зоны могли быть удалены
        try {
            Map<Integer, NArea> dbAreas = dbManager.loadAllAreas();
            freshAreas = dbAreas.values();
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to reload areas from DB after pull: " + e.getMessage());
        }
        
        // Теперь отправляем локальные изменения (push)
        int[] pushStats = pushLocalChanges(freshAreas, dbManager);
        
        // ВАЖНО: Обновляем glob.map.areas и виджет зон после синхронизации
        // Делаем это безопасно, через SwingUtilities.invokeLater для выполнения в UI потоке
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updateAreasInMemory();
            }
        });
        
        lastSyncTime = System.currentTimeMillis();
        long syncDuration = (lastSyncTime - syncStartTime) / 1000;
        System.out.println("AreaSyncManager: Sync completed, took " + syncDuration + " seconds. " +
                         "Added: " + pullStats[0] + ", updated: " + pullStats[1] + ", sent: " + pushStats[0]);
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
     * @return массив [отправлено, пропущено]
     */
    private int[] pushLocalChanges(Collection<NArea> localAreas, AreaDBManager dbManager) {
        if (localAreas == null || localAreas.isEmpty()) {
            return new int[]{0, 0};
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
                skipped++;
                continue;
            }
            
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
        
        return new int[]{pushed, skipped};
    }
    
    /**
     * Получает обновления с сервера и применяет их
     * @return массив [создано, обновлено]
     */
    private int[] pullServerUpdates(Collection<NArea> localAreas, AreaDBManager dbManager) {
        // ВАЖНО: Для обнаружения удаленных зон нужно запросить ВСЕ зоны с сервера,
        // а не только обновленные после lastSyncTime. Иначе удаленные зоны не будут обнаружены.
        // Используем updatedAfter = 0, чтобы получить все активные зоны
        long updatedAfter = 0; // Запрашиваем все зоны для проверки удалений
        
        // Получаем зоны с сервера
        List<NArea> serverZones = syncClient.pullZones(updatedAfter);
        
        // Получаем список UUID зон, удалённых на сервере
        List<String> deletedUuids = syncClient.getLastDeletedUuids();
        
        // ВАЖНО: Если сервер вернул null или пустой список, это может быть ошибка запроса
        // В этом случае НЕ удаляем локальные зоны, так как мы не знаем, действительно ли они удалены на сервере
        boolean serverRequestFailed = false;
        if (serverZones == null) {
            System.err.println("AreaSyncManager: Server returned null zones list - skipping deletion check to prevent data loss");
            serverZones = new ArrayList<>();
            serverRequestFailed = true;
        } else if (serverZones.isEmpty() && !localAreas.isEmpty() && (deletedUuids == null || deletedUuids.isEmpty())) {
            // Если сервер вернул пустой список И нет удалённых зон, но у нас есть локальные зоны - это подозрительно
            // Может быть ошибка запроса или сервер действительно пуст
            // ВАЖНО: Не удаляем зоны, если запрос мог быть неудачным
            System.err.println("AreaSyncManager: WARNING - Server returned empty zones list but local areas exist. " +
                             "This may indicate a server error. Skipping deletion check to prevent data loss.");
            serverRequestFailed = true;
        }
        
        // ВАЖНО: Обрабатываем зоны, удалённые на сервере (deleted=true)
        // Это гарантирует, что удаления синхронизируются между всеми клиентами
        int deletedFromServer = 0;
        if (deletedUuids != null && !deletedUuids.isEmpty() && !serverRequestFailed) {
            for (String deletedUuid : deletedUuids) {
                NArea localZone = null;
                for (NArea area : localAreas) {
                    if (deletedUuid.equals(area.uuid)) {
                        localZone = area;
                        break;
                    }
                }
                
                if (localZone != null) {
                    // Зона найдена локально, но удалена на сервере - удаляем локально
                    try {
                        System.out.println("AreaSyncManager: Deleting zone " + localZone.id + " (" + localZone.name + 
                                         ") because it was deleted on server (UUID: " + deletedUuid + ")");
                        dbManager.deleteArea(localZone.id, true); // skipServerSync = true
                        syncedZones.remove(deletedUuid);
                        uuidToAreaId.remove(deletedUuid);
                        deletedFromServer++;
                        
                        // Удаляем визуальные элементы через SwingUtilities.invokeLater
                        final int zoneIdToRemove = localZone.id;
                        final NArea zoneToRemove = localZone;
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            try {
                                if (nurgling.NUtils.getGameUI() != null && nurgling.NUtils.getGameUI().map != null) {
                                    nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
                                    removeVisualZone(mapView, zoneIdToRemove, zoneToRemove);
                                }
                            } catch (Exception e) {
                                System.err.println("AreaSyncManager: Failed to remove visual zone " + zoneIdToRemove + ": " + e.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("AreaSyncManager: Failed to delete zone " + localZone.id + 
                                         " that was deleted on server: " + e.getMessage());
                    }
                }
            }
            if (deletedFromServer > 0) {
                System.out.println("AreaSyncManager: Deleted " + deletedFromServer + " zones that were deleted on server");
            }
        }
        
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
        // ВАЖНО: Отслеживаем созданные ID в текущей синхронизации,
        // чтобы не присваивать одинаковые ID нескольким новым зонам
        Set<Integer> createdIds = new HashSet<>();
        int maxExistingId = 0;
        for (NArea area : localAreas) {
            if (area.id > maxExistingId) {
                maxExistingId = area.id;
            }
        }
        
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
                // ВАЖНО: Проверяем, не была ли зона удалена локально
                // Если да - НЕ создаём её заново, а отправляем команду удаления на сервер
                boolean wasLocallyDeleted = isLocallyDeleted(serverZone.uuid);
                System.out.println("AreaSyncManager.pullServerUpdates: Zone UUID=" + serverZone.uuid + 
                                 " not found locally. isLocallyDeleted=" + wasLocallyDeleted + 
                                 ", locallyDeletedZones.size=" + locallyDeletedZones.size());
                if (wasLocallyDeleted) {
                    System.out.println("AreaSyncManager.pullServerUpdates: BLOCKING zone resurrection (UUID: " + serverZone.uuid + 
                                     ", name: " + serverZone.name + ") - was deleted locally, sending DELETE to server");
                    // Отправляем DELETE на сервер, чтобы удалить зону там тоже
                    boolean deleteSuccess = syncClient.deleteZone(serverZone.uuid);
                    if (deleteSuccess) {
                        System.out.println("AreaSyncManager: Successfully deleted resurrected zone on server: " + serverZone.uuid);
                        locallyDeletedZones.remove(serverZone.uuid);
                        syncedZones.remove(serverZone.uuid);
                    } else {
                        System.err.println("AreaSyncManager: Failed to delete resurrected zone on server: " + serverZone.uuid);
                    }
                    skipped++;
                    continue;
                }
                
                // Новая зона с сервера - создаем локально
                // ВАЖНО: Находим свободный ID, учитывая уже созданные зоны в этой синхронизации
                int newId = maxExistingId + 1;
                // Проверяем, не занят ли ID
                boolean idExists = createdIds.contains(newId);
                if (!idExists) {
                    for (NArea area : localAreas) {
                        if (area.id == newId) {
                            idExists = true;
                            break;
                        }
                    }
                }
                while (idExists) {
                    newId++;
                    idExists = createdIds.contains(newId);
                    if (!idExists) {
                        for (NArea area : localAreas) {
                            if (area.id == newId) {
                                idExists = true;
                                break;
                            }
                        }
                    }
                }
                final int finalNewId = newId; // Делаем final для использования в лямбде
                serverZone.id = finalNewId;
                createdIds.add(finalNewId);
                if (finalNewId > maxExistingId) {
                    maxExistingId = finalNewId;
                }
                serverZone.synced = true;
                
                // ВАЖНО: Применяем локальный hide статус для новых зон с сервера
                // По умолчанию все зоны с сервера скрыты, если не в списке разрешённых
                nurgling.areas.AllowedZonesManager.getInstance().applyLocalHideStatus(serverZone);
                System.out.println("AreaSyncManager: New zone from server (UUID: " + serverZone.uuid + 
                                 ", name: " + serverZone.name + ") - hide=" + serverZone.hide);
                
                try {
                    // ВАЖНО: Используем saveAreaNoThrottle, чтобы гарантировать сохранение,
                    // даже если есть throttling
                    dbManager.saveAreaNoThrottle(serverZone);
                    uuidToAreaId.put(serverZone.uuid, serverZone.id);
                    syncedZones.put(serverZone.uuid, serverZone.lastUpdated);
                    
                    // ВАЖНО: НЕ добавляем в localAreas, так как это может быть неизменяемая коллекция
                    // Вместо этого используем createdIds для отслеживания созданных ID
                    
                    // Сохраняем last_sync_at в БД
                    updateLastSyncAt(serverZone.uuid, serverZone.lastUpdated, dbManager);
                    
                    created++;
                } catch (Exception e) {
                    System.err.println("AreaSyncManager: Failed to create zone from server (UUID: " + 
                                     (serverZone.uuid != null ? serverZone.uuid : "unknown") + 
                                     ", attempted ID: " + finalNewId + "): " + e.getMessage());
                    e.printStackTrace();
                    // ВАЖНО: Продолжаем обработку остальных зон, даже если одна не создалась
                    // Освобождаем ID, чтобы он мог быть использован снова
                    createdIds.remove(finalNewId);
                    skipped++;
                    // Продолжаем цикл для следующей зоны
                    continue;
                }
            } else {
                // Зона существует локально - разрешаем конфликт
                if (resolveConflict(localZone, serverZone, dbManager)) {
                    merged++;
                    // ВАЖНО: После merge нужно обновить зону в glob.map.areas,
                    // чтобы виджет увидел изменения (например, новое имя)
                    updateZoneInMemory(localZone.id, localZone);
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
        // ВАЖНО: Не удаляем зоны, если запрос к серверу мог быть неудачным
        // Это предотвращает случайное удаление зон при ошибках сети или сервера
        if (!serverRequestFailed) {
            for (NArea localZone : localAreas) {
                if (localZone.uuid != null && !localZone.uuid.isEmpty()) {
                    // ВАЖНО: Проверяем, была ли зона синхронизирована ранее
                    // Зона считается синхронизированной ТОЛЬКО если она есть в syncedZones
                    // (т.е. имеет last_sync_at в БД, что означает, что она была успешно отправлена на сервер)
                    // НЕ проверяем uuidToAreaId, так как он содержит все зоны с UUID, даже несинхронизированные
                    boolean wasSynced = syncedZones.containsKey(localZone.uuid);
                    boolean inServerUuids = serverUuids.contains(localZone.uuid);
                    
                    // Если зона была синхронизирована (имеет last_sync_at), но её нет на сервере - значит она удалена на сервере
                    // НЕ удаляем зоны, которые никогда не синхронизировались (они просто еще не были отправлены)
                    // ВАЖНО: Проверяем также, что зона действительно была отправлена на сервер (имеет last_sync_at)
                    // Это предотвращает удаление зон, которые были созданы локально, но еще не синхронизированы
                    if (wasSynced && !inServerUuids) {
                    // Зона была удалена на сервере - удаляем её локально
                    // ВАЖНО: Не отправляем команду удаления на сервер, так как зона уже удалена на сервере
                    try {
                        dbManager.deleteArea(localZone.id, true); // skipServerSync = true
                        syncedZones.remove(localZone.uuid);
                        uuidToAreaId.remove(localZone.uuid);
                        deleted++;
                        
                        // ВАЖНО: Удаляем визуальные элементы через SwingUtilities.invokeLater
                        // для безопасного выполнения в UI потоке
                        final int zoneIdToRemove = localZone.id;
                        final nurgling.areas.NArea zoneToRemove = localZone;
                        javax.swing.SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (nurgling.NUtils.getGameUI() != null && nurgling.NUtils.getGameUI().map != null) {
                                        nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
                                        removeVisualZone(mapView, zoneIdToRemove, zoneToRemove);
                                    }
                                } catch (Exception e) {
                                    System.err.println("AreaSyncManager: Failed to remove visual zone " + zoneIdToRemove + ": " + e.getMessage());
                                }
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("AreaSyncManager: Failed to delete zone " + localZone.id + 
                                         " that was deleted on server: " + e.getMessage());
                        e.printStackTrace();
                    }
                    }
                }
            }
        } else {
            System.out.println("AreaSyncManager: Skipping zone deletion check due to potential server request failure");
        }
        
        return new int[]{created, merged};
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
            
            // Сохраняем оригинальное lastUpdated с сервера перед merge
            long serverLastUpdated = server.lastUpdated;
            
            mergeZoneData(local, server);
            
            
            // ВАЖНО: После merge устанавливаем synced = false, чтобы saveArea() правильно определил изменения
            // Это гарантирует, что hasAreaChanged() обнаружит изменения и обновит ВСЕ поля в БД
            // (имя, цвет, координаты, специализации и т.д.)
            local.synced = false;
            
            try {
                // Сохраняем зону в БД - это обновит ВСЕ поля (имя, цвет, координаты, специализации)
                // ВАЖНО: сохраняем без троттлинга, чтобы не пропустить изменения с сервера
                // saveArea() установит lastUpdated = System.currentTimeMillis() при hasChanges=true
                dbManager.saveAreaNoThrottle(local);
                
                // ВАЖНО: Восстанавливаем server.lastUpdated после сохранения,
                // так как saveArea() установил System.currentTimeMillis() при hasChanges=true
                // Но мы хотим сохранить время с сервера для правильной синхронизации
                local.lastUpdated = serverLastUpdated;
                local.synced = true;
                
                // ВАЖНО: Обновляем updated_at в БД напрямую, чтобы сохранить server.lastUpdated
                // saveArea() уже обновил все поля (имя, цвет, координаты, специализации),
                // нам нужно только обновить timestamp
                updateAreaTimestamp(local.id, serverLastUpdated, dbManager);
                
                syncedZones.put(local.uuid, local.lastUpdated);
                
                // Сохраняем last_sync_at в БД
                updateLastSyncAt(local.uuid, local.lastUpdated, dbManager);
                
                return true;
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Failed to merge zone: " + e.getMessage());
                e.printStackTrace();
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
        // ВАЖНО: hide - локальный параметр, не синхронизируется с сервером
        // local.hide сохраняется локально для каждого игрока
        local.lastUpdated = server.lastUpdated;
        local.zoneSync = server.zoneSync;
        local.synced = true;
        
        // Обновляем пространственные данные (если есть)
        if (server.space != null && server.space.space != null && !server.space.space.isEmpty()) {
            local.space = server.space;
        }
        // ВАЖНО: grids_id должен соответствовать space.space, иначе MCache#getnolcut может попытаться
        // построить меш для grid_id, которого нет в space (и упадёт в NOverlay.makenol).
        local.syncGridIdsFromSpace();
        
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
     * Проверяет, была ли зона синхронизирована с сервером
     * @param uuid UUID зоны
     * @return true если зона была синхронизирована (имеет last_sync_at в БД)
     */
    public boolean isZoneSynced(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        return syncedZones.containsKey(uuid);
    }
    
    /**
     * Удаляет зону на сервере
     */
    public void deleteZone(NArea area) {
        if (!isEnabled()) {
            System.out.println("AreaSyncManager.deleteZone: Zone synchronization is disabled, skipping deletion");
            return;
        }
        
        if (area == null) {
            System.err.println("AreaSyncManager.deleteZone: WARNING - Cannot delete zone: area is null");
            return;
        }
        
        if (area.uuid == null || area.uuid.isEmpty()) {
            System.err.println("AreaSyncManager.deleteZone: WARNING - Cannot delete zone " + area.id + 
                             " (" + (area.name != null ? area.name : "unknown") + "): UUID is null or empty. " +
                             "Zone will be deleted locally only.");
            return;
        }
        
        System.out.println("AreaSyncManager.deleteZone: DELETING zone " + area.id + " (" + 
                         (area.name != null ? area.name : "unknown") + ") on server (UUID: " + area.uuid + ")");
        
        // ВАЖНО: Сразу помечаем зону как удалённую локально,
        // чтобы она не воскресла при следующей синхронизации даже если DELETE не прошёл на сервер
        markAsLocallyDeleted(area.uuid);
        System.out.println("AreaSyncManager.deleteZone: Marked zone " + area.uuid + " as locally deleted. Total deleted: " + locallyDeletedZones.size());
        
        boolean success = syncClient.deleteZone(area.uuid);
        if (success) {
            System.out.println("AreaSyncManager.deleteZone: Successfully deleted zone " + area.id + " on server");
            syncedZones.remove(area.uuid);
            uuidToAreaId.remove(area.uuid);
            // НЕ удаляем из locallyDeletedZones - оставляем для предотвращения воскрешения
            // locallyDeletedZones.remove(area.uuid);
            System.out.println("AreaSyncManager.deleteZone: Zone " + area.uuid + " remains in locallyDeletedZones for protection");
        } else {
            System.err.println("AreaSyncManager.deleteZone: Failed to delete zone " + area.id + " on server - zone marked for retry");
            // Не удаляем из locallyDeletedZones - при следующей синхронизации попробуем удалить снова
        }
    }
    
    /**
     * Загружает UUID зон из БД для предотвращения дублей
     * Загружает syncedZones из last_sync_at, чтобы знать, какие зоны уже синхронизированы
     */
    // Хранит UUID зон, удалённых локально (soft delete), чтобы не создавать их заново при синхронизации
    private final ConcurrentHashMap<String, Long> locallyDeletedZones = new ConcurrentHashMap<>();
    
    /**
     * Проверяет, была ли зона удалена локально
     */
    public boolean isLocallyDeleted(String uuid) {
        return uuid != null && locallyDeletedZones.containsKey(uuid);
    }
    
    /**
     * Помечает зону как удалённую локально (чтобы не создавать заново при синхронизации)
     */
    public void markAsLocallyDeleted(String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            locallyDeletedZones.put(uuid, System.currentTimeMillis());
        }
    }
    
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
            // ВКЛЮЧАЕМ deleted зоны для активных зон (uuidToAreaId) и синхронизации
            String sql = "SELECT id, global_id, last_sync_at, updated_at, deleted FROM areas WHERE global_id IS NOT NULL AND global_id != ''";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                int loaded = 0;
                int synced = 0;
                int deletedLoaded = 0;
                
                while (rs.next()) {
                    int areaId = rs.getInt("id");
                    String uuid = rs.getString("global_id");
                    boolean isDeleted = rs.getBoolean("deleted");
                    
                    if (uuid != null && !uuid.isEmpty()) {
                        // ВАЖНО: Для УДАЛЁННЫХ зон - добавляем в locallyDeletedZones, чтобы не создавать заново
                        // Это предотвращает "воскрешение" зон, которые пользователь удалил
                        if (isDeleted) {
                            java.sql.Timestamp lastSyncAt = rs.getTimestamp("last_sync_at");
                            if (lastSyncAt != null) {
                                // Зона была синхронизирована и удалена локально
                                locallyDeletedZones.put(uuid, lastSyncAt.getTime());
                                syncedZones.put(uuid, lastSyncAt.getTime()); // Важно для isZoneSynced()
                                deletedLoaded++;
                            }
                            continue; // Не добавляем в uuidToAreaId (зона удалена)
                        }
                        
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
                
                System.out.println("AreaSyncManager: Loaded " + loaded + " UUID mappings from DB, " + synced + " zones marked as synced, " + deletedLoaded + " locally deleted zones tracked");
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
     * Обновляет updated_at для зоны в БД напрямую
     * (все остальные поля уже обновлены через saveArea())
     */
    private void updateAreaTimestamp(int areaId, long timestamp, AreaDBManager dbManager) {
        try {
            nurgling.areas.storage.DatabaseConnectionManager poolManager = dbManager.getPoolManager();
            java.sql.Connection conn = poolManager.getConnection();
            if (conn != null) {
                try {
                    String sql = "UPDATE areas SET updated_at = ? WHERE id = ?";
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                        java.sql.Timestamp ts = new java.sql.Timestamp(timestamp);
                        stmt.setTimestamp(1, ts);
                        stmt.setInt(2, areaId);
                        int rowsUpdated = stmt.executeUpdate();
                        if (rowsUpdated > 0) {
                            System.out.println("AreaSyncManager: Updated zone " + areaId + " timestamp to " + timestamp + " in DB");
                        } else {
                            System.err.println("AreaSyncManager: WARNING - Zone " + areaId + " not found for timestamp update");
                        }
                    }
                    conn.commit();
                } finally {
                    conn.close();
                }
            }
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to update timestamp for zone " + areaId + ": " + e.getMessage());
            e.printStackTrace();
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
     * Обновляет одну зону в памяти (glob.map.areas) после merge
     * ВАЖНО: Выполняется на UI потоке через SwingUtilities.invokeLater()
     */
    private void updateZoneInMemory(int areaId, NArea updatedArea) {
        // Выполняем на UI потоке, так как обновляем визуальные элементы
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    if (nurgling.NUtils.getGameUI() == null || nurgling.NUtils.getGameUI().map == null) {
                        return;
                    }
                    
                    nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
                    
                    synchronized (mapView.glob.map.areas) {
                        nurgling.areas.NArea existingArea = mapView.glob.map.areas.get(areaId);
                        if (existingArea != null) {
                            // Обновляем данные существующей зоны
                            updateAreaData(existingArea, updatedArea);
                            
                            // ВАЖНО: Пересоздаем визуальные элементы (overlay и dummy),
                            // так как могли измениться координаты (space) или цвет
                            // Это делается так же, как при локальном изменении цвета или координат
                            synchronized (mapView.nols) {
                                // Удаляем старый overlay
                                nurgling.overlays.map.NOverlay nol = mapView.nols.get(areaId);
                                if (nol != null) {
                                    nol.remove();
                                    mapView.nols.remove(areaId);
                                }
                            }
                            
                            // Удаляем старый dummy (если координаты изменились, нужно пересоздать dummy)
                            if (existingArea.gid != Long.MIN_VALUE) {
                                haven.Gob dummy = mapView.dummys.get(existingArea.gid);
                                if (dummy != null) {
                                    mapView.glob.oc.remove(dummy);
                                    mapView.dummys.remove(existingArea.gid);
                                }
                                existingArea.gid = Long.MIN_VALUE; // Сбрасываем gid для пересоздания
                            }
                            
                            // Создаем новые визуальные элементы с обновленными данными
                            synchronized (mapView.nols) {
                                mapView.createAreaLabel(areaId);
                            }
                            
                        } else {
                            // Зона не найдена в памяти - добавляем
                            mapView.glob.map.areas.put(areaId, updatedArea);
                            
                            // Создаем визуальные элементы для новой зоны
                            synchronized (mapView.nols) {
                                mapView.createAreaLabel(areaId);
                            }
                            
                            // Подключаем к графу маршрутов
                            mapView.routeGraphManager.getGraph().connectAreaToRoutePoints(updatedArea);
                            
                        }
                    }
                    
            // Обновляем виджет зон, если он открыт
            // ВАЖНО: Не перезагружаем весь список, только обновляем существующие элементы
            if (nurgling.NUtils.getGameUI().areas != null && nurgling.NUtils.getGameUI().areas.visible()) {
                nurgling.NConfig.needAreasUpdate();
                // Обновляем только текущий путь без полной перезагрузки
                nurgling.NUtils.getGameUI().areas.refreshCurrentPath();
            }
                    
                } catch (Exception e) {
                    System.err.println("AreaSyncManager: Failed to update zone in memory: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Обновляет зоны в памяти (glob.map.areas) и виджет после синхронизации
     * Выполняется безопасно, без длительной блокировки UI потока
     */
    private void updateAreasInMemory() {
        try {
            System.out.println("AreaSyncManager.updateAreasInMemory: Starting memory update");
            // Проверяем, что игра запущена
            if (nurgling.NUtils.getGameUI() == null || nurgling.NUtils.getGameUI().map == null) {
                System.out.println("AreaSyncManager.updateAreasInMemory: GameUI or map is null, skipping");
                return;
            }
            
            nurgling.NMapView mapView = (nurgling.NMapView) nurgling.NUtils.getGameUI().map;
            System.out.println("AreaSyncManager.updateAreasInMemory: MapView found, areas in memory: " + mapView.glob.map.areas.size() + ", nols.size: " + mapView.nols.size());
            
            // Загружаем актуальные зоны из БД
            Map<Integer, nurgling.areas.NArea> dbAreas = new HashMap<>();
            try {
                nurgling.areas.db.AreaDBManager areaManager = nurgling.areas.db.AreaDBManager.getInstance();
                dbAreas = areaManager.loadAllAreas();
            } catch (Exception e) {
                System.err.println("AreaSyncManager: Failed to load areas for memory update: " + e.getMessage());
                return;
            }
            
            // Обновляем glob.map.areas (краткая блокировка)
            List<Integer> newZoneIds = new ArrayList<>();
            synchronized (mapView.glob.map.areas) {
                // Добавляем/обновляем зоны из БД
                for (Map.Entry<Integer, nurgling.areas.NArea> entry : dbAreas.entrySet()) {
                    Integer areaId = entry.getKey();
                    nurgling.areas.NArea dbArea = entry.getValue();
                    
                    // ВАЖНО: Применяем локальный hide статус ПЕРЕД добавлением в память
                    nurgling.areas.AllowedZonesManager.getInstance().applyLocalHideStatus(dbArea);
                    
                    nurgling.areas.NArea existingArea = mapView.glob.map.areas.get(areaId);
                    if (existingArea == null) {
                        // Новая зона - добавляем в память
                        mapView.glob.map.areas.put(areaId, dbArea);
                        newZoneIds.add(areaId);
                    } else {
                        // Существующая зона - обновляем данные (hide НЕ обновляется из БД)
                        updateAreaData(existingArea, dbArea);
                    }
                }
                
                // Удаляем зоны, которых нет в БД
                Set<Integer> toRemove = new HashSet<>();
                for (Integer areaId : mapView.glob.map.areas.keySet()) {
                    if (!dbAreas.containsKey(areaId)) {
                        toRemove.add(areaId);
                    }
                }
                // Сохраняем список зон для удаления, чтобы удалить их визуальные элементы
                final java.util.List<Integer> zonesToRemoveVisual = new java.util.ArrayList<>(toRemove);
                
                for (Integer areaId : toRemove) {
                    nurgling.areas.NArea areaToRemove = mapView.glob.map.areas.get(areaId);
                    
                    // Удаляем из памяти
                    mapView.glob.map.areas.remove(areaId);
                }
                
                // Удаляем визуальные элементы для всех удаленных зон
                // Это нужно делать после удаления из памяти, чтобы получить данные о зонах
                for (Integer areaId : zonesToRemoveVisual) {
                    // Загружаем данные о зоне из БД перед удалением (если еще доступны)
                    // или используем данные из памяти, если они еще есть
                    nurgling.areas.NArea areaToRemove = null;
                    synchronized (mapView.glob.map.areas) {
                        // Пытаемся найти зону в памяти (может быть уже удалена)
                        for (nurgling.areas.NArea area : mapView.glob.map.areas.values()) {
                            if (area.id == areaId) {
                                areaToRemove = area;
                                break;
                            }
                        }
                    }
                    
                    // Если зона не найдена в памяти, пытаемся загрузить из БД
                    if (areaToRemove == null) {
                        try {
                            nurgling.areas.db.AreaDBManager areaManager = nurgling.areas.db.AreaDBManager.getInstance();
                            areaToRemove = areaManager.getArea(areaId);
                        } catch (Exception e) {
                            // Игнорируем ошибки загрузки
                        }
                    }
                    
                    // Удаляем визуальные элементы
                    removeVisualZone(mapView, areaId, areaToRemove);
                }
            }
            
            // Список новых/обновленных зон для подключения к роутам в отдельном потоке
            final List<nurgling.areas.NArea> zonesToConnect = new ArrayList<>();
            
            // ВАЖНО: Для новых зон выполняем те же действия, что и при создании через UI
            // Это обеспечивает правильное отображение и подключение к графу маршрутов
            for (Integer newZoneId : newZoneIds) {
                nurgling.areas.NArea newZone = mapView.glob.map.areas.get(newZoneId);
                if (newZone != null) {
                    // Создаем визуальное отображение (как в addArea)
                    synchronized (mapView.nols) {
                        mapView.createAreaLabel(newZoneId);
                    }
                    
                    // Добавляем в список для подключения к роутам в отдельном потоке
                    zonesToConnect.add(newZone);
                }
            }
            
            // Подключаем новые/обновленные зоны к роутам в отдельном потоке (чтобы не блокировать UI)
            if (!zonesToConnect.isEmpty()) {
                final List<nurgling.areas.NArea> zonesToConnectFinal = new ArrayList<>(zonesToConnect);
                syncExecutor.submit(() -> {
                    connectZonesToRoutesInBackground(zonesToConnectFinal);
                });
            }
            
            // Обновляем виджет зон, если он открыт
            if (nurgling.NUtils.getGameUI().areas != null) {
                nurgling.NConfig.needAreasUpdate();
                // Обновляем список зон в виджете, если он видим
                if (nurgling.NUtils.getGameUI().areas.visible()) {
                    nurgling.NUtils.getGameUI().areas.showPath(nurgling.NUtils.getGameUI().areas.currentPath);
                }
            }
            
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to update areas in memory: " + e.getMessage());
            e.printStackTrace();
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
            
            // Список новых/обновленных зон для подключения к роутам в отдельном потоке
            final List<nurgling.areas.NArea> zonesToConnect = new ArrayList<>();
            
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
                    // Получаем объект зоны перед удалением из памяти
                    nurgling.areas.NArea areaToRemove = mapView.glob.map.areas.get(areaId);
                    removeVisualZone(mapView, areaId, areaToRemove);
                }
                
                // Добавляем/обновляем зоны из БД
                for (Map.Entry<Integer, nurgling.areas.NArea> entry : dbAreas.entrySet()) {
                    Integer areaId = entry.getKey();
                    nurgling.areas.NArea dbArea = entry.getValue();
                    
                    nurgling.areas.NArea existingArea = mapView.glob.map.areas.get(areaId);
                    
                    if (existingArea == null) {
                        // Новая зона - добавляем
                        // ВАЖНО: Применяем локальный hide статус - по умолчанию все зоны из БД скрыты,
                        // кроме тех что в локальном списке разрешённых
                        nurgling.areas.AllowedZonesManager.getInstance().applyLocalHideStatus(dbArea);
                        
                        System.out.println("AreaSyncManager.updateAreasInMemory: Adding new zone " + areaId + " (" + dbArea.name + ") from sync, hide=" + dbArea.hide);
                        mapView.glob.map.areas.put(areaId, dbArea);
                        // ВАЖНО: Выполняем те же действия, что и при создании через UI
                        // Создаем overlay синхронизированно
                        synchronized (mapView.nols) {
                            System.out.println("AreaSyncManager.updateAreasInMemory: Creating overlay for zone " + areaId + ", nols.size before=" + mapView.nols.size());
                            mapView.createAreaLabel(areaId);
                            System.out.println("AreaSyncManager.updateAreasInMemory: Overlay created for zone " + areaId + ", nols.size after=" + mapView.nols.size() + ", contains=" + mapView.nols.containsKey(areaId));
                        }
                        // Добавляем в список для подключения к роутам в отдельном потоке
                        zonesToConnect.add(dbArea);
                    } else {
                        // Существующая зона - проверяем, была ли она обновлена
                        boolean wasUpdated = updateAreaData(existingArea, dbArea);
                        
                        if (wasUpdated) {
                            System.out.println("AreaSyncManager.updateAreasInMemory: Updating existing zone " + areaId + " (" + dbArea.name + ") from sync");
                            
                            // ВАЖНО: Сбрасываем gid чтобы createAreaLabel() пересоздал dummy и overlay
                            if (existingArea.gid != Long.MIN_VALUE) {
                                haven.Gob dummy = mapView.dummys.get(existingArea.gid);
                                if (dummy != null) {
                                    mapView.glob.oc.remove(dummy);
                                    mapView.dummys.remove(existingArea.gid);
                                }
                                existingArea.gid = Long.MIN_VALUE; // Сбрасываем gid для пересоздания
                            }
                            
                            // Пересоздаем overlay если нужно
                            synchronized (mapView.nols) {
                                System.out.println("AreaSyncManager.updateAreasInMemory: Recreating overlay for zone " + areaId + ", nols.size before=" + mapView.nols.size() + ", contains=" + mapView.nols.containsKey(areaId));
                                nurgling.overlays.map.NOverlay nol = mapView.nols.get(areaId);
                                if (nol != null) {
                                    nol.remove();
                                    mapView.nols.remove(areaId);
                                    System.out.println("AreaSyncManager.updateAreasInMemory: Removed old overlay for zone " + areaId);
                                }
                                mapView.createAreaLabel(areaId);
                                System.out.println("AreaSyncManager.updateAreasInMemory: Overlay recreated for zone " + areaId + ", nols.size after=" + mapView.nols.size() + ", contains=" + mapView.nols.containsKey(areaId));
                            }
                            
                            // Добавляем в список для подключения к роутам в отдельном потоке
                            zonesToConnect.add(existingArea);
                        }
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
            
            // Подключаем новые/обновленные зоны к роутам в отдельном потоке (чтобы не блокировать UI)
            if (!zonesToConnect.isEmpty()) {
                final List<nurgling.areas.NArea> zonesToConnectFinal = new ArrayList<>(zonesToConnect);
                syncExecutor.submit(() -> {
                    connectZonesToRoutesInBackground(zonesToConnectFinal);
                });
            }
            
        } catch (Exception e) {
            System.err.println("AreaSyncManager: Failed to refresh visual zones: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Подключает зоны к роутам в фоновом потоке (чтобы не блокировать UI)
     */
    private void connectZonesToRoutesInBackground(List<nurgling.areas.NArea> zones) {
        try {
            nurgling.NGameUI gui = nurgling.NUtils.getGameUI();
            if (gui == null || gui.map == null) {
                return;
            }
            
            nurgling.NMapView mapView = (nurgling.NMapView) gui.map;
            
            int connectedCount = 0;
            for (nurgling.areas.NArea area : zones) {
                try {
                    // Небольшая задержка между подключениями, чтобы не перегружать систему
                    Thread.sleep(50);
                    
                    mapView.routeGraphManager.getGraph().connectAreaToRoutePoints(area);
                    connectedCount++;
                } catch (Exception e) {
                    System.err.println("AreaSyncManager.connectZonesToRoutesInBackground: Failed to connect zone " + area.id + " (" + area.name + "): " + e.getMessage());
                }
            }
            System.out.println("AreaSyncManager.connectZonesToRoutesInBackground: Connected " + connectedCount + " zones to route points in background");
        } catch (Exception e) {
            System.err.println("AreaSyncManager.connectZonesToRoutesInBackground: Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Обновляет данные зоны из БД
     * @return true если зона была обновлена, false если данные не изменились
     */
    private boolean updateAreaData(nurgling.areas.NArea existing, nurgling.areas.NArea fromDB) {
        boolean wasUpdated = false;
        
        // Проверяем изменения в базовых полях
        // ВАЖНО: hide НЕ сравниваем и НЕ копируем из БД - это локальный параметр!
        if (!java.util.Objects.equals(existing.name, fromDB.name) ||
            !java.util.Objects.equals(existing.path, fromDB.path) ||
            !java.util.Objects.equals(existing.color, fromDB.color) ||
            !java.util.Objects.equals(existing.uuid, fromDB.uuid) ||
            !java.util.Objects.equals(existing.zoneSync, fromDB.zoneSync) ||
            existing.lastUpdated != fromDB.lastUpdated ||
            existing.synced != fromDB.synced) {
            wasUpdated = true;
        }
        
        existing.name = fromDB.name;
        existing.path = fromDB.path;
        existing.color = fromDB.color;
        // ВАЖНО: НЕ копируем hide из БД - используем локальный AllowedZonesManager
        // existing.hide остаётся без изменений, или применяется локальный статус
        nurgling.areas.AllowedZonesManager.getInstance().applyLocalHideStatus(existing);
        existing.uuid = fromDB.uuid;
        existing.zoneSync = fromDB.zoneSync;
        existing.lastUpdated = fromDB.lastUpdated;
        existing.synced = fromDB.synced;
        
        // Обновляем space если изменился
        if (fromDB.space != null && fromDB.space.space != null) {
            if (existing.space == null || !existing.space.space.equals(fromDB.space.space)) {
                wasUpdated = true;
            }
            existing.space = fromDB.space;
            // ВАЖНО: поддерживаем grids_id консистентным со space
            existing.syncGridIdsFromSpace();
        }
        
        // Обновляем spec, jin, jout если изменились
        // ВАЖНО: Создаем новые объекты, чтобы гарантировать обновление ссылок
        if (fromDB.spec != null) {
            if (existing.spec == null || !existing.spec.equals(fromDB.spec)) {
                wasUpdated = true;
            }
            existing.spec = new ArrayList<>(fromDB.spec);
        }
        if (fromDB.jin != null) {
            // Создаем новый JSONArray из fromDB.jin, чтобы гарантировать обновление
            try {
                String newJinStr = fromDB.jin.toString();
                String oldJinStr = existing.jin != null ? existing.jin.toString() : null;
                if (!java.util.Objects.equals(oldJinStr, newJinStr)) {
                    wasUpdated = true;
                }
                existing.jin = new org.json.JSONArray(newJinStr);
            } catch (Exception e) {
                if (existing.jin != fromDB.jin) {
                    wasUpdated = true;
                }
                existing.jin = fromDB.jin;
            }
        }
        if (fromDB.jout != null) {
            // ВАЖНО: Создаем новый JSONArray из fromDB.jout, чтобы гарантировать обновление
            // Это нужно чтобы боты видели изменения в jout после синхронизации
            try {
                String newJoutStr = fromDB.jout.toString();
                String oldJoutStr = existing.jout != null ? existing.jout.toString() : null;
                if (!java.util.Objects.equals(oldJoutStr, newJoutStr)) {
                    wasUpdated = true;
                }
                existing.jout = new org.json.JSONArray(newJoutStr);
            } catch (Exception e) {
                if (existing.jout != fromDB.jout) {
                    wasUpdated = true;
                }
                existing.jout = fromDB.jout;
            }
        }
        
        return wasUpdated;
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
                // Получаем объект зоны из памяти
                nurgling.areas.NArea area = mapView.glob.map.areas.get(areaId);
                removeVisualZone(mapView, areaId, area);
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
     * @param mapView карта
     * @param areaId ID зоны
     * @param area объект зоны (может быть null, если зона уже удалена из памяти)
     */
    private void removeVisualZone(nurgling.NMapView mapView, int areaId, nurgling.areas.NArea area) {
        // Удаляем overlay (синхронизируем доступ к nols)
        synchronized (mapView.nols) {
            nurgling.overlays.map.NOverlay nol = mapView.nols.get(areaId);
            if (nol != null) {
                nol.remove();
                mapView.nols.remove(areaId);
            }
        }
        
        // Удаляем dummy (используем данные из area, если доступны)
        if (area != null && area.gid != Long.MIN_VALUE) {
            haven.Gob dummy = mapView.dummys.get(area.gid);
            if (dummy != null) {
                mapView.glob.oc.remove(dummy);
                mapView.dummys.remove(area.gid);
            }
        } else {
            // Если area не доступна, пытаемся найти dummy по всем зонам
            // (на случай, если зона уже удалена из памяти, но dummy еще существует)
            synchronized (mapView.glob.map.areas) {
                for (nurgling.areas.NArea existingArea : mapView.glob.map.areas.values()) {
                    if (existingArea.id == areaId && existingArea.gid != Long.MIN_VALUE) {
                        haven.Gob dummy = mapView.dummys.get(existingArea.gid);
                        if (dummy != null) {
                            mapView.glob.oc.remove(dummy);
                            mapView.dummys.remove(existingArea.gid);
                        }
                        break;
                    }
                }
            }
        }
        
        // Удаляем из route graph
        try {
            mapView.routeGraphManager.getGraph().deleteAreaFromRoutePoints(areaId);
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        // Удаляем из areas widget
        if (nurgling.NUtils.getGameUI().areas != null) {
            nurgling.NUtils.getGameUI().areas.removeArea(areaId);
        }
        
    }
}
