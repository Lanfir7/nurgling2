package nurgling.overlays;

import haven.*;
import nurgling.GhostAlpha;
import nurgling.NHitBox;
import nurgling.NUtils;
import nurgling.pf.NHitBoxD;

import java.util.*;

/**
 * Ghost preview overlay showing where buildings will be placed using real Gobs with models
 * Similar to BlueprintPlob but for buildings
 */
public class BuildGhostPreview extends GAttrib {
    private Pair<Coord2d, Coord2d> area;
    private NHitBox buildingHitBox;
    private Indir<Resource> buildingResource;
    private Message spriteData;
    private List<Gob> ghostGobs = new ArrayList<>();
    private Glob glob;
    private double rotationAngle = 0.0;  // Rotation angle in radians
    private boolean gridMode = false;  // Grid mode: place objects at tile centers

    public BuildGhostPreview(Gob owner, Pair<Coord2d, Coord2d> area, NHitBox hitBox, Indir<Resource> resource) {
        this(owner, area, hitBox, resource, 0, Message.nil);
    }
    
    public BuildGhostPreview(Gob owner, Pair<Coord2d, Coord2d> area, NHitBox hitBox, Indir<Resource> resource, int rotationCount) {
        this(owner, area, hitBox, resource, rotationCount, Message.nil);
    }
    
    public BuildGhostPreview(Gob owner, Pair<Coord2d, Coord2d> area, NHitBox hitBox, Indir<Resource> resource, int rotationCount, Message sdt) {
        super(owner);
        this.area = area;
        this.buildingHitBox = hitBox;
        this.buildingResource = resource;
        this.spriteData = (sdt != null) ? sdt : Message.nil;
        this.glob = owner.glob;
        this.rotationAngle = (rotationCount * Math.PI / 2.0);  // Convert rotation count to radians
        if (area != null && hitBox != null && resource != null) {
            calculateGhostPositions();
        }
    }

    /**
     * Set grid mode (place objects at tile centers)
     */
    public void setGridMode(boolean gridMode) {
        if (this.gridMode != gridMode) {
            this.gridMode = gridMode;
            // Recalculate positions when grid mode changes
            if (area != null && buildingHitBox != null && buildingResource != null) {
                calculateGhostPositions();
            }
        }
    }
    
    /**
     * Get grid mode state
     */
    public boolean getGridMode() {
        return gridMode;
    }

    /**
     * Calculate all valid building positions using the same logic as Finder.getFreePlace()
     */
    private void calculateGhostPositions() {
        // Clean up existing ghosts
        removeGhosts();

        if (buildingHitBox == null || area == null || buildingResource == null) {
            return;
        }

        // Find all obstacles in the area (same as Finder.getFreePlace)
        ArrayList<NHitBoxD> obstacles = findObstacles();

        // Track placed buildings to avoid showing overlaps
        ArrayList<NHitBoxD> placedBuildings = new ArrayList<>();

        if (gridMode) {
            // Grid mode: place objects at tile centers
            calculateGhostPositionsGrid(obstacles, placedBuildings);
        } else {
            // Normal mode: pixel-by-pixel search (tight packing)
            calculateGhostPositionsNormal(obstacles, placedBuildings);
        }
    }
    
    /**
     * Calculate positions in grid mode (tile centers)
     */
    private void calculateGhostPositionsGrid(ArrayList<NHitBoxD> obstacles, ArrayList<NHitBoxD> placedBuildings) {
        // Get rotated hitbox dimensions
        NHitBoxD tempBox = new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, Coord2d.of(0), rotationAngle);
        Coord2d rotatedUL = tempBox.getCircumscribedUL();
        Coord2d rotatedBR = tempBox.getCircumscribedBR();
        Coord hitboxSize = rotatedBR.sub(rotatedUL).floor();
        
        // Calculate tile bounds
        Coord tileBegin = area.a.floor(MCache.tilesz);
        Coord tileEnd = area.b.sub(1, 1).floor(MCache.tilesz);
        
        // Iterate through tiles
        for (int tx = tileBegin.x; tx <= tileEnd.x; tx++) {
            for (int ty = tileBegin.y; ty <= tileEnd.y; ty++) {
                // Check if hitbox fits in this tile (must be <= 1x1 tile)
                if (hitboxSize.x > MCache.tilesz.x || hitboxSize.y > MCache.tilesz.y) {
                    continue; // Hitbox too large for single tile
                }
                
                // Calculate tile center position
                Coord2d tileCenter = new Coord2d(
                    tx * MCache.tilesz.x + MCache.tilesz.x / 2.0,
                    ty * MCache.tilesz.y + MCache.tilesz.y / 2.0
                );
                
                // Check if tile center is within area
                if (tileCenter.x < area.a.x || tileCenter.x >= area.b.x ||
                    tileCenter.y < area.a.y || tileCenter.y >= area.b.y) {
                    continue;
                }
                
                // Create test box at tile center
                NHitBoxD testBox = new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, tileCenter, rotationAngle);
                
                // Check collisions with obstacles AND already-placed buildings
                boolean passed = true;
                
                for (NHitBoxD obstacle : obstacles) {
                    if (obstacle.intersects(testBox, false)) {
                        passed = false;
                        break;
                    }
                }
                
                if (passed) {
                    for (NHitBoxD placed : placedBuildings) {
                        if (placed.intersects(testBox, false)) {
                            passed = false;
                            break;
                        }
                    }
                }
                
                if (passed) {
                    // This position is valid - create a ghost Gob
                    Coord2d worldPos = new Coord2d(testBox.rc.x, testBox.rc.y);
                    createGhostGob(worldPos);
                    
                    // Add this building to placed list so we don't overlap it
                    placedBuildings.add(new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, tileCenter, rotationAngle));
                }
            }
        }
    }
    
    /**
     * Calculate positions in normal mode (tight packing)
     */
    private void calculateGhostPositionsNormal(ArrayList<NHitBoxD> obstacles, ArrayList<NHitBoxD> placedBuildings) {
        Coord inchMax = area.b.sub(area.a).floor();
        
        // Match Finder.getFreePlace() margin calculation: use rotated circumscribed dimensions.
        NHitBoxD tempBox = new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, Coord2d.of(0), rotationAngle);
        Coord2d rotatedUL = tempBox.getCircumscribedUL();
        Coord2d rotatedBR = tempBox.getCircumscribedBR();
        Coord margin = rotatedBR.sub(rotatedUL).floor(2, 2);

        // Simulate Finder.getFreePlace() behavior: pixel-by-pixel search
        for (int i = margin.x; i <= inchMax.x - margin.x; i++) {
            for (int j = margin.y; j <= inchMax.y - margin.y; j++) {
                Coord2d testPos = area.a.add(i, j);
                NHitBoxD testBox = new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, testPos, rotationAngle);

                // Check collisions with obstacles AND already-placed buildings
                boolean passed = true;

                for (NHitBoxD obstacle : obstacles) {
                    if (obstacle.intersects(testBox, false)) {
                        passed = false;
                        break;
                    }
                }

                if (passed) {
                    for (NHitBoxD placed : placedBuildings) {
                        if (placed.intersects(testBox, false)) {
                            passed = false;
                            break;
                        }
                    }
                }

                if (passed) {
                    // This position is valid - create a ghost Gob
                    Coord2d worldPos = new Coord2d(testBox.rc.x, testBox.rc.y);
                    createGhostGob(worldPos);

                    // Add this building to placed list so we don't overlap it
                    placedBuildings.add(new NHitBoxD(buildingHitBox.begin, buildingHitBox.end, testPos, rotationAngle));
                }
            }
        }
    }

    /**
     * Create a ghost Gob at the specified position with building model
     */
    private void createGhostGob(Coord2d worldPos) {
        try {
            if (buildingResource == null) {
                return;
            }
            
            Gob ghost = new Gob(glob, worldPos);
            ghost.a = rotationAngle;
            
            ghost.setattr(new GhostAlpha(ghost));
            ghost.setattr(new ResDrawable(ghost, buildingResource, spriteData));
            
            synchronized (ghostGobs) {
                ghostGobs.add(ghost);
            }
            
            glob.oc.add(ghost);
        } catch (Exception e) {
            // Silently ignore if can't create
        }
    }

    /**
     * Remove all ghost Gobs
     */
    private void removeGhosts() {
        List<Gob> toRemove;
        synchronized (ghostGobs) {
            toRemove = new ArrayList<>(ghostGobs);
            ghostGobs.clear();
        }
        
        // Synchronous remove with exception handling
        for (Gob ghost : toRemove) {
            try {
                glob.oc.remove(ghost);
            } catch (Exception e) {
                // Ignore concurrent modification - will be handled by dispose if needed
            }
        }
    }

    /**
     * Find obstacles in area (same logic as Finder.getFreePlace)
     */
    private ArrayList<NHitBoxD> findObstacles() {
        ArrayList<NHitBoxD> obstacles = new ArrayList<>();
        NHitBoxD areaBox = new NHitBoxD(area.a, area.b);

        try {
            synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
                for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                    if (!(gob instanceof OCache.Virtual || gob.attr.isEmpty() ||
                          gob.getClass().getName().contains("GlobEffector"))) {
                        // Skip ghost gobs from preview (they have GhostAlpha)
                        if (gob.getattr(GhostAlpha.class) != null) {
                            continue;
                        }
                        
                        NHitBox effectiveHitBox = gob.ngob.hitBox;

                        // If gob has no hitbox, check if there's a custom hitbox defined for it
                        if (effectiveHitBox == null && gob.ngob.name != null) {
                            effectiveHitBox = NHitBox.findCustom(gob.ngob.name);
                        }
                        
                        if (effectiveHitBox != null && gob.getattr(Following.class) == null &&
                            gob.id != NUtils.player().id) {
                            NHitBoxD gobBox = new NHitBoxD(effectiveHitBox.begin, effectiveHitBox.end, gob.rc, gob.a);
                            if (gobBox.intersects(areaBox, true)) {
                                obstacles.add(gobBox);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle exceptions finding obstacles
        }

        return obstacles;
    }

    public void dispose() {
        System.out.println("[BuildGhostPreview] dispose() called, removing " + ghostGobs.size() + " ghosts");
        // Use deferred removal on dispose to avoid ConcurrentModificationException
        List<Gob> toRemove;
        synchronized (ghostGobs) {
            toRemove = new ArrayList<>(ghostGobs);
            ghostGobs.clear();
        }
        
        if (!toRemove.isEmpty() && glob != null && glob.loader != null) {
            glob.loader.defer(() -> {
                for (Gob ghost : toRemove) {
                    try {
                        glob.oc.remove(ghost);
                    } catch (Exception e) {
                        // Silently ignore
                    }
                }
                return null;
            });
        }
    }

    /**
     * Check if the preview needs to be updated
     */
    public boolean needsUpdate(Pair<Coord2d, Coord2d> newArea) {
        if (newArea != null && !newArea.equals(this.area)) {
            return true;
        }
        return false;
    }

    /**
     * Update preview when area changes
     */
    public void update(Pair<Coord2d, Coord2d> newArea) {
        if (newArea != null && !newArea.equals(this.area)) {
            this.area = newArea;
            if (area != null && buildingHitBox != null && buildingResource != null) {
                calculateGhostPositions();
            }
        }
    }
    
    /**
     * Update rotation angle and recalculate positions with new hitbox
     */
    public void updateRotation(int rotationCount, NHitBox newHitBox) {
        this.rotationAngle = (rotationCount * Math.PI / 2.0);
        this.buildingHitBox = newHitBox;
        
        // Recalculate ghost positions with new hitbox and rotation
        if (area != null && buildingHitBox != null && buildingResource != null) {
            calculateGhostPositions();
        }
    }
    
    public ArrayList<Coord2d> getGhostPositions() {
        ArrayList<Coord2d> positions = new ArrayList<>();
        synchronized (ghostGobs) {
            for (Gob ghost : ghostGobs) {
                positions.add(ghost.rc);
            }
        }
        System.out.println("[BuildGhostPreview] getGhostPositions() returning " + positions.size() + " positions");
        return positions;
    }
    
    public void removeGhost(Coord2d pos) {
        synchronized (ghostGobs) {
            System.out.println("[BuildGhostPreview] removeGhost called for position: " + pos + ", total ghosts: " + ghostGobs.size());
            Gob toRemove = null;
            for (Gob ghost : ghostGobs) {
                if (ghost.rc.dist(pos) < 1.0) {
                    toRemove = ghost;
                    break;
                }
            }
            if (toRemove != null) {
                ghostGobs.remove(toRemove);
                System.out.println("[BuildGhostPreview] Removed ghost at " + toRemove.rc + ", remaining: " + ghostGobs.size());
                try {
                    glob.oc.remove(toRemove);
                } catch (Exception e) {
                    System.out.println("[BuildGhostPreview] Error removing ghost from glob.oc: " + e.getMessage());
                }
            } else {
                System.out.println("[BuildGhostPreview] No ghost found at position " + pos);
            }
        }
    }
}
