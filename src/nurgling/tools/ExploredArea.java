package nurgling.tools;

import haven.*;
import nurgling.NConfig;
import nurgling.widgets.NMiniMap;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Tracks explored (visible) area on the minimap.
 * Uses grid-based boolean masks for efficient storage and fast updates.
 * Each grid (100x100 tiles) has its own mask marking explored tiles.
 * 
 * Supports session layers - temporary explored areas that can be created
 * and deleted without affecting the main persistent explored area.
 */
public class ExploredArea {
    // Global version tracking for cache invalidation (similar to TileHighlight.seq)
    // Note: This is only used for full reloads/clears, not for per-tile updates
    public static volatile long seq = 0;
    // Separate version tracking for session layer
    public static volatile long sessionSeq = 0;
    
    private static final int GRID_SIZE = 100; // MCache.cmaps.x
    private static final int MASK_SIZE = GRID_SIZE * GRID_SIZE;
    
    /**
     * Wrapper for grid mask with per-grid version tracking and cached hasAny.
     * This allows renderers to only regenerate textures for changed grids.
     */
    public static class GridMask {
        public final boolean[] mask;
        public volatile long seq;       // Per-grid version, incremented when this grid changes
        public volatile boolean hasAny; // Cached result of "does this mask have any true values"
        
        public GridMask() {
            this.mask = new boolean[MASK_SIZE];
            this.seq = 0;
            this.hasAny = false;
        }
        
        public GridMask(boolean[] existingMask) {
            this.mask = existingMask;
            this.seq = 0;
            this.hasAny = computeHasAny(existingMask);
        }
        
        private static boolean computeHasAny(boolean[] m) {
            for (boolean b : m) {
                if (b) return true;
            }
            return false;
        }
    }
    
    /**
     * Key for identifying a grid in a specific segment.
     */
    public static class GridKey {
        public final long segmentId;
        public final Coord gridCoord;  // Grid coordinate at data level 0
        
        public GridKey(long segmentId, Coord gridCoord) {
            this.segmentId = segmentId;
            this.gridCoord = gridCoord;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GridKey)) return false;
            GridKey key = (GridKey) o;
            return segmentId == key.segmentId && gridCoord.equals(key.gridCoord);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(segmentId, gridCoord);
        }
    }
    
    final NMiniMap miniMap;
    
    // Main storage: grid-based masks with per-grid seq (persistent)
    private final ConcurrentHashMap<GridKey, GridMask> gridMasks = new ConcurrentHashMap<>();
    
    // Session layer storage: grid-based masks with per-grid seq (temporary, not saved)
    private final ConcurrentHashMap<GridKey, GridMask> sessionGridMasks = new ConcurrentHashMap<>();
    
    // Flag indicating if session layer is active
    private volatile boolean sessionActive = false;
    
    /**
     * Get the appropriate NConfig instance (profile-specific if available)
     */
    private NConfig getConfig() {
        try {
            if (nurgling.NUtils.getUI() != null && nurgling.NUtils.getUI().core != null) {
                return nurgling.NUtils.getUI().core.config;
            }
        } catch (Exception e) {
            // Fallback to global config
        }
        return NConfig.current;
    }
    
    // Track last update position to avoid redundant updates
    private Coord lastTileUL, lastTileBR;
    private long lastSegmentId = -1;
    
    public ExploredArea(NMiniMap miniMap) {
        this.miniMap = miniMap;
        // Note: Don't load from file here! The profile may not be initialized yet.
        // Data is loaded later via reloadFromFile() when the profile is ready.
        // This prevents loading from the wrong path and losing profile-specific data.
    }
    
    /**
     * Update explored area with current view bounds.
     * This is called every tick when player moves.
     * Very fast - just sets bits in the mask, no complex Rectangle operations.
     * Also updates session layer if active.
     * 
     * Optimization: Uses per-grid seq tracking so only changed grids need texture regeneration.
     */
    public void updateExploredTiles(Coord tileUL, Coord tileBR, long segmentId) {
        // Skip if same as last update
        if (Objects.equals(tileUL, lastTileUL) && Objects.equals(tileBR, lastTileBR) && segmentId == lastSegmentId) {
            return;
        }
        
        lastTileUL = tileUL;
        lastTileBR = tileBR;
        lastSegmentId = segmentId;
        
        // Calculate which grids are affected
        Coord gridUL = tileUL.div(GRID_SIZE);
        Coord gridBR = tileBR.sub(1, 1).div(GRID_SIZE); // Inclusive end
        
        boolean anyChanged = false;
        boolean anySessionChanged = false;
        
        // Update each affected grid
        for (int gy = gridUL.y; gy <= gridBR.y; gy++) {
            for (int gx = gridUL.x; gx <= gridBR.x; gx++) {
                Coord gridCoord = new Coord(gx, gy);
                GridKey key = new GridKey(segmentId, gridCoord);
                
                // Get or create mask for this grid (main persistent layer)
                GridMask gridMask = gridMasks.computeIfAbsent(key, k -> new GridMask());
                boolean[] mask = gridMask.mask;
                
                // Get or create mask for session layer if active
                GridMask sessionGridMask = null;
                boolean[] sessionMask = null;
                if (sessionActive) {
                    sessionGridMask = sessionGridMasks.computeIfAbsent(key, k -> new GridMask());
                    sessionMask = sessionGridMask.mask;
                }
                
                // Calculate tile bounds within this grid
                Coord gridTileStart = gridCoord.mul(GRID_SIZE);
                int localULX = Math.max(0, tileUL.x - gridTileStart.x);
                int localULY = Math.max(0, tileUL.y - gridTileStart.y);
                int localBRX = Math.min(GRID_SIZE, tileBR.x - gridTileStart.x);
                int localBRY = Math.min(GRID_SIZE, tileBR.y - gridTileStart.y);
                
                boolean gridChanged = false;
                boolean sessionGridChanged = false;
                
                // Mark tiles as explored
                for (int y = localULY; y < localBRY; y++) {
                    for (int x = localULX; x < localBRX; x++) {
                        int idx = x + y * GRID_SIZE;
                        // Update main layer
                        if (!mask[idx]) {
                            mask[idx] = true;
                            gridChanged = true;
                        }
                        // Update session layer if active
                        if (sessionMask != null && !sessionMask[idx]) {
                            sessionMask[idx] = true;
                            sessionGridChanged = true;
                        }
                    }
                }
                
                // Update per-grid seq only if this specific grid changed
                if (gridChanged) {
                    gridMask.seq++;
                    gridMask.hasAny = true; // If we added tiles, hasAny is definitely true
                    anyChanged = true;
                }
                if (sessionGridChanged && sessionGridMask != null) {
                    sessionGridMask.seq++;
                    sessionGridMask.hasAny = true;
                    anySessionChanged = true;
                }
            }
        }
        
        // Only update global seq on actual changes (for file save tracking)
        if (anyChanged) {
            seq++;
            NConfig.needExploredUpdate();
        }
        if (anySessionChanged) {
            sessionSeq++;
            needSessionUpdate = true;
        }
    }
    
    // Flag for session save
    private volatile boolean needSessionUpdate = false;
    private long lastSessionSaveTime = 0;
    private static final long SESSION_SAVE_INTERVAL = 5000; // Save every 5 seconds max
    
    /**
     * Get explored GridMask for a specific grid at base level (dataLevel 0).
     * Used by MinimapExploredAreaRenderer for rendering.
     * Very fast - just returns the stored GridMask.
     * 
     * GridMask contains:
     * - mask: the boolean array
     * - seq: per-grid version for cache invalidation
     * - hasAny: cached result of whether mask has any true values
     * 
     * @param gridCoord Grid coordinate at base level
     * @param segmentId Segment ID
     * @param dataLevel Must be 0 (aggregation is done by renderer)
     * @return GridMask or null if no data
     */
    public GridMask getExploredMaskForGrid(Coord gridCoord, long segmentId, int dataLevel) {
        GridKey key = new GridKey(segmentId, gridCoord);
        return gridMasks.get(key);
    }
    
    /**
     * Clear all explored data.
     */
    public void clear() {
        if (!gridMasks.isEmpty()) {
            gridMasks.clear();
            lastTileUL = null;
            lastTileBR = null;
            lastSegmentId = -1;
            seq++; // Global seq for full clear
            NConfig.needExploredUpdate();
        }
    }
    
    /**
     * Check if session layer is currently active.
     */
    public boolean isSessionActive() {
        return sessionActive;
    }
    
    /**
     * Start a new session layer.
     * Clears any existing session data and starts fresh.
     * Resets last position to force immediate update of current view.
     */
    public void startSession() {
        sessionGridMasks.clear();
        sessionActive = true;
        // Reset last position to force immediate coloring of current view
        lastTileUL = null;
        lastTileBR = null;
        lastSegmentId = -1;
        sessionSeq++; // Global seq for session clear
        // Save session state
        saveSessionToFile();
    }
    
    /**
     * End and delete the session layer.
     * All session data is discarded.
     */
    public void endSession() {
        sessionGridMasks.clear();
        sessionActive = false;
        sessionSeq++; // Global seq for session clear
        // Delete session file
        deleteSessionFile();
    }
    
    /**
     * Get session GridMask for a specific grid at base level (dataLevel 0).
     * Used by MinimapExploredAreaRenderer for rendering session overlay.
     * 
     * @param gridCoord Grid coordinate at base level
     * @param segmentId Segment ID
     * @return GridMask or null if no data or session not active
     */
    public GridMask getSessionMaskForGrid(Coord gridCoord, long segmentId) {
        if (!sessionActive) {
            return null;
        }
        GridKey key = new GridKey(segmentId, gridCoord);
        return sessionGridMasks.get(key);
    }
    
    /**
     * Tick method - handles periodic session saving.
     */
    public void tick(double dt) {
        // Periodically save session data if needed
        if (needSessionUpdate && sessionActive) {
            long now = System.currentTimeMillis();
            if (now - lastSessionSaveTime > SESSION_SAVE_INTERVAL) {
                saveSessionToFile();
                needSessionUpdate = false;
                lastSessionSaveTime = now;
            }
        }
    }
    
    // Flag to track if async loading is in progress
    private volatile boolean loadingInProgress = false;
    
    // Executor for async loading
    private static final java.util.concurrent.ExecutorService loadExecutor = 
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ExploredArea-Loader");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    
    /**
     * Check if async loading is in progress.
     */
    public boolean isLoadingInProgress() {
        return loadingInProgress;
    }
    
    /**
     * Reload explored area data from file asynchronously.
     * Call this after profile initialization to load profile-specific data.
     * This is the preferred method for startup to avoid blocking the game.
     */
    public void reloadFromFileAsync() {
        loadingInProgress = true;
        loadExecutor.submit(() -> {
            try {
                reloadFromFileInternal();
            } finally {
                loadingInProgress = false;
            }
        });
    }
    
    /**
     * Reload explored area data from file (synchronous version).
     * Call this after profile initialization to load profile-specific data.
     * Merges file data with any in-memory data (in case exploration happened before profile init).
     */
    public void reloadFromFile() {
        reloadFromFileInternal();
    }
    
    /**
     * Internal implementation of file reload.
     */
    private void reloadFromFileInternal() {
        // Save current in-memory data before loading
        Map<GridKey, GridMask> currentData = new HashMap<>(gridMasks);
        
        // Load from file
        gridMasks.clear();
        loadFromFile();
        
        // Merge in-memory data back (OR operation - keep explored tiles from both)
        for (Map.Entry<GridKey, GridMask> entry : currentData.entrySet()) {
            GridKey key = entry.getKey();
            GridMask memoryGridMask = entry.getValue();
            boolean[] memoryMask = memoryGridMask.mask;
            
            GridMask fileGridMask = gridMasks.get(key);
            if (fileGridMask == null) {
                // Grid only in memory, add it
                gridMasks.put(key, memoryGridMask);
            } else {
                // Merge: OR the masks
                boolean changed = false;
                for (int i = 0; i < MASK_SIZE; i++) {
                    if (memoryMask[i] && !fileGridMask.mask[i]) {
                        fileGridMask.mask[i] = true;
                        changed = true;
                    }
                }
                if (changed) {
                    fileGridMask.seq++;
                    fileGridMask.hasAny = true;
                }
            }
        }
        
        // Also reload session data (session doesn't need merge - it's temporary)
        sessionGridMasks.clear();
        loadSessionFromFile();
        
        seq++; // Global seq for full reload
    }
    
    /**
     * Load explored area from JSON file.
     */
    private void loadFromFile() {
        // Use profile-specific config from NCore if available, otherwise fallback to global
        NConfig config = getConfig();
        File file = new File(config.getExploredPath());
        if (!file.exists()) {
            return;
        }
        
        try {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            }
            
            if (contentBuilder.length() == 0) {
                return;
            }
            
            JSONObject json = new JSONObject(contentBuilder.toString());
            if (!json.has("grids")) {
                return;
            }
            
            JSONArray gridsArray = json.getJSONArray("grids");
            for (int i = 0; i < gridsArray.length(); i++) {
                JSONObject gridJson = gridsArray.getJSONObject(i);
                
                long segmentId = gridJson.getLong("seg");
                int gx = gridJson.getInt("gx");
                int gy = gridJson.getInt("gy");
                
                GridKey key = new GridKey(segmentId, new Coord(gx, gy));
                
                // Decode RLE compressed mask
                String rle = gridJson.getString("mask");
                boolean[] mask = decodeRLE(rle);
                
                if (mask != null) {
                    gridMasks.put(key, new GridMask(mask));
                }
            }
            
            seq++; // Global seq for full load
        } catch (Exception e) {
            // Ignore load errors
        }
    }
    
    /**
     * Save explored area to JSON file.
     */
    public JSONObject toJson() {
        JSONArray gridsArray = new JSONArray();
        
        for (Map.Entry<GridKey, GridMask> entry : gridMasks.entrySet()) {
            GridKey key = entry.getKey();
            GridMask gridMask = entry.getValue();
            
            // Skip empty masks (use cached hasAny for efficiency)
            if (!gridMask.hasAny) {
                continue;
            }
            
            JSONObject gridJson = new JSONObject();
            gridJson.put("seg", key.segmentId);
            gridJson.put("gx", key.gridCoord.x);
            gridJson.put("gy", key.gridCoord.y);
            
            // Encode mask with RLE compression
            gridJson.put("mask", encodeRLE(gridMask.mask));
            
            gridsArray.put(gridJson);
        }
        
        JSONObject doc = new JSONObject();
        doc.put("grids", gridsArray);
        return doc;
    }
    
    /**
     * Convert session data to JSON for saving.
     */
    private JSONObject sessionToJson() {
        JSONArray gridsArray = new JSONArray();
        
        for (Map.Entry<GridKey, GridMask> entry : sessionGridMasks.entrySet()) {
            GridKey key = entry.getKey();
            GridMask gridMask = entry.getValue();
            
            // Skip empty masks (use cached hasAny for efficiency)
            if (!gridMask.hasAny) {
                continue;
            }
            
            JSONObject gridJson = new JSONObject();
            gridJson.put("seg", key.segmentId);
            gridJson.put("gx", key.gridCoord.x);
            gridJson.put("gy", key.gridCoord.y);
            
            // Encode mask with RLE compression
            gridJson.put("mask", encodeRLE(gridMask.mask));
            
            gridsArray.put(gridJson);
        }
        
        JSONObject doc = new JSONObject();
        doc.put("active", sessionActive);
        doc.put("grids", gridsArray);
        return doc;
    }
    
    /**
     * Save session data to file.
     */
    private void saveSessionToFile() {
        NConfig config = getConfig();
        try {
            FileWriter f = new FileWriter(config.getSessionExploredPath(), StandardCharsets.UTF_8);
            sessionToJson().write(f);
            f.close();
        } catch (IOException e) {
            // Ignore save errors
        }
    }
    
    /**
     * Load session data from file.
     */
    private void loadSessionFromFile() {
        NConfig config = getConfig();
        File file = new File(config.getSessionExploredPath());
        if (!file.exists()) {
            return;
        }
        
        try {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            }
            
            if (contentBuilder.length() == 0) {
                return;
            }
            
            JSONObject json = new JSONObject(contentBuilder.toString());
            
            // Load active state
            if (json.has("active")) {
                sessionActive = json.getBoolean("active");
            }
            
            if (!json.has("grids")) {
                return;
            }
            
            JSONArray gridsArray = json.getJSONArray("grids");
            for (int i = 0; i < gridsArray.length(); i++) {
                JSONObject gridJson = gridsArray.getJSONObject(i);
                
                long segmentId = gridJson.getLong("seg");
                int gx = gridJson.getInt("gx");
                int gy = gridJson.getInt("gy");
                
                GridKey key = new GridKey(segmentId, new Coord(gx, gy));
                
                // Decode RLE compressed mask
                String rle = gridJson.getString("mask");
                boolean[] mask = decodeRLE(rle);
                
                if (mask != null) {
                    sessionGridMasks.put(key, new GridMask(mask));
                }
            }
            
            sessionSeq++; // Global seq for full load
        } catch (Exception e) {
            // Ignore load errors
        }
    }
    
    /**
     * Delete session file.
     */
    private void deleteSessionFile() {
        NConfig config = getConfig();
        try {
            File file = new File(config.getSessionExploredPath());
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // Ignore delete errors
        }
    }
    
    /**
     * Encode boolean mask with RLE (Run-Length Encoding) for compression.
     * Format: "startBit:count1,count2,count3..." where startBit (0 or 1) indicates first value.
     */
    private String encodeRLE(boolean[] mask) {
        StringBuilder sb = new StringBuilder();
        
        // Store the starting value (0 for false, 1 for true)
        sb.append(mask[0] ? '1' : '0').append(':');
        
        boolean currentValue = mask[0];
        int count = 1;
        
        for (int i = 1; i < mask.length; i++) {
            if (mask[i] == currentValue) {
                count++;
            } else {
                sb.append(count).append(',');
                currentValue = mask[i];
                count = 1;
            }
        }
        sb.append(count); // Last run
        
        return sb.toString();
    }
    
    /**
     * Decode RLE compressed mask.
     */
    private boolean[] decodeRLE(String rle) {
        try {
            // Split by colon to get starting bit and run counts
            String[] mainParts = rle.split(":", 2);
            if (mainParts.length != 2) {
                return null;
            }
            
            // Get starting value (0 = false, 1 = true)
            boolean currentValue = mainParts[0].equals("1");
            
            // Parse run counts
            String[] parts = mainParts[1].split(",");
            boolean[] mask = new boolean[MASK_SIZE];
            
            int idx = 0;
            
            for (String part : parts) {
                int count = Integer.parseInt(part.trim());
                for (int i = 0; i < count && idx < MASK_SIZE; i++) {
                    mask[idx++] = currentValue;
                }
                currentValue = !currentValue; // Toggle
            }
            
            return mask;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Merge in-memory data with existing file data and save with file locking.
     * This prevents data loss when multiple clients run simultaneously.
     * 
     * Algorithm:
     * 1. Acquire exclusive file lock
     * 2. Read existing data from file
     * 3. Merge: for each grid, OR the masks together (explored in file OR explored in memory)
     * 4. Write merged result
     * 5. Update in-memory data with merged result
     * 6. Release lock
     */
    public void mergeAndSaveToFile(String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Create lock file to coordinate access
        File lockFile = new File(filePath + ".lock");
        
        try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
             FileChannel channel = raf.getChannel()) {
            
            // Acquire exclusive lock (blocks until available)
            FileLock lock = null;
            try {
                lock = channel.tryLock();
                if (lock == null) {
                    // Could not acquire lock immediately, wait a bit and try again
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    lock = channel.tryLock();
                }
                
                if (lock == null) {
                    // Still no lock, fall back to simple save
                    System.err.println("Could not acquire file lock, saving without merge");
                    saveWithoutMerge(filePath);
                    return;
                }
                
                // Read existing data from file
                Map<GridKey, boolean[]> diskData = readFromDisk(filePath);
                
                // Merge: OR the masks together
                // First, copy disk data to merged result
                Map<GridKey, boolean[]> mergedData = new HashMap<>();
                for (Map.Entry<GridKey, boolean[]> entry : diskData.entrySet()) {
                    boolean[] copy = new boolean[MASK_SIZE];
                    System.arraycopy(entry.getValue(), 0, copy, 0, MASK_SIZE);
                    mergedData.put(entry.getKey(), copy);
                }
                
                // Then, merge in-memory data
                for (Map.Entry<GridKey, GridMask> entry : gridMasks.entrySet()) {
                    GridKey key = entry.getKey();
                    boolean[] memoryMask = entry.getValue().mask;
                    
                    boolean[] existingMask = mergedData.get(key);
                    if (existingMask == null) {
                        // New grid, just add it
                        boolean[] copy = new boolean[MASK_SIZE];
                        System.arraycopy(memoryMask, 0, copy, 0, MASK_SIZE);
                        mergedData.put(key, copy);
                    } else {
                        // Merge: OR the masks
                        for (int i = 0; i < MASK_SIZE; i++) {
                            existingMask[i] = existingMask[i] || memoryMask[i];
                        }
                    }
                }
                
                // Write merged result to file
                JSONObject doc = toJsonFromData(mergedData);
                try (FileWriter f = new FileWriter(filePath, StandardCharsets.UTF_8)) {
                    doc.write(f);
                }
                
                // Update in-memory data with merged result (so we have the latest data)
                // This is important to prevent re-saving stale data
                for (Map.Entry<GridKey, boolean[]> entry : mergedData.entrySet()) {
                    GridKey key = entry.getKey();
                    boolean[] mergedMask = entry.getValue();
                    GridMask currentGridMask = gridMasks.get(key);
                    
                    if (currentGridMask == null) {
                        // Grid from disk that we didn't have
                        gridMasks.put(key, new GridMask(mergedMask));
                    } else {
                        // Update our mask with merged data
                        boolean changed = false;
                        for (int i = 0; i < MASK_SIZE; i++) {
                            if (mergedMask[i] && !currentGridMask.mask[i]) {
                                currentGridMask.mask[i] = true;
                                changed = true;
                            }
                        }
                        if (changed) {
                            currentGridMask.seq++;
                            currentGridMask.hasAny = true;
                        }
                    }
                }
                
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            // If locking fails, fall back to simple save
            System.err.println("Error during merge-save, falling back to simple save: " + e.getMessage());
            saveWithoutMerge(filePath);
        }
    }
    
    /**
     * Simple save without merge (fallback when locking fails).
     */
    private void saveWithoutMerge(String filePath) throws IOException {
        try (FileWriter f = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            toJson().write(f);
        }
    }
    
    /**
     * Read grid data from disk file.
     */
    private Map<GridKey, boolean[]> readFromDisk(String filePath) {
        Map<GridKey, boolean[]> result = new HashMap<>();
        
        File file = new File(filePath);
        if (!file.exists()) {
            return result;
        }
        
        try {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            }
            
            if (contentBuilder.length() == 0) {
                return result;
            }
            
            JSONObject json = new JSONObject(contentBuilder.toString());
            if (!json.has("grids")) {
                return result;
            }
            
            JSONArray gridsArray = json.getJSONArray("grids");
            for (int i = 0; i < gridsArray.length(); i++) {
                JSONObject gridJson = gridsArray.getJSONObject(i);
                
                long segmentId = gridJson.getLong("seg");
                int gx = gridJson.getInt("gx");
                int gy = gridJson.getInt("gy");
                
                GridKey key = new GridKey(segmentId, new Coord(gx, gy));
                
                // Decode RLE compressed mask
                String rle = gridJson.getString("mask");
                boolean[] mask = decodeRLE(rle);
                
                if (mask != null) {
                    result.put(key, mask);
                }
            }
        } catch (Exception e) {
            // Ignore load errors, return what we have
        }
        
        return result;
    }
    
    /**
     * Convert given data to JSON (for saving merged data).
     */
    private JSONObject toJsonFromData(Map<GridKey, boolean[]> data) {
        JSONArray gridsArray = new JSONArray();
        
        for (Map.Entry<GridKey, boolean[]> entry : data.entrySet()) {
            GridKey key = entry.getKey();
            boolean[] mask = entry.getValue();
            
            // Skip empty masks
            boolean hasAny = false;
            for (boolean b : mask) {
                if (b) { hasAny = true; break; }
            }
            if (!hasAny) {
                continue;
            }
            
            JSONObject gridJson = new JSONObject();
            gridJson.put("seg", key.segmentId);
            gridJson.put("gx", key.gridCoord.x);
            gridJson.put("gy", key.gridCoord.y);
            
            // Encode mask with RLE compression
            gridJson.put("mask", encodeRLE(mask));
            
            gridsArray.put(gridJson);
        }
        
        JSONObject doc = new JSONObject();
        doc.put("grids", gridsArray);
        return doc;
    }
}

