package nurgling.areas.storage;

import nurgling.NConfig;
import nurgling.areas.NArea;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Реализация хранилища зон через JSON (fallback)
 */
public class AreaJSONStorage implements AreaStorage {
    private final String areasPath;
    
    public AreaJSONStorage() {
        NConfig config = NConfig.current;
        if (config != null) {
            this.areasPath = config.getAreasPath();
        } else {
            // Fallback если конфиг недоступен
            this.areasPath = null;
        }
    }
    
    public AreaJSONStorage(String customPath) {
        this.areasPath = customPath;
    }
    
    @Override
    public boolean isAvailable() {
        return areasPath != null && !areasPath.isEmpty();
    }
    
    @Override
    public Map<Integer, NArea> loadAllAreas() throws StorageException {
        Map<Integer, NArea> areas = new HashMap<>();
        
        if (!isAvailable()) {
            return areas;
        }
        
        File file = new File(areasPath);
        if (!file.exists()) {
            return areas;
        }
        
        try {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(areasPath), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            }
            
            if (contentBuilder.length() == 0) {
                return areas;
            }
            
            JSONObject main = new JSONObject(contentBuilder.toString());
            if (!main.has("areas")) {
                return areas;
            }
            
            JSONArray array = main.getJSONArray("areas");
            for (int i = 0; i < array.length(); i++) {
                NArea area = new NArea(array.getJSONObject(i));
                areas.put(area.id, area);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to load areas from JSON", e);
        }
        
        return areas;
    }
    
    @Override
    public void saveArea(NArea area) throws StorageException {
        // Для JSON хранилища сохраняем все зоны
        // Это неэффективно, но это fallback
        Map<Integer, NArea> allAreas = loadAllAreas();
        allAreas.put(area.id, area);
        saveAllAreas(allAreas);
    }
    
    @Override
    public void deleteArea(int areaId) throws StorageException {
        Map<Integer, NArea> allAreas = loadAllAreas();
        allAreas.remove(areaId);
        saveAllAreas(allAreas);
    }
    
    @Override
    public NArea getArea(int areaId) throws StorageException {
        Map<Integer, NArea> allAreas = loadAllAreas();
        return allAreas.get(areaId);
    }
    
    private void saveAllAreas(Map<Integer, NArea> areas) throws StorageException {
        if (!isAvailable()) {
            throw new StorageException("JSON storage path is not available");
        }
        
        try {
            JSONObject main = new JSONObject();
            JSONArray jareas = new JSONArray();
            for (NArea area : areas.values()) {
                jareas.put(area.toJson());
            }
            main.put("areas", jareas);
            
            try (FileWriter f = new FileWriter(areasPath, StandardCharsets.UTF_8)) {
                main.write(f);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to save areas to JSON", e);
        }
    }
}

