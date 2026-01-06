package nurgling.areas.db;

import nurgling.areas.NArea;
import nurgling.db.DatabaseManager;
import nurgling.db.service.AreaService;

import java.util.Map;

/**
 * Адаптер для работы с зонами через новую систему БД (DatabaseManager/AreaService)
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
     * Удаляет зону
     */
    public void deleteArea(int areaId, boolean skipServerSync) {
        try {
            String profile = getCurrentProfile();
            if (profile == null || profile.isEmpty()) {
                profile = "global";
            }
            areaService.deleteArea(areaId, profile);
        } catch (Exception e) {
            System.err.println("AreaDBAdapter: Failed to delete area: " + e.getMessage());
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

