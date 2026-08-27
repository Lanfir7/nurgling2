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
        Gob ghost = new Gob(glob, worldPos);
        ghost.a = rotationAngle;
        ghost.setattr(new GhostAlpha(ghost));
        if (buildingHitBox != null && ghost.ngob != null) {
            ghost.ngob.hitBox = buildingHitBox;
            Coord2d boxSize = buildingHitBox.end.sub(buildingHitBox.begin);
            ghost.addcustomol(new NBoxOverlay(ghost, boxSize, Coord2d.z));
        }
        try {
            if (buildingResource != null) {
                ghost.setattr(new ResDrawable(ghost, buildingResource, spriteData));
            }
        } catch (Loading e) {
            ghost.setattr(new PendingGhostModel(ghost, buildingResource, spriteData));
        }
        synchronized (ghostGobs) {
            ghostGobs.add(ghost);
        }
        try {
            glob.oc.add(ghost);
        } catch (Exception ignored) {
        }
    }

    /**
     * Retries attaching the building sprite once the resource finishes loading.
     */
    private static class PendingGhostModel extends GAttrib {
        private final Indir<Resource> res;
        private final Message sdt;

        PendingGhostModel(Gob gob, Indir<Resource> res, Message sdt) {
            super(gob);
            this.res = res;
            this.sdt = sdt;
        }

        @Override
        public void ctick(double dt) {
            if (res == null) {
                return;
            }
            try {
                gob.setattr(new ResDrawable(gob, res, sdt));
                gob.delattr(PendingGhostModel.class);
            } catch (Loading ignored) {
            }
        }
    }

    public List<Gob> takeGhosts() {
        synchronized (ghostGobs) {
            List<Gob> taken = new ArrayList<>(ghostGobs);
            ghostGobs.clear();
            return taken;
        }
    }

    public void addExistingGhosts(List<Gob> ghosts) {
        if (ghosts == null) {
            return;
        }
        synchronized (ghostGobs) {
            ghostGobs.addAll(ghosts);
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
            if (glob == null || glob.oc == null) {
                return obstacles;
            }
            long ownerId = (gob instanceof Gob) ? gob.id : -1;
            synchronized (glob.oc) {
                for (Gob gob : glob.oc) {
                    if (!(gob instanceof OCache.Virtual || gob.attr.isEmpty() ||
                          gob.getClass().getName().contains("GlobEffector"))) {
                        if (gob.getattr(GhostAlpha.class) != null) {
                            continue;
                        }

                        NHitBox effectiveHitBox = gob.ngob.hitBox;

                        if (effectiveHitBox == null && gob.ngob.name != null) {
                            effectiveHitBox = NHitBox.findCustom(gob.ngob.name);
                        }

                        if (effectiveHitBox != null && gob.getattr(Following.class) == null &&
                            gob.id != ownerId) {
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
        List<Gob> toRemove;
        synchronized (ghostGobs) {
            toRemove = new ArrayList<>(ghostGobs);
            ghostGobs.clear();
        }
        
        if (toRemove.isEmpty()) {
            System.out.println("[BuildGhostPreview] No ghosts to remove");
            return;
        }
        
        // Always do immediate removal first
        for (Gob ghost : toRemove) {
            try {
                if (glob != null && glob.oc != null) {
                    glob.oc.remove(ghost);
                }
            } catch (Exception e) {
                System.out.println("[BuildGhostPreview] Error removing ghost: " + e.getMessage());
            }
        }
        
        // Also schedule deferred removal as backup
        if (glob != null && glob.loader != null) {
            final List<Gob> deferredRemove = new ArrayList<>(toRemove);
            glob.loader.defer(() -> {
                for (Gob ghost : deferredRemove) {
                    try {
                        glob.oc.remove(ghost);
                    } catch (Exception e) {
                        // Already removed or error - ignore
                    }
                }
                return null;
            });
        }
        
        System.out.println("[BuildGhostPreview] dispose() completed");
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
            double minDist = Double.MAX_VALUE;
            // Find the closest ghost within tolerance
            for (Gob ghost : ghostGobs) {
                double dist = ghost.rc.dist(pos);
                if (dist < 5.0 && dist < minDist) {  // Increased tolerance to 5 units
                    minDist = dist;
                    toRemove = ghost;
                }
            }
            if (toRemove != null) {
                ghostGobs.remove(toRemove);
                System.out.println("[BuildGhostPreview] Removed ghost at " + toRemove.rc + " (dist=" + minDist + "), remaining: " + ghostGobs.size());
                try {
                    glob.oc.remove(toRemove);
                } catch (Exception e) {
                    System.out.println("[BuildGhostPreview] Error removing ghost from glob.oc: " + e.getMessage());
                }
            } else {
                System.out.println("[BuildGhostPreview] No ghost found near position " + pos);
            }
        }
    }
}
