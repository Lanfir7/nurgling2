package nurgling.navigation;

import haven.Coord2d;
import haven.Pair;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.PathFinder;
import nurgling.areas.NArea;

/**
 * Helper utilities for area navigation.
 * Provides methods for checking area reachability and finding optimal paths to area corners.
 */
public class AreaNavigationHelper {
    
    /**
     * Get the 4 corners of an area as Coord2d array.
     * First tries live getRCArea(), then falls back to stored ChunkNav data.
     * @return array of 4 corners [top-left, bottom-right, bottom-left, top-right], or null if area bounds unavailable
     */
    public static Coord2d[] getAreaCorners(NArea area) {
        if (area == null) {
            return null;
        }
        
        // Try live getRCArea() first (works when area is visible)
        Pair<Coord2d, Coord2d> rcArea = area.getRCArea();
        
        if (rcArea == null) {
            // Fallback: try to get from stored ChunkNav data
            rcArea = getAreaCornersFromStoredData(area);
        }
        
        if (rcArea == null) {
            return null;
        }
        
        return new Coord2d[] {
            rcArea.a,                                        // top-left
            rcArea.b,                                        // bottom-right
            Coord2d.of(rcArea.a.x, rcArea.b.y),             // bottom-left
            Coord2d.of(rcArea.b.x, rcArea.a.y)              // top-right
        };
    }
    
    /**
     * Get area bounds from stored ChunkNav data when area is not visible.
     * Uses worldTileOrigin from recorded chunks to calculate world coordinates.
     */
    private static Pair<Coord2d, Coord2d> getAreaCornersFromStoredData(NArea area) {
        if (area == null) {
            return null;
        }
        if (area.space == null || area.space.space == null || area.space.space.isEmpty()) {
            return null;
        }
        
        try {
            // Get ChunkNavManager
            if (NUtils.getGameUI() == null || NUtils.getGameUI().map == null) {
                return null;
            }
            
            NMapView mapView = (NMapView) NUtils.getGameUI().map;
            ChunkNavManager chunkNav = mapView.getChunkNavManager();
            if (chunkNav == null || !chunkNav.isInitialized()) {
                return null;
            }
            
            ChunkNavGraph graph = chunkNav.getGraph();
            if (graph == null) {
                return null;
            }
            
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            int foundChunks = 0;
            
            for (java.util.Map.Entry<Long, NArea.VArea> entry : area.space.space.entrySet()) {
                long gridId = entry.getKey();
                NArea.VArea varea = entry.getValue();
                
                if (varea == null || varea.area == null) {
                    continue;
                }
                
                // Try to get worldTileOrigin from stored chunk data
                ChunkNavData chunk = graph.getChunk(gridId);
                if (chunk == null || chunk.worldTileOrigin == null) {
                    continue;
                }
                
                // Calculate world tile coordinates
                haven.Coord ul = chunk.worldTileOrigin.add(varea.area.ul);
                haven.Coord br = chunk.worldTileOrigin.add(varea.area.br);
                
                minX = Math.min(minX, ul.x);
                minY = Math.min(minY, ul.y);
                maxX = Math.max(maxX, br.x);
                maxY = Math.max(maxY, br.y);
                foundChunks++;
            }
            
            if (foundChunks == 0) {
                return null;
            }
            
            // Convert tile coords to world coords
            Coord2d begin = new haven.Coord(minX, minY).mul(haven.MCache.tilesz);
            Coord2d end = new haven.Coord(maxX - 1, maxY - 1).mul(haven.MCache.tilesz).add(haven.MCache.tilesz);
            
            return new Pair<>(begin, end);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Find the shortest path to any of the 4 corners of an area.
     * Plans paths to all corners in parallel and returns the shortest one.
     * Uses planToAreaCorner which works correctly across different layers/areas.
     */
    public static ChunkPath findShortestPathToAreaCorners(NArea area, ChunkNavManager chunkNav) throws InterruptedException {
        if (area == null || area.space == null || area.space.space == null || area.space.space.isEmpty()) {
            return chunkNav.planToArea(area);
        }
        
        // Plan paths to all 4 corners in parallel using planToAreaCorner (gridId + local coords)
        final ChunkPath[] paths = new ChunkPath[4];
        Thread[] threads = new Thread[4];
        
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                paths[idx] = chunkNav.planToAreaCorner(area, idx);
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread t : threads) {
            t.join();
        }
        
        // Find the shortest path
        ChunkPath bestPath = null;
        float bestCost = Float.MAX_VALUE;
        
        for (int i = 0; i < 4; i++) {
            ChunkPath path = paths[i];
            if (path != null && path.totalCost < bestCost) {
                bestPath = path;
                bestCost = path.totalCost;
            }
        }
        
        if (bestPath == null) {
            return chunkNav.planToArea(area);
        }
        
        return bestPath;
    }
    
    /**
     * Check if player is already inside or very close to the area.
     * 
     * This is NOT about "can PathFinder reach it" - it's about "are we already there".
     * If we're not inside or close, we need ChunkNav to navigate there first.
     * 
     * Returns true only if:
     * 1. Area is visible (grid loaded)
     * 2. Player is inside the area bounds OR within 3 tiles of the area edge
     */
    public static boolean isAreaReachableByLocalPF(NArea area) throws InterruptedException {
        return isAreaReachableByLocalPF(area, NUtils.getGameUI());
    }

    /** Check reachability using the given session's player and map (for macros on background window). */
    public static boolean isAreaReachableByLocalPF(NArea area, nurgling.NGameUI gui) throws InterruptedException {
        if (area == null || gui == null || gui.map == null) return false;
        haven.Gob player = gui.map.player();
        if (player == null) return false;
        
        if (!area.isVisible()) {
            return false;
        }
        
        Pair<Coord2d, Coord2d> rcArea = area.getRCArea();
        if (rcArea == null) {
            return false;
        }
        
        Coord2d playerPos = player.rc;
        
        // Check if player is inside the area bounds
        if (playerPos.x >= rcArea.a.x && playerPos.x <= rcArea.b.x &&
            playerPos.y >= rcArea.a.y && playerPos.y <= rcArea.b.y) {
            return true;  // Player is inside the area
        }
        
        // Check if player is within 3 tiles of any edge
        double tileSize = haven.MCache.tilesz.x;
        double closeDistance = tileSize * 3;
        
        // Expand area bounds by closeDistance and check if player is within
        double expandedMinX = rcArea.a.x - closeDistance;
        double expandedMinY = rcArea.a.y - closeDistance;
        double expandedMaxX = rcArea.b.x + closeDistance;
        double expandedMaxY = rcArea.b.y + closeDistance;
        
        if (playerPos.x >= expandedMinX && playerPos.x <= expandedMaxX &&
            playerPos.y >= expandedMinY && playerPos.y <= expandedMaxY) {
            // Player is close - now verify with PathFinder that we can actually reach
            // Check center of area
            Coord2d center = new Coord2d((rcArea.a.x + rcArea.b.x) / 2, (rcArea.a.y + rcArea.b.y) / 2);
            return PathFinder.isAvailable(center);
        }
        
        // Player is too far - need ChunkNav
        return false;
    }
}
