package nurgling.areas.db;

import nurgling.areas.NArea;
import nurgling.areas.storage.AreaDBStorage;
import nurgling.areas.storage.AreaJSONStorage;
import nurgling.areas.storage.AreaStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный менеджер для работы с зонами в БД.
 * Автоматически выбирает хранилище (БД или JSON fallback).
 */
public class AreaDBManager {
    private static AreaDBManager instance;
    private AreaStorage primaryStorage;
    private AreaStorage fallbackStorage;
    private AreasDBPoolManager areasPoolManager;
    private boolean useDB = false;
    
    // Throttling для сохранений (не чаще чем раз в 2 секунды)
    private long lastSaveTime = 0;
    private static final long SAVE_THROTTLE_MS = 2000;
    
    // Отслеживание измененных зон для предотвращения лишних сохранений
    private final Map<Integer, Long> lastSavedAreas = new ConcurrentHashMap<>();
    
    // Флаг для предотвращения сохранения сразу после загрузки
    private long lastLoadTime = 0;
    private static final long LOAD_SAVE_DELAY_MS = 3000; // Не сохраняем 3 секунды после загрузки
    
    private AreaDBManager() {
        // Инициализация будет выполнена при первом использовании
    }
    
    /**
     * Сохраняет зону без троттлинга (используется при получении обновлений с сервера),
     * чтобы не пропускать изменения, пришедшие извне.
     */
    public void saveAreaNoThrottle(NArea area) {
        try {
            getActiveStorage().saveArea(area);

            // Обновляем счетчики сохранений, чтобы внутренняя логика оставалась консистентной
            long now = System.currentTimeMillis();
            lastSaveTime = now;
            lastSavedAreas.put(area.id, now);

            // При получении с сервера не инициируем обратную отправку (push) —
            // иначе можем вызвать ненужный круговой трафик.
        } catch (AreaStorage.StorageException e) {
            if (!e.getMessage().contains("locked") && !e.getMessage().contains("BUSY")) {
                System.err.println("AreaDBManager: Failed to save (no throttle) area " + area.id + ": " + e.getMessage());
            }
        }
    }
    
    public static synchronized AreaDBManager getInstance() {
        if (instance == null) {
            instance = new AreaDBManager();
        }
        return instance;
    }
    
    /**
     * Инициализирует менеджер с учетом доступности БД
     * Использует отдельную БД "Areas.db" в корне игры
     */
    private void initialize() {
        if (primaryStorage != null) {
            return; // Уже инициализирован
        }
        
        // Создаем отдельный менеджер БД для зон
        try {
            if (areasPoolManager == null) {
                areasPoolManager = new AreasDBPoolManager();
            }
            
            primaryStorage = new AreaDBStorage(areasPoolManager);
            if (primaryStorage.isAvailable()) {
                useDB = true;
                System.out.println("AreaDBManager: Using database storage: " + areasPoolManager.getDbPath());
                return;
            }
        } catch (Exception e) {
            System.err.println("AreaDBManager: Failed to initialize database storage: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Fallback на JSON
        fallbackStorage = new AreaJSONStorage();
        useDB = false;
        System.out.println("AreaDBManager: Using JSON storage (fallback)");
    }
    
    /**
     * Получает активное хранилище
     */
    private AreaStorage getActiveStorage() {
        initialize();
        if (useDB && primaryStorage != null && primaryStorage.isAvailable()) {
            return primaryStorage;
        }
        if (fallbackStorage == null) {
            fallbackStorage = new AreaJSONStorage();
        }
        return fallbackStorage;
    }
    
    /**
     * Возвращает poolManager для работы с БД (для синхронизации)
     */
    public nurgling.areas.storage.DatabaseConnectionManager getPoolManager() {
        initialize();
        if (useDB && areasPoolManager != null) {
            return areasPoolManager;
        }
        return null;
    }
    
    /**
     * Загружает все зоны
     */
    public Map<Integer, NArea> loadAllAreas() {
        try {
            Map<Integer, NArea> areas = getActiveStorage().loadAllAreas();
            long currentTime = System.currentTimeMillis();
            lastLoadTime = currentTime;
            
            // Помечаем все загруженные зоны как "недавно загруженные", 
            // чтобы не пытаться их сохранять сразу после загрузки
            for (Integer areaId : areas.keySet()) {
                lastSavedAreas.put(areaId, currentTime);
            }
            
            System.out.println("AreaDBManager: Loaded " + areas.size() + " areas from " + 
                             (useDB ? "database" : "JSON"));
            return areas;
        } catch (AreaStorage.StorageException e) {
            System.err.println("AreaDBManager: Failed to load areas: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
    
    /**
     * Сохраняет зону (с проверкой throttling)
     */
    public void saveArea(NArea area) {
        long currentTime = System.currentTimeMillis();
        
        // Проверяем throttling - не сохраняем слишком часто
        if (currentTime - lastSaveTime < SAVE_THROTTLE_MS) {
            return; // Пропускаем сохранение, слишком рано
        }
        
        // Проверяем, изменилась ли зона (простая проверка по времени последнего сохранения)
        Long lastSaved = lastSavedAreas.get(area.id);
        if (lastSaved != null && (currentTime - lastSaved) < SAVE_THROTTLE_MS) {
            return; // Зона недавно сохранена, пропускаем
        }
        
        try {
            getActiveStorage().saveArea(area);
            
            // Если используем БД, также сохраняем в JSON как резервную копию
            if (useDB && primaryStorage != null) {
                try {
                    if (fallbackStorage == null) {
                        fallbackStorage = new AreaJSONStorage();
                    }
                    fallbackStorage.saveArea(area);
                } catch (Exception e) {
                    // Игнорируем ошибки резервного копирования
                }
            }
            
            lastSaveTime = currentTime;
            lastSavedAreas.put(area.id, currentTime);
        } catch (AreaStorage.StorageException e) {
            // Не логируем каждую ошибку, только серьезные
            if (!e.getMessage().contains("locked") && !e.getMessage().contains("BUSY")) {
                System.err.println("AreaDBManager: Failed to save area " + area.id + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Сохраняет все зоны (с throttling и проверкой изменений)
     */
    public void saveAllAreas(Collection<NArea> areas) {
        long currentTime = System.currentTimeMillis();
        
        // Не сохраняем сразу после загрузки
        if (currentTime - lastLoadTime < LOAD_SAVE_DELAY_MS) {
            return; // Пропускаем сохранение, недавно загрузили
        }
        
        // Throttling - не сохраняем слишком часто
        if (currentTime - lastSaveTime < SAVE_THROTTLE_MS) {
            return; // Пропускаем сохранение
        }
        
        // Фильтруем только измененные зоны
        List<NArea> areasToSave = new ArrayList<>();
        for (NArea area : areas) {
            Long lastSaved = lastSavedAreas.get(area.id);
            if (lastSaved == null || (currentTime - lastSaved) >= SAVE_THROTTLE_MS) {
                areasToSave.add(area);
            }
        }
        
        if (areasToSave.isEmpty()) {
            return; // Нет изменений для сохранения
        }
        
        try {
            AreaStorage storage = getActiveStorage();
            int savedCount = 0;
            int failedCount = 0;
            
            for (NArea area : areasToSave) {
                try {
                    // Проверяем, что зона валидна перед сохранением
                    if (area == null || area.id <= 0) {
                        continue; // Пропускаем невалидные зоны
                    }
                    
                    storage.saveArea(area);
                    lastSavedAreas.put(area.id, currentTime);
                    savedCount++;
                } catch (AreaStorage.StorageException e) {
                    failedCount++;
                    // Пропускаем ошибки блокировки БД (SQLITE_BUSY)
                    String errorMsg = e.getMessage();
                    Throwable cause = e.getCause();
                    boolean isLockError = false;
                    
                    if (errorMsg != null) {
                        isLockError = errorMsg.contains("locked") || 
                                     errorMsg.contains("BUSY") || 
                                     errorMsg.contains("database is locked") ||
                                     errorMsg.contains("non-critical");
                    }
                    
                    if (cause != null && cause instanceof java.sql.SQLException) {
                        String causeMsg = cause.getMessage();
                        if (causeMsg != null && (causeMsg.contains("locked") || causeMsg.contains("BUSY"))) {
                            isLockError = true;
                        }
                    }
                    
                    if (isLockError) {
                        // Игнорируем блокировки - попробуем в следующий раз
                        // Не увеличиваем счетчик ошибок для блокировок
                        failedCount--;
                        continue;
                    }
                    
                    // Логируем только серьезные ошибки (не блокировки)
                    System.err.println("AreaDBManager: Failed to save area " + (area != null ? area.id : "null") + 
                                     ": " + errorMsg);
                    if (cause != null) {
                        System.err.println("  Caused by: " + cause.getClass().getSimpleName() + 
                                         " - " + cause.getMessage());
                    }
                } catch (Exception e) {
                    failedCount++;
                    // Ловим любые другие исключения, чтобы не прерывать сохранение других зон
                    System.err.println("AreaDBManager: Unexpected error saving area " + 
                                     (area != null ? area.id : "null") + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Логируем результат только если были ошибки
            if (failedCount > 0) {
                System.err.println("AreaDBManager: Saved " + savedCount + " areas, failed " + failedCount + " areas");
            }
            
            // Резервная копия в JSON если используем БД (только при успешном сохранении)
            if (useDB && primaryStorage != null && !areasToSave.isEmpty()) {
                try {
                    if (fallbackStorage == null) {
                        fallbackStorage = new AreaJSONStorage();
                    }
                    // Сохраняем все зоны в JSON для резервной копии (реже, чем в БД)
                    if (currentTime - lastSaveTime > SAVE_THROTTLE_MS * 5) {
                        for (NArea area : areas) {
                            fallbackStorage.saveArea(area);
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки резервного копирования
                }
            }
            
            lastSaveTime = currentTime;
        } catch (Exception e) {
            // Не логируем каждую ошибку блокировки
            if (!e.getMessage().contains("locked") && !e.getMessage().contains("BUSY")) {
                System.err.println("AreaDBManager: Failed to save areas: " + e.getMessage());
            }
        }
    }
    
    /**
     * Удаляет зону (без пропуска синхронизации - для обратной совместимости)
     */
    /**
     * Проверяет, существует ли зона в БД и не удалена ли она
     */
    public boolean areaExists(int areaId) {
        try {
            if (useDB && primaryStorage instanceof AreaDBStorage) {
                return ((AreaDBStorage) primaryStorage).areaExists(areaId);
            }
            // Для JSON fallback всегда возвращаем true (не можем проверить)
            return true;
        } catch (Exception e) {
            System.err.println("AreaDBManager: Failed to check if area exists: " + e.getMessage());
            return true; // В случае ошибки считаем, что зона существует (не блокируем работу)
        }
    }
    
    public void deleteArea(int areaId) {
        deleteArea(areaId, false);
    }
    
    /**
     * Удаляет зону
     * @param areaId ID зоны для удаления
     * @param skipServerSync не используется (оставлено для обратной совместимости)
     */
    public void deleteArea(int areaId, boolean skipServerSync) {
        try {
            getActiveStorage().deleteArea(areaId);
            
            // Также удаляем из резервной копии
            if (useDB && fallbackStorage != null) {
                try {
                    fallbackStorage.deleteArea(areaId);
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
            
            System.out.println("AreaDBManager: Deleted area " + areaId);
        } catch (AreaStorage.StorageException e) {
            System.err.println("AreaDBManager: Failed to delete area: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Получает зону по ID
     */
    public NArea getArea(int areaId) {
        try {
            return getActiveStorage().getArea(areaId);
        } catch (AreaStorage.StorageException e) {
            System.err.println("AreaDBManager: Failed to get area: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Мигрирует данные из JSON в БД (если БД доступна)
     */
    public void migrateFromJSON() {
        try {
            // Используем отдельный менеджер БД для зон
            if (areasPoolManager == null) {
                areasPoolManager = new AreasDBPoolManager();
            }
            
            AreaDBStorage dbStorage = new AreaDBStorage(areasPoolManager);
            if (!dbStorage.isAvailable()) {
                return; // БД недоступна
            }
            
            // Проверяем, есть ли уже данные в БД
            Map<Integer, NArea> dbAreas = dbStorage.loadAllAreas();
            if (!dbAreas.isEmpty()) {
                System.out.println("AreaDBManager: Database already contains " + dbAreas.size() + " areas, skipping migration");
                return;
            }
            
            // Загружаем из JSON
            AreaJSONStorage jsonStorage = new AreaJSONStorage();
            Map<Integer, NArea> jsonAreas = jsonStorage.loadAllAreas();
            
            if (jsonAreas.isEmpty()) {
                System.out.println("AreaDBManager: No areas found in JSON, nothing to migrate");
                return;
            }
            
            // Сохраняем в БД
            System.out.println("AreaDBManager: Migrating " + jsonAreas.size() + " areas from JSON to database");
            for (NArea area : jsonAreas.values()) {
                dbStorage.saveArea(area);
            }
            
            System.out.println("AreaDBManager: Migration completed successfully");
        } catch (Exception e) {
            System.err.println("AreaDBManager: Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Проверяет, используется ли БД
     */
    public boolean isUsingDatabase() {
        initialize();
        return useDB && primaryStorage != null && primaryStorage.isAvailable();
    }
    
    /**
     * Сбрасывает инициализацию (для переподключения к БД)
     */
    public void reset() {
        primaryStorage = null;
        fallbackStorage = null;
        useDB = false;
    }
    
}

