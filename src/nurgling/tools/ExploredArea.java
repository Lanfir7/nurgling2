package nurgling.tools;

import haven.*;
import nurgling.NConfig;
import nurgling.widgets.NMiniMap;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks explored (visible) area on the minimap.
 * Uses grid-based boolean masks for efficient storage and fast updates.
 * Each grid (100x100 tiles) has its own mask marking explored tiles.
 * 
 * Supports session layers - temporary explored areas that can be created
 * and deleted without affecting the main persistent explored area.
 */
public class ExploredArea {
    // Version tracking for cache invalidation (similar to TileHighlight.seq)
    public static volatile long seq = 0;
    // Separate version tracking for session layer
    public static volatile long sessionSeq = 0;
    
    private static final int GRID_SIZE = 100; // MCache.cmaps.x
    private static final int MASK_SIZE = GRID_SIZE * GRID_SIZE;
    
    /**
     * Key for identifying a grid in a specific segment.
     */
    private static class GridKey {
        final long segmentId;
        final Coord gridCoord;  // Grid coordinate at data level 0
        
        GridKey(long segmentId, Coord gridCoord) {
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
    
    // Main storage: grid-based masks (persistent)
    private final ConcurrentHashMap<GridKey, boolean[]> gridMasks = new ConcurrentHashMap<>();
    
    // Session layer storage: grid-based masks (temporary, not saved)
    private final ConcurrentHashMap<GridKey, boolean[]> sessionGridMasks = new ConcurrentHashMap<>();
    
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
        
        boolean changed = false;
        boolean sessionChanged = false;
        
        // Update each affected grid
        for (int gy = gridUL.y; gy <= gridBR.y; gy++) {
            for (int gx = gridUL.x; gx <= gridBR.x; gx++) {
                Coord gridCoord = new Coord(gx, gy);
                GridKey key = new GridKey(segmentId, gridCoord);
                
                // Get or create mask for this grid (main persistent layer)
                boolean[] mask = gridMasks.computeIfAbsent(key, k -> new boolean[MASK_SIZE]);
                
                // Get or create mask for session layer if active
                boolean[] sessionMask = null;
                if (sessionActive) {
                    sessionMask = sessionGridMasks.computeIfAbsent(key, k -> new boolean[MASK_SIZE]);
                }
                
                // Calculate tile bounds within this grid
                Coord gridTileStart = gridCoord.mul(GRID_SIZE);
                int localULX = Math.max(0, tileUL.x - gridTileStart.x);
                int localULY = Math.max(0, tileUL.y - gridTileStart.y);
                int localBRX = Math.min(GRID_SIZE, tileBR.x - gridTileStart.x);
                int localBRY = Math.min(GRID_SIZE, tileBR.y - gridTileStart.y);
                
                // Mark tiles as explored
                for (int y = localULY; y < localBRY; y++) {
                    for (int x = localULX; x < localBRX; x++) {
                        int idx = x + y * GRID_SIZE;
                        // Update main layer
                        if (!mask[idx]) {
                            mask[idx] = true;
                            changed = true;
                        }
                        // Update session layer if active
                        if (sessionMask != null && !sessionMask[idx]) {
                            sessionMask[idx] = true;
                            sessionChanged = true;
                        }
                    }
                }
            }
        }
        
        if (changed) {
            seq++;
            NConfig.needExploredUpdate();
        }
        if (sessionChanged) {
            sessionSeq++;
            needSessionUpdate = true;
        }
    }
    
    // Flag for session save
    private volatile boolean needSessionUpdate = false;
    private long lastSessionSaveTime = 0;
    private static final long SESSION_SAVE_INTERVAL = 5000; // Save every 5 seconds max
    
    public static class GridMask {
        public final boolean[] mask;
        public volatile long seq;
        public volatile boolean hasAny;

        public GridMask(boolean[] existingMask) {
            this.mask = existingMask;
            this.seq = ExploredArea.seq;
            boolean any = false;
            if (existingMask != null) {
                for (boolean b : existingMask) {
                    if (b) { any = true; break; }
                }
            }
            this.hasAny = any;
        }
    }

    /**
     * Get explored mask for a specific grid at base level (dataLevel 0).
     * Used by MinimapExploredAreaRenderer for rendering.
     * Very fast - just returns the stored mask.
     * 
     * @param gridCoord Grid coordinate at base level
     * @param segmentId Segment ID
     * @param dataLevel Must be 0 (aggregation is done by renderer)
     * @return boolean[] mask or null if no data
     */
    public GridMask getExploredMaskForGrid(Coord gridCoord, long segmentId, int dataLevel) {
        GridKey key = new GridKey(segmentId, gridCoord);
        boolean[] mask = gridMasks.get(key);
        return mask == null ? null : new GridMask(mask);
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
            seq++;
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
        sessionSeq++;
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
        sessionSeq++;
        // Delete session file
        deleteSessionFile();
    }
    
    /**
     * Get session mask for a specific grid at base level (dataLevel 0).
     * Used by MinimapExploredAreaRenderer for rendering session overlay.
     * 
     * @param gridCoord Grid coordinate at base level
     * @param segmentId Segment ID
     * @return boolean[] mask or null if no data or session not active
     */
    public GridMask getSessionMaskForGrid(Coord gridCoord, long segmentId) {
        if (!sessionActive) {
            return null;
        }
        GridKey key = new GridKey(segmentId, gridCoord);
        boolean[] mask = sessionGridMasks.get(key);
        return mask == null ? null : new GridMask(mask);
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
    
    /**
     * Reload explored area data from file.
     * Call this after profile initialization to load profile-specific data.
     * Merges file data with any in-memory data (in case exploration happened before profile init).
     */
    public boolean isLoadingInProgress() {
        return false;
    }

    public void reloadFromFileAsync() {
        new Thread(this::reloadFromFile, "ExploredArea-Reload").start();
    }

    public static void resetExecutor() {
    }

    public void reloadFromFile() {
        // Save current in-memory data before loading
        Map<GridKey, boolean[]> currentData = new HashMap<>(gridMasks);
        
        // Load from file
        gridMasks.clear();
        loadFromFile();
        
        // Merge in-memory data back (OR operation - keep explored tiles from both)
        for (Map.Entry<GridKey, boolean[]> entry : currentData.entrySet()) {
            GridKey key = entry.getKey();
            boolean[] memoryMask = entry.getValue();
            
            boolean[] fileMask = gridMasks.get(key);
            if (fileMask == null) {
                // Grid only in memory, add it
                gridMasks.put(key, memoryMask);
            } else {
                // Merge: OR the masks
                for (int i = 0; i < MASK_SIZE; i++) {
                    fileMask[i] = fileMask[i] || memoryMask[i];
                }
            }
        }
        
        // Also reload session data (session doesn't need merge - it's temporary)
        sessionGridMasks.clear();
        loadSessionFromFile();
        
        seq++;
    }
    
    /**
     * Load explored area from JSON file.
     */
    private void loadFromFile() {
        // Use profile-specific config from NCore if available, otherwise fallback to global
        NConfig config = getConfig();

        try {
            String content = NFileUtils.readWithBackupFallback(config.getExploredPath());
            if (content == null || content.isEmpty()) {
                return;
            }

            JSONObject json = new JSONObject(content);
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
                    gridMasks.put(key, mask);
                }
            }
            
            seq++;
        } catch (Exception e) {
            // Ignore load errors
        }
    }
    
    /**
     * Save explored area to JSON file.
     */
    public JSONObject toJson() {
        JSONArray gridsArray = new JSONArray();
        
        for (Map.Entry<GridKey, boolean[]> entry : gridMasks.entrySet()) {
            GridKey key = entry.getKey();
            boolean[] mask = entry.getValue();
            
            // Skip empty masks
            if (!hasAnyExploredTiles(mask)) {
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
    
    /**
     * Convert session data to JSON for saving.
     */
    private JSONObject sessionToJson() {
        JSONArray gridsArray = new JSONArray();
        
        for (Map.Entry<GridKey, boolean[]> entry : sessionGridMasks.entrySet()) {
            GridKey key = entry.getKey();
            boolean[] mask = entry.getValue();
            
            // Skip empty masks
            if (!hasAnyExploredTiles(mask)) {
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
            NFileUtils.writeAtomically(config.getSessionExploredPath(), sessionToJson().toString());
        } catch (IOException e) {
            // Ignore save errors
        }
    }
    
    /**
     * Load session data from file.
     */
    private void loadSessionFromFile() {
        NConfig config = getConfig();

        try {
            String content = NFileUtils.readWithBackupFallback(config.getSessionExploredPath());
            if (content == null || content.isEmpty()) {
                return;
            }

            JSONObject json = new JSONObject(content);
            
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
                    sessionGridMasks.put(key, mask);
                }
            }
            
            sessionSeq++;
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
     * Check if mask has any explored tiles.
     */
    private boolean hasAnyExploredTiles(boolean[] mask) {
        for (boolean tile : mask) {
            if (tile) return true;
        }
        return false;
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
        byte[] mergedBytes = NFileUtils.updateAtomically(filePath,
                this::isValidStoredData,
                (target, primary) -> toJsonFromData(mergeWithMemory(readFromBytes(primary)))
                        .toString().getBytes(StandardCharsets.UTF_8));
        Map<GridKey, boolean[]> mergedData = readFromBytes(mergedBytes);
        for (Map.Entry<GridKey, boolean[]> entry : mergedData.entrySet()) {
            GridKey key = entry.getKey();
            boolean[] mergedMask = entry.getValue();
            boolean[] currentMask = gridMasks.get(key);
            if (currentMask == null) {
                gridMasks.put(key, mergedMask);
            } else {
                System.arraycopy(mergedMask, 0, currentMask, 0, MASK_SIZE);
            }
        }
    }

    private Map<GridKey, boolean[]> mergeWithMemory(Map<GridKey, boolean[]> diskData) {
        Map<GridKey, boolean[]> mergedData = new HashMap<>();
        for (Map.Entry<GridKey, boolean[]> entry : diskData.entrySet())
            mergedData.put(entry.getKey(), Arrays.copyOf(entry.getValue(), MASK_SIZE));
        for (Map.Entry<GridKey, boolean[]> entry : gridMasks.entrySet()) {
            boolean[] mergedMask = mergedData.computeIfAbsent(entry.getKey(),
                    ignored -> new boolean[MASK_SIZE]);
            boolean[] memoryMask = entry.getValue();
            for (int i = 0; i < MASK_SIZE; i++)
                mergedMask[i] |= memoryMask[i];
        }
        return mergedData;
    }

    private boolean isValidStoredData(byte[] content) {
        if(content == null || content.length == 0)
            return false;
        try {
            return new JSONObject(new String(content, StandardCharsets.UTF_8)).has("grids");
        } catch(Exception e) {
            return false;
        }
    }

    private Map<GridKey, boolean[]> readFromBytes(byte[] bytes) {
        Map<GridKey, boolean[]> result = new HashMap<>();
        if(bytes == null || bytes.length == 0)
            return result;
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
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
            if (!hasAnyExploredTiles(mask)) {
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
