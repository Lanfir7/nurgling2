package nurgling.areas;

import nurgling.profiles.ProfileManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет локальным списком "разрешённых" зон (без hide).
 * 
 * Когда зоны загружаются из общей PostgreSQL базы данных,
 * все зоны по умолчанию помечаются как hide=true.
 * Только зоны из этого локального списка будут видны макросам (hide=false).
 * 
 * Это позволяет каждому игроку индивидуально выбирать какие зоны ему нужны,
 * не влияя на других игроков.
 */
public class AllowedZonesManager {
    private static final String CONFIG_FILE = "allowed_zones.nurgling.json";
    
    private static AllowedZonesManager instance;
    
    // UUID зон, которые разрешены (hide=false)
    private final Set<String> allowedZoneUuids = ConcurrentHashMap.newKeySet();
    
    // ID зон, которые разрешены (для зон без UUID)
    private final Set<Integer> allowedZoneIds = ConcurrentHashMap.newKeySet();
    
    // ID зон созданных локально в текущей сессии (автоматически разрешены)
    private final Set<Integer> locallyCreatedZoneIds = ConcurrentHashMap.newKeySet();
    
    private ProfileManager profileManager;
    private boolean initialized = false;
    
    private AllowedZonesManager() {}
    
    public static synchronized AllowedZonesManager getInstance() {
        if (instance == null) {
            instance = new AllowedZonesManager();
        }
        return instance;
    }
    
    /**
     * Инициализация с профилем мира
     */
    public void initialize(String genus) {
        if (genus == null || genus.isEmpty()) {
            return;
        }
        
        this.profileManager = new ProfileManager(genus);
        load();
        initialized = true;
        System.out.println("AllowedZonesManager: Initialized with " + allowedZoneIds.size() + " allowed IDs, " + allowedZoneUuids.size() + " allowed UUIDs");
    }
    
    /**
     * Проверяет, разрешена ли зона (не hide)
     */
    public boolean isAllowed(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        return allowedZoneUuids.contains(uuid);
    }
    
    /**
     * Проверяет, была ли зона создана локально в этой сессии
     */
    public boolean isLocallyCreated(int areaId) {
        return locallyCreatedZoneIds.contains(areaId);
    }
    
    /**
     * Помечает зону как созданную локально (автоматически разрешена)
     */
    public void markAsLocallyCreated(int areaId, String uuid) {
        locallyCreatedZoneIds.add(areaId);
        // Добавляем в allowed по ID (работает даже без UUID)
        allowedZoneIds.add(areaId);
        if (uuid != null && !uuid.isEmpty()) {
            allowedZoneUuids.add(uuid);
        }
        save();
        System.out.println("AllowedZonesManager: Marked zone " + areaId + " as locally created and allowed");
    }
    
    /**
     * Разрешает зону (снимает hide)
     */
    public void allow(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        if (allowedZoneUuids.add(uuid)) {
            save();
            System.out.println("AllowedZonesManager: Allowed zone " + uuid);
        }
    }
    
    /**
     * Запрещает зону (устанавливает hide)
     */
    public void disallow(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        if (allowedZoneUuids.remove(uuid)) {
            save();
            System.out.println("AllowedZonesManager: Disallowed zone " + uuid);
        }
    }
    
    /**
     * Переключает состояние зоны
     */
    public void toggle(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        if (allowedZoneUuids.contains(uuid)) {
            disallow(uuid);
        } else {
            allow(uuid);
        }
    }
    
    /**
     * Применяет локальный hide статус к зоне
     * Возвращает true если зона должна быть скрыта
     */
    public boolean shouldBeHidden(NArea area) {
        if (area == null) {
            return true;
        }
        
        boolean isLocallyCreated = locallyCreatedZoneIds.contains(area.id);
        boolean isInAllowedById = allowedZoneIds.contains(area.id);
        boolean isInAllowedByUuid = area.uuid != null && !area.uuid.isEmpty() && allowedZoneUuids.contains(area.uuid);
        
        // Зоны созданные локально в этой сессии - всегда видны
        if (isLocallyCreated) {
            return false;
        }
        
        // Зоны в списке разрешённых по ID - видны
        if (isInAllowedById) {
            return false;
        }
        
        // Зоны с UUID в списке разрешённых - видны
        if (isInAllowedByUuid) {
            return false;
        }
        
        // ВСЕ остальные зоны - скрыты по умолчанию
        // Это ключевое изменение: зоны без UUID тоже скрываются если не в allowed
        return true;
    }
    
    /**
     * Применяет локальный hide статус к зоне (модифицирует area.hide)
     */
    public void applyLocalHideStatus(NArea area) {
        if (area == null) {
            return;
        }
        area.hide = shouldBeHidden(area);
    }
    
    /**
     * Загрузка списка из файла
     */
    private void load() {
        if (profileManager == null) {
            return;
        }
        
        try {
            Path filePath = profileManager.getConfigPath(CONFIG_FILE);
            if (!Files.exists(filePath)) {
                return;
            }
            
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            
            allowedZoneUuids.clear();
            allowedZoneIds.clear();
            
            // Загружаем UUID (для совместимости)
            if (root.has("allowed")) {
                JSONArray allowed = root.getJSONArray("allowed");
                for (int i = 0; i < allowed.length(); i++) {
                    allowedZoneUuids.add(allowed.getString(i));
                }
            }
            
            // Загружаем ID
            if (root.has("allowedIds")) {
                JSONArray allowedIds = root.getJSONArray("allowedIds");
                for (int i = 0; i < allowedIds.length(); i++) {
                    allowedZoneIds.add(allowedIds.getInt(i));
                }
            }
            
            System.out.println("AllowedZonesManager: Loaded " + allowedZoneIds.size() + " allowed IDs, " + allowedZoneUuids.size() + " allowed UUIDs");
            
        } catch (Exception e) {
            System.err.println("AllowedZonesManager: Failed to load: " + e.getMessage());
        }
    }
    
    /**
     * Сохранение списка в файл
     */
    private void save() {
        if (profileManager == null) {
            return;
        }
        
        try {
            Path filePath = profileManager.getConfigPath(CONFIG_FILE);
            Files.createDirectories(filePath.getParent());
            
            JSONObject root = new JSONObject();
            
            // Сохраняем UUID
            JSONArray allowed = new JSONArray();
            for (String uuid : allowedZoneUuids) {
                allowed.put(uuid);
            }
            root.put("allowed", allowed);
            
            // Сохраняем ID
            JSONArray allowedIds = new JSONArray();
            for (Integer id : allowedZoneIds) {
                allowedIds.put(id);
            }
            root.put("allowedIds", allowedIds);
            
            root.put("version", 2);
            root.put("lastSaved", System.currentTimeMillis());
            
            nurgling.util.SafeJsonWriter.writeAtomic(filePath.toString(), root);
            
        } catch (Exception e) {
            System.err.println("AllowedZonesManager: Failed to save: " + e.getMessage());
        }
    }
    
    /**
     * Разрешить зону по ID
     */
    public void allowById(int areaId) {
        if (allowedZoneIds.add(areaId)) {
            save();
            System.out.println("AllowedZonesManager: Allowed zone ID " + areaId);
        }
    }
    
    /**
     * Запретить зону по ID
     */
    public void disallowById(int areaId) {
        if (allowedZoneIds.remove(areaId)) {
            save();
            System.out.println("AllowedZonesManager: Disallowed zone ID " + areaId);
        }
    }
    
    /**
     * Проверить разрешена ли зона по ID
     */
    public boolean isAllowedById(int areaId) {
        return allowedZoneIds.contains(areaId);
    }
    
    /**
     * Получить количество разрешённых зон
     */
    public int getAllowedCount() {
        return allowedZoneUuids.size() + allowedZoneIds.size();
    }
    
    /**
     * Сброс состояния (при смене мира)
     */
    public void reset() {
        allowedZoneUuids.clear();
        locallyCreatedZoneIds.clear();
        initialized = false;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
}

