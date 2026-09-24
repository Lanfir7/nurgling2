package nurgling;

import haven.*;
import haven.Locked;
import nurgling.i18n.L10n;
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
    private static final long RESOLVE_RETRY_MS = 60_000L;
    private final java.util.Set<String> resolving = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, Long> lastResolveAttempt = new java.util.concurrent.ConcurrentHashMap<>();
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
        long gridId = LocalizedResourceTimer.NO_GRID;
        haven.Coord gridOffset = null;
        if (autoRemoveAfterMs <= 0) {
            GridAnchor anchor = anchorFor(segmentId, tileCoords);
            if (anchor != null) {
                gridId = anchor.gridId;
                gridOffset = anchor.offset;
            }
        }
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer timer = new LocalizedResourceTimer(segmentId, tileCoords, resourceName,
                                                   resourceType, duration, description, autoRemoveAfterMs, iconRes,
                                                   gridId, gridOffset);
            if (timer.hasGrid())
                timers.remove(LocalizedResourceTimer.legacyResourceId(segmentId, tileCoords, resourceType));
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
        GridAnchor anchor = anchorFor(segmentId, tileCoords);
        if (anchor != null) {
            LocalizedResourceTimer byGrid = getTimer(LocalizedResourceTimer.sharedResourceId(
                    anchor.gridId, anchor.offset, resourceType));
            if (byGrid != null)
                return byGrid;
        }
        return getTimer(LocalizedResourceTimer.legacyResourceId(segmentId, tileCoords, resourceType));
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
        java.util.List<LocalizedResourceTimer> pending = new ArrayList<>();
        java.util.List<LocalizedResourceTimer> placed = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (LocalizedResourceTimer timer : timers.values()) {
                if (!timer.hasLocalPlacement()) {
                    if (timer.hasGrid())
                        pending.add(timer);
                    continue;
                }
                if (timer.getSegmentId() == segmentId)
                    placed.add(timer);
            }
        } finally {
            lock.readLock().unlock();
        }
        for (LocalizedResourceTimer timer : pending)
            scheduleResolve(timer);
        return placed;
    }

    /**
     * Follow a MapFile segment merge. Same shift as {@code Marker.tc.sub(soff.mul(cmaps))}.
     *
     * @return resource ids that no longer exist, so the shared DB row can be dropped
     */
    public java.util.List<String> remapSegment(long srcSeg, long dstSeg, haven.Coord gridSoff) {
        haven.Coord tileShift = gridSoff.mul(haven.MCache.cmaps);
        lock.writeLock().lock();
        try {
            java.util.List<String> oldIds = new ArrayList<>();
            java.util.List<LocalizedResourceTimer> moved = new ArrayList<>();
            java.util.Iterator<java.util.Map.Entry<String, LocalizedResourceTimer>> it =
                    timers.entrySet().iterator();
            while (it.hasNext()) {
                LocalizedResourceTimer t = it.next().getValue();
                if (!t.hasLocalPlacement() || t.getSegmentId() != srcSeg)
                    continue;
                LocalizedResourceTimer next = t.relocated(dstSeg, tileShift);
                if (!t.getResourceId().equals(next.getResourceId()))
                    oldIds.add(t.getResourceId());
                it.remove();
                moved.add(next);
            }
            for (LocalizedResourceTimer t : moved)
                timers.put(t.getResourceId(), t);
            if (!moved.isEmpty()) {
                dirty = true;
                saveTimers();
                refreshTimerWindow();
            }
            return oldIds;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Navigate to a resource timer location
     */
    public void openMapAtLocalizedResourceLocation(LocalizedResourceTimer timer) {
        try {
            if (timer != null && !timer.hasLocalPlacement()) {
                resolvePlacement(timer);
                timer = getTimer(timer.getResourceId());
            }
            if (timer == null || !timer.hasLocalPlacement()) {
                showMessage(L10n.get("timer.not_on_map"));
                return;
            }
            openMapWindowIfNeeded();
            
            if (gui.mmap != null) {
                try (Locked lk = new Locked(gui.mmap.file.lock.readLock())) {
                    MapFile.Segment segment = gui.mmap.file.segments.get(timer.getSegmentId());
                    if (segment != null) {
                        MiniMap.Location targetLoc = new MiniMap.Location(segment, timer.getTileCoords());
                        centerBigMapOnly(targetLoc);
                    } else {
                        showMessage(L10n.get("timer.not_on_map"));
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
    /**
     * Shared row from another client. Coordinates stay unresolved until this map file
     * knows the server grid; the sender's segment must not be used.
     */
    public void addUnplacedFromDb(long gridId, haven.Coord gridOffset,
                                  String resourceName, String resourceType,
                                  long startTimeUtc, long durationMs, String description) {
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer timer = LocalizedResourceTimer.unplaced(
                    gridId, gridOffset, resourceName, resourceType, startTimeUtc, durationMs, description);
            if (!timer.isExpired())
                timers.put(timer.getResourceId(), timer);
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
                LocalizedResourceTimer updated = existing.withSchedule(startTimeUtc, durationMs, description);
                
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
     * Give local timers a server-grid identity before upload.
     * @return previous resource ids that the database should drop
     */
    /** Legacy rows stored another client's segment id. Drop them when this map has no such segment. */
    public void dropUnmappedLegacyTimers() {
        java.util.List<LocalizedResourceTimer> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(timers.values());
        } finally {
            lock.readLock().unlock();
        }
        java.util.List<String> drop = new ArrayList<>();
        for (LocalizedResourceTimer timer : snapshot) {
            if (timer.hasGrid() || timer.isEphemeral() || !timer.hasLocalPlacement())
                continue;
            if (!segmentExists(timer.getSegmentId()))
                drop.add(timer.getResourceId());
        }
        if (drop.isEmpty())
            return;
        lock.writeLock().lock();
        try {
            boolean removed = false;
            for (String id : drop) {
                LocalizedResourceTimer timer = timers.get(id);
                if (timer != null && !timer.hasGrid()) {
                    timers.remove(id);
                    removed = true;
                }
            }
            if (removed) {
                dirty = true;
                saveTimers();
                refreshTimerWindow();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public java.util.List<String> bindGridAnchors() {
        java.util.List<LocalizedResourceTimer> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(timers.values());
        } finally {
            lock.readLock().unlock();
        }
        java.util.List<String> retired = new ArrayList<>();
        for (LocalizedResourceTimer timer : snapshot) {
            if (timer.isEphemeral() || timer.hasGrid() || !timer.hasLocalPlacement())
                continue;
            GridAnchor anchor = anchorFor(timer.getSegmentId(), timer.getTileCoords());
            if (anchor == null)
                continue;
            lock.writeLock().lock();
            try {
                LocalizedResourceTimer current = timers.get(timer.getResourceId());
                if (current == null || current.hasGrid() || current.isEphemeral())
                    continue;
                LocalizedResourceTimer bound = current.withGrid(anchor.gridId, anchor.offset);
                if (bound.getResourceId().equals(current.getResourceId()))
                    continue;
                timers.remove(current.getResourceId());
                LocalizedResourceTimer other = timers.get(bound.getResourceId());
                if (other == null || current.getStartTime() >= other.getStartTime())
                    timers.put(bound.getResourceId(), bound);
                retired.add(current.getResourceId());
                dirty = true;
                saveTimers();
            } finally {
                lock.writeLock().unlock();
            }
        }
        return retired;
    }

    public void resolveUnplaced() {
        java.util.List<LocalizedResourceTimer> pending = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (LocalizedResourceTimer timer : timers.values()) {
                if (!timer.hasLocalPlacement() && timer.hasGrid())
                    pending.add(timer);
            }
        } finally {
            lock.readLock().unlock();
        }
        boolean any = false;
        for (LocalizedResourceTimer timer : pending) {
            if (resolvePlacement(timer))
                any = true;
        }
        if (any)
            refreshTimerWindow();
    }
    
    /**
     * Refresh timer window from sync (called on UI thread).
     */
    public void refreshTimerWindowFromSync() {
        refreshTimerWindow();
    }

    private void scheduleResolve(LocalizedResourceTimer timer) {
        if (timer == null || timer.hasLocalPlacement() || !timer.hasGrid())
            return;
        long now = System.currentTimeMillis();
        Long last = lastResolveAttempt.get(timer.getResourceId());
        if (last != null && now - last < RESOLVE_RETRY_MS)
            return;
        if (!resolving.add(timer.getResourceId()))
            return;
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null
                || gui.mmap == null || gui.mmap.file == null) {
            resolving.remove(timer.getResourceId());
            return;
        }
        lastResolveAttempt.put(timer.getResourceId(), now);
        final String resourceId = timer.getResourceId();
        gui.ui.sess.glob.loader.defer(() -> {
            try {
                resolvePlacement(getTimer(resourceId));
            } finally {
                resolving.remove(resourceId);
            }
        }, null);
    }

    private boolean resolvePlacement(LocalizedResourceTimer timer) {
        if (timer == null || timer.hasLocalPlacement() || !timer.hasGrid())
            return timer != null && timer.hasLocalPlacement();
        if (gui == null || gui.mmap == null || gui.mmap.file == null)
            return false;
        MapFile file = gui.mmap.file;
        long segmentId;
        haven.Coord tileCoords;
        file.lock.readLock().lock();
        try {
            MapFile.GridInfo info = file.gridinfo.get(timer.getGridId());
            if (info == null)
                return false;
            segmentId = info.seg;
            tileCoords = info.sc.mul(MCache.cmaps).add(timer.getGridOffset());
        } catch (RuntimeException e) {
            return false;
        } finally {
            file.lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            LocalizedResourceTimer current = timers.get(timer.getResourceId());
            if (current == null || current.hasLocalPlacement() || !current.hasGrid())
                return current != null && current.hasLocalPlacement();
            timers.put(current.getResourceId(), current.withLocalPlace(segmentId, tileCoords));
            dirty = true;
            saveTimers();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean segmentExists(long segmentId) {
        if (gui == null || gui.mmap == null || gui.mmap.file == null)
            return true;
        MapFile file = gui.mmap.file;
        file.lock.readLock().lock();
        try {
            return file.segments.get(segmentId) != null;
        } catch (RuntimeException e) {
            return true;
        } finally {
            file.lock.readLock().unlock();
        }
    }

    private GridAnchor anchorFor(long segmentId, haven.Coord tileCoords) {
        if (tileCoords == null || gui == null || gui.mmap == null || gui.mmap.file == null)
            return null;
        MapFile file = gui.mmap.file;
        file.lock.readLock().lock();
        try {
            MapFile.Segment segment = file.segments.get(segmentId);
            if (segment == null)
                return null;
            Long gridId = segment.map.get(tileCoords.div(MCache.cmaps));
            if (gridId == null)
                return null;
            return new GridAnchor(gridId, tileCoords.mod(MCache.cmaps));
        } catch (RuntimeException e) {
            return null;
        } finally {
            file.lock.readLock().unlock();
        }
    }

    private static final class GridAnchor {
        final long gridId;
        final haven.Coord offset;

        GridAnchor(long gridId, haven.Coord offset) {
            this.gridId = gridId;
            this.offset = offset;
        }
    }
}
