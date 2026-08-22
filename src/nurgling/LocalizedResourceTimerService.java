package nurgling;

import haven.*;
import haven.Locked;
import nurgling.profiles.ConfigFactory;
import nurgling.profiles.ProfileAwareService;
import nurgling.widgets.LocalizedResourceTimerDialog;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Centralized service for all resource timer operations
 * Handles persistence, UI coordination, and map navigation
 * Supports world-specific profiles via ProfileAwareService
 */
public class LocalizedResourceTimerService implements ProfileAwareService {
    private final Map<String, LocalizedResourceTimer> timers = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private String dataFile;
    private final NGameUI gui;
    private String genus;
    private boolean dirty;

    public LocalizedResourceTimerService(NGameUI gui) {
        this.gui = gui;
        this.dataFile = NUtils.getDataFile("resource_timers.nurgling.json");
        loadTimers();
        scheduleReloadFromDisk();
    }

    /**
     * Constructor for profile-aware initialization
     */
    public LocalizedResourceTimerService(NGameUI gui, String genus) {
        this.gui = gui;
        this.genus = genus;
        initializeForProfile(genus);
    }

    // ProfileAwareService implementation

    @Override
    public void initializeForProfile(String genus) {
        this.genus = genus;
        NConfig config = ConfigFactory.getConfig(genus);
        this.dataFile = config.getResourceTimersPath();
        load();
        scheduleReloadFromDisk();
    }

    @Override
    public String getGenus() {
        return genus;
    }

    @Override
    public void load() {
        loadTimers();
    }

    private void scheduleReloadFromDisk() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            reloadFromDisk();
        }, "ResourceTimerReload");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void save() {
        lock.writeLock().lock();
        try {
            saveTimers();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handle resource marker click for timer functionality
     */
    public boolean handleResourceClick(MapFile.SMarker marker) {
        if (!isTimerSupportedResource(marker.res.name)) {
            return false;
        }
        
        String displayName = marker.nm != null ? marker.nm : marker.res.name;
        showResourceTimerDialog(marker, displayName);
        return true;
    }
    
    /**
     * Show the resource timer dialog
     */
    public void showResourceTimerDialog(MapFile.SMarker marker, String displayName) {
        LocalizedResourceTimerDialog widget = gui.getAddResourceTimerWidget();
        if (widget != null) {
            widget.showForMarker(this, marker, displayName);
        }
    }
    
    /**
     * Create a timer for a resource
     */
    public void createTimer(long segmentId, haven.Coord tileCoords, String resourceName, 
                           String resourceType, long duration, String description) {
        createTimer(segmentId, tileCoords, resourceName, resourceType, duration, description, 0L, null);
    }

    public void createTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                           String resourceType, long duration, String description,
                           long autoRemoveAfterMs, String iconRes) {
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer timer = new LocalizedResourceTimer(segmentId, tileCoords, resourceName,
                                                   resourceType, duration, description, autoRemoveAfterMs, iconRes);
            timers.put(timer.getResourceId(), timer);
            dirty = true;
            saveTimers();
            refreshTimerWindow();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove a timer
     */
    public boolean removeTimer(String resourceId) {
        lock.writeLock().lock();
        try {
            boolean removed = timers.remove(resourceId) != null;
            if (removed) {
                dirty = true;
                saveTimers();
                refreshTimerWindow();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get existing timer for a resource location
     */
    public LocalizedResourceTimer getExistingTimer(long segmentId, haven.Coord tileCoords, String resourceType) {
        String resourceId = generateResourceId(segmentId, tileCoords, resourceType);
        return getTimer(resourceId);
    }
    
    /**
     * Get timer by resource ID
     */
    public LocalizedResourceTimer getTimer(String resourceId) {
        lock.readLock().lock();
        try {
            return timers.get(resourceId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all timers for display
     */
    public java.util.Collection<LocalizedResourceTimer> getAllTimers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(timers.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get timers for a specific segment (for map display)
     */
    public java.util.List<LocalizedResourceTimer> getTimersForSegment(long segmentId) {
        lock.readLock().lock();
        try {
            return timers.values().stream()
                    .filter(timer -> timer.getSegmentId() == segmentId)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private static String generateResourceId(long segmentId, haven.Coord tileCoords, String resourceType) {
        return String.format("res_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y, 
                           resourceType.replaceAll("[^a-zA-Z0-9]", "_"));
    }
    
    /**
     * Navigate to a resource timer location
     */
    public void openMapAtLocalizedResourceLocation(LocalizedResourceTimer timer) {
        try {
            openMapWindowIfNeeded();
            
            if (gui.mmap != null) {
                try (Locked lk = new Locked(gui.mmap.file.lock.readLock())) {
                    MapFile.Segment segment = gui.mmap.file.segments.get(timer.getSegmentId());
                    if (segment != null) {
                        MiniMap.Location targetLoc = new MiniMap.Location(segment, timer.getTileCoords());
                        centerBigMapOnly(targetLoc);
                    }
                }
            }
        } catch (Exception e) {
            showMessage("Navigation error: " + e.getMessage());
        }
    }
    
    /**
     * Show the timer window
     */
    public void showTimerWindow() {
        if (gui.localizedResourceTimersWindow != null) {
            if (gui.localizedResourceTimersWindow.visible()) {
                gui.localizedResourceTimersWindow.hide();
            } else {
                gui.localizedResourceTimersWindow.show();
            }
        }
    }
    
    /**
     * Check if a resource type supports timers.
     * Supports both minimap markers (gfx/terobjs/mm/...) and direct terobjs
     * (e.g. gfx/terobjs/crystalpatch, gfx/terobjs/bumlings/...) that send "Will refill in" when inspected.
     */
    public boolean isTimerSupportedResource(String resourceType) {
        return resourceType != null && resourceType.startsWith("gfx/terobjs/");
    }
    
    /**
     * Refresh the timer window display
     */
    private void refreshTimerWindow() {
        if (gui.localizedResourceTimersWindow != null) {
            gui.localizedResourceTimersWindow.refreshTimers();
        }
    }
    
    /**
     * Open map window if needed
     */
    private void openMapWindowIfNeeded() {
        if (gui.mapfile == null || !gui.mapfile.visible()) {
            gui.togglewnd(gui.mapfile);
        }
    }
    
    /**
     * Center only the big map window, not the minimap
     */
    private void centerBigMapOnly(MiniMap.Location targetLoc) {
        if (gui.mapfile != null) {
            nurgling.widgets.NMapWnd mapWnd = gui.mapfile;
            mapWnd.view.center(targetLoc);
            mapWnd.view.follow(null);
        }
    }
    
    /**
     * Show message to user
     */
    private void showMessage(String message) {
        gui.msg(message);
    }
    
    static JSONObject serializeTimers(Collection<LocalizedResourceTimer> timers) {
        JSONObject main = new JSONObject();
        JSONArray jTimers = new JSONArray();
        if (timers != null) {
            for (LocalizedResourceTimer timer : timers) {
                if (timer.shouldPersist()) {
                    jTimers.put(timer.toJson());
                }
            }
        }
        main.put("timers", jTimers);
        main.put("version", 1);
        main.put("lastSaved", java.time.Instant.now().toString());
        return main;
    }

    static List<LocalizedResourceTimer> deserializeTimers(JSONObject main) {
        List<LocalizedResourceTimer> loaded = new ArrayList<>();
        if (main == null || !main.has("timers"))
            return loaded;
        JSONArray array = main.getJSONArray("timers");
        for (int i = 0; i < array.length(); i++) {
            LocalizedResourceTimer timer = new LocalizedResourceTimer(array.getJSONObject(i));
            if (timer.shouldPersist()) {
                loaded.add(timer);
            }
        }
        return loaded;
    }

    static void mergeMissing(Map<String, LocalizedResourceTimer> memory,
                             Iterable<LocalizedResourceTimer> disk) {
        if (memory == null || disk == null)
            return;
        for (LocalizedResourceTimer timer : disk) {
            if (timer == null || !timer.shouldPersist())
                continue;
            if (!memory.containsKey(timer.getResourceId())) {
                memory.put(timer.getResourceId(), timer);
            }
        }
    }

    static boolean shouldSaveOnDispose(boolean dirty) {
        return dirty;
    }

    /**
     * Re-read the JSON file and add any timers missing in memory.
     * Needed after relog when the previous session may save after this one already loaded.
     */
    public void reloadFromDisk() {
        lock.writeLock().lock();
        try {
            int before = timers.size();
            mergeMissing(timers, deserializeTimers(nurgling.util.SafeJsonWriter.readCurrent(dataFile)));
            if (timers.size() > before) {
                refreshTimerWindow();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Load timers from JSON file
     */
    private void loadTimers() {
        lock.writeLock().lock();
        try {
            timers.clear();
            JSONObject main = nurgling.util.SafeJsonWriter.readCurrent(dataFile);
            try {
                for (LocalizedResourceTimer timer : deserializeTimers(main)) {
                    timers.put(timer.getResourceId(), timer);
                }
                JSONArray stored = main.optJSONArray("timers");
                if (stored != null && stored.length() > timers.size()) {
                    dirty = true;
                    saveTimers();
                }
            } catch (Exception e) {
                System.err.println("Failed to parse resource timers JSON: " + e.getMessage());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Save timers to JSON file
     */
    private void saveTimers() {
        // Called within write lock - don't lock again
        try {
            nurgling.util.SafeJsonWriter.writeAtomic(dataFile, serializeTimers(timers.values()));
        } catch (IOException e) {
            System.err.println("Failed to save resource timers: " + e.getMessage());
        }
    }
    
    /**
     * Dispose the service and cleanup resources
     */
    public void dispose() {
        lock.writeLock().lock();
        try {
            if (shouldSaveOnDispose(dirty)) {
                saveTimers();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ========== Database Sync Methods ==========
    
    /**
     * Add a timer loaded from database (called by LocalTimerSyncService).
     * Does not trigger save to file or DB (to avoid infinite loops).
     */
    public void addTimerFromDb(String resourceId, long segmentId, haven.Coord tileCoords,
                               String resourceName, String resourceType,
                               long startTimeUtc, long durationMs, String description) {
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer timer = new LocalizedResourceTimer(
                resourceId, segmentId, tileCoords, resourceName, resourceType,
                startTimeUtc, durationMs, description);
            
            // Only add if not expired
            if (!timer.isExpired()) {
                timers.put(resourceId, timer);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update an existing timer with data from database.
     * Creates a new timer instance with updated values.
     */
    public void updateTimerFromDb(String resourceId, long startTimeUtc, long durationMs, String description) {
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer existing = timers.get(resourceId);
            if (existing != null) {
                if (existing.isEphemeral()) {
                    return;
                }
                // Create new timer with updated values
                LocalizedResourceTimer updated = new LocalizedResourceTimer(
                    resourceId,
                    existing.getSegmentId(),
                    existing.getTileCoords(),
                    existing.getResourceName(),
                    existing.getResourceType(),
                    startTimeUtc,
                    durationMs,
                    description
                );
                
                // Only update if not expired
                if (!updated.isExpired()) {
                    timers.put(resourceId, updated);
                } else {
                    timers.remove(resourceId);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Refresh timer window from sync (called on UI thread).
     */
    public void refreshTimerWindowFromSync() {
        refreshTimerWindow();
    }
}
