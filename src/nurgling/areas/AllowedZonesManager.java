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
        System.out.println("AllowedZonesManager: Initialized with " + allowedZoneUuids.size() + " allowed zones");
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
        if (uuid != null && !uuid.isEmpty()) {
            allowedZoneUuids.add(uuid);
            save();
        }
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
        
        // Зоны созданные локально в этой сессии - всегда видны
        if (locallyCreatedZoneIds.contains(area.id)) {
            return false;
        }
        
        // Зоны с UUID в списке разрешённых - видны
        if (area.uuid != null && !area.uuid.isEmpty() && allowedZoneUuids.contains(area.uuid)) {
            return false;
        }
        
        // Зоны без UUID (старые или локальные) - сохраняем текущий статус
        if (area.uuid == null || area.uuid.isEmpty()) {
            return area.hide;
        }
        
        // Все остальные зоны из БД - скрыты по умолчанию
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
            
            if (root.has("allowed")) {
                JSONArray allowed = root.getJSONArray("allowed");
                for (int i = 0; i < allowed.length(); i++) {
                    allowedZoneUuids.add(allowed.getString(i));
                }
            }
            
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
            JSONArray allowed = new JSONArray();
            for (String uuid : allowedZoneUuids) {
                allowed.put(uuid);
            }
            root.put("allowed", allowed);
            root.put("version", 1);
            root.put("lastSaved", System.currentTimeMillis());
            
            Files.write(filePath, root.toString(2).getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            System.err.println("AllowedZonesManager: Failed to save: " + e.getMessage());
        }
    }
    
    /**
     * Получить количество разрешённых зон
     */
    public int getAllowedCount() {
        return allowedZoneUuids.size();
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

