package nurgling;

import haven.*;
import nurgling.profiles.ConfigFactory;
import nurgling.profiles.ProfileAwareService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Service for managing saved prospecting locations
 * Similar to TreeLocationService but for prospecting results
 */
public class ProspectingLocationService implements ProfileAwareService {
    private final Map<String, ProspectingLocation> prospectingLocations = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private String dataFile;
    private final NGameUI gui;
    private String genus;

    public ProspectingLocationService(NGameUI gui) {
        this.gui = gui;
        this.dataFile = ((HashDirCache) ResCache.global).base + "\\..\\" + "prospecting_locations.nurgling.json";
        loadProspectingLocations();
    }

    /**
     * Constructor for profile-aware initialization
     */
    public ProspectingLocationService(NGameUI gui, String genus) {
        this.gui = gui;
        this.genus = genus;
        initializeForProfile(genus);
    }

    // ProfileAwareService implementation

    @Override
    public void initializeForProfile(String genus) {
        this.genus = genus;
        NConfig config = ConfigFactory.getConfig(genus);
        this.dataFile = config.getProspectingLocationsPath();
        load();
    }

    @Override
    public String getGenus() {
        return genus;
    }

    @Override
    public void load() {
        loadProspectingLocations();
    }

    @Override
    public void save() {
        lock.writeLock().lock();
        try {
            saveProspectingLocations();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Save a prospecting location at the player's current position
     */
    public void saveProspectingLocation(String resourceType) {
        try {
            if (gui.map == null) return;

            Gob player = gui.map.player();
            if (player == null) return;

            // Get current grid and segment info (same as TreeLocationService)
            MCache mcache = gui.map.glob.map;
            Coord tc = player.rc.floor(MCache.tilesz);  // Tile coordinate in world
            Coord gridCoord = tc.div(MCache.cmaps);  // Grid coordinate
            MCache.Grid grid = mcache.getgrid(gridCoord);

            MapFile mapFile = gui.mmap.file;
            // ВАЖНО: Получаем read lock перед доступом к gridinfo с таймаутом
            MapFile.GridInfo info = null;
            boolean lockAcquired = false;
            try {
                lockAcquired = mapFile.lock.readLock().tryLock(100, TimeUnit.MILLISECONDS);
                if (!lockAcquired) {
                    return; // Не можем получить блокировку в течение 100ms, пропускаем сохранение
                }
                info = mapFile.gridinfo.get(grid.id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // Прервано, пропускаем сохранение
            } finally {
                if (lockAcquired) {
                    mapFile.lock.readLock().unlock();
                }
            }
            if (info == null) return;

            long segmentId = info.seg;

            // Calculate segment-relative coordinate (same as SMarker creation in MiniMap.java:773)
            Coord segmentCoord = tc.add(info.sc.sub(grid.gc).mul(MCache.cmaps));

            // Проверяем, не сохранено ли уже это место
            lock.readLock().lock();
            try {
                String locationId = ProspectingLocation.generateLocationId(segmentId, segmentCoord, resourceType);
                if (prospectingLocations.containsKey(locationId)) {
                    return; // Уже сохранено
                }
            } finally {
                lock.readLock().unlock();
            }

            lock.writeLock().lock();
            try {
                // Повторная проверка после получения write lock (double-check pattern)
                String locationId = ProspectingLocation.generateLocationId(segmentId, segmentCoord, resourceType);
                if (prospectingLocations.containsKey(locationId)) {
                    return; // Уже сохранено другим потоком
                }
                
                ProspectingLocation location = new ProspectingLocation(segmentId, segmentCoord, resourceType);
                prospectingLocations.put(location.getLocationId(), location);
                saveProspectingLocations();
                
                // Создаем маркер на карте с иконкой Wine Glance (только для проспектинга)
                createProspectingMarker(mapFile, segmentId, segmentCoord, resourceType);
            } finally {
                lock.writeLock().unlock();
            }

        } catch (Exception e) {
            System.err.println("Error saving prospecting location: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Get all prospecting locations for a segment (for map rendering)
     */
    public List<ProspectingLocation> getProspectingLocationsForSegment(long segmentId) {
        lock.readLock().lock();
        try {
            List<ProspectingLocation> result = new ArrayList<>();
            for (ProspectingLocation loc : prospectingLocations.values()) {
                if (loc.getSegmentId() == segmentId) {
                    result.add(loc);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all prospecting locations
     */
    public Collection<ProspectingLocation> getAllProspectingLocations() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(prospectingLocations.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove a prospecting location
     */
    public boolean removeProspectingLocation(String locationId) {
        lock.writeLock().lock();
        try {
            boolean removed = prospectingLocations.remove(locationId) != null;
            if (removed) {
                saveProspectingLocations();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Load prospecting locations from JSON
     */
    private void loadProspectingLocations() {
        lock.writeLock().lock();
        try {
            prospectingLocations.clear();
            File file = new File(dataFile);
            if (file.exists()) {
                StringBuilder contentBuilder = new StringBuilder();
                try (Stream<String> stream = Files.lines(Paths.get(dataFile), StandardCharsets.UTF_8)) {
                    stream.forEach(s -> contentBuilder.append(s).append("\n"));
                } catch (IOException e) {
                    System.err.println("Failed to load prospecting locations: " + e.getMessage());
                    return;
                }

                if (!contentBuilder.toString().trim().isEmpty()) {
                    try {
                        JSONObject main = new JSONObject(contentBuilder.toString());
                        JSONArray array = main.getJSONArray("prospectingLocations");
                        for (int i = 0; i < array.length(); i++) {
                            ProspectingLocation location = new ProspectingLocation(array.getJSONObject(i));
                            prospectingLocations.put(location.getLocationId(), location);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse prospecting locations JSON: " + e.getMessage());
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Save prospecting locations to JSON
     */
    private void saveProspectingLocations() {
        // Called within write lock - don't lock again
        try {
            JSONObject main = new JSONObject();
            JSONArray jLocations = new JSONArray();
            for (ProspectingLocation location : prospectingLocations.values()) {
                jLocations.put(location.toJson());
            }
            main.put("prospectingLocations", jLocations);
            main.put("version", 1);
            main.put("lastSaved", java.time.Instant.now().toString());

            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                writer.write(main.toString(2)); // Pretty print with indent
            }
        } catch (IOException e) {
            System.err.println("Failed to save prospecting locations: " + e.getMessage());
        }
    }

    /**
     * Создает маркер на карте для проспектинга с иконкой Wine Glance
     */
    private void createProspectingMarker(MapFile mapFile, long segmentId, Coord segmentCoord, String resourceType) {
        try {
            // Используем уникальное имя маркера на основе координат и типа ресурса
            String markerName = resourceType != null ? resourceType : "Prospecting";
            String markerResourceName = "gfx/invobjs/wineglance"; // Wine Glance иконка (одна иконка, не cuprite)
            
            // Пытаемся получить write lock для создания маркера
            boolean lockAcquired = false;
            try {
                lockAcquired = mapFile.lock.writeLock().tryLock(500, TimeUnit.MILLISECONDS);
                if (!lockAcquired) {
                    return; // Не можем получить блокировку
                }
                
                // Проверяем, не существует ли уже маркер проспектинга на этой позиции
                // Используем уникальное имя ресурса для маркеров проспектинга
                MapFile.SMarker existingProspectingMarker = mapFile.smarker(markerResourceName, segmentId, segmentCoord);
                if (existingProspectingMarker != null) {
                    return; // Маркер проспектинга уже существует на этой позиции
                }
                
                // Также проверяем, нет ли уже обычного маркера камня на этой позиции
                // Если есть маркер камня, не создаем маркер проспектинга, чтобы не дублировать
                boolean hasStoneMarker = false;
                for (MapFile.Marker mark : mapFile.markers) {
                    if (mark.seg == segmentId && mark.tc.equals(segmentCoord)) {
                        // Проверяем, что это не маркер проспектинга
                        if (mark instanceof MapFile.SMarker) {
                            MapFile.SMarker sm = (MapFile.SMarker) mark;
                            // Если это маркер камня (не wineglance), пропускаем создание
                            if (!sm.res.name.equals(markerResourceName)) {
                                hasStoneMarker = true;
                                break;
                            }
                        }
                    }
                }
                if (hasStoneMarker) {
                    return; // Уже есть маркер камня на этой позиции, не создаем дубликат
                }
                
                // Загружаем ресурс иконки
                try {
                    Indir<Resource> resIndir = Resource.remote().load(markerResourceName);
                    Resource res = resIndir.get();
                    int resVer = res.ver;
                    
                    // Создаем маркер с иконкой Wine Glance
                    MapFile.SMarker marker = new MapFile.SMarker(
                        segmentId, 
                        segmentCoord, 
                        markerName, 
                        0, 
                        new Resource.Saved(Resource.remote(), markerResourceName, resVer)
                    );
                    
                    mapFile.add(marker);
                } catch (Loading e) {
                    // Ресурс еще не загружен, создаем маркер с версией 0
                    MapFile.SMarker marker = new MapFile.SMarker(
                        segmentId, 
                        segmentCoord, 
                        markerName, 
                        0, 
                        new Resource.Saved(Resource.remote(), markerResourceName, 0)
                    );
                    mapFile.add(marker);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lockAcquired) {
                    mapFile.lock.writeLock().unlock();
                }
            }
        } catch (Exception e) {
            System.err.println("Error creating prospecting marker: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Dispose the service and cleanup resources
     */
    public void dispose() {
        lock.writeLock().lock();
        try {
            saveProspectingLocations();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
