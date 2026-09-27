package nurgling.navigation;

import haven.*;
import nurgling.NConfig;
import nurgling.NCore;
import nurgling.NUtils;
import nurgling.tasks.GateDetector;
import nurgling.tools.Finder;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.NAlias;

import java.util.*;
import java.util.function.LongSupplier;

import static nurgling.navigation.ChunkNavConfig.*;

/**
 * Tracks when the player traverses portals (doors, stairs, cellars, mines)
 * and records the connections in the ChunkNav graph.
 * Detection approach:
 * 1. Monitor player's grid ID
 * 2. When grid ID changes, check if player was near a portal
 * 3. If so, find the matching portal on the destination side
 * 4. Record the bidirectional connection
 */
public class PortalTraversalTracker {
    private final ChunkNavGraph graph;
    private final ChunkNavRecorder recorder;
    private final ChunkNavManager manager;
    private final HomePortalLearningService homeLearning;
    private final LongSupplier clock;
    private PendingTransition pendingTransition;

    // State tracking
    private long lastGridId = -1;
    private long lastCheckTime = 0;

    // Duplicate grid transition prevention
    private long lastProcessedFromGridId = -1;
    private long lastProcessedToGridId = -1;
    private long lastProcessedTime = 0;
    private static final long DUPLICATE_PREVENTION_MS = 2000; // Ignore same transition within 2 seconds

    // Snapshot the clicked portal while its source grid is still loaded.
    private ClickedPortal clickedPortal;
    private long lastProcessedPortalGobId = -1;  // Gob ID of last processed portal (prevents re-capture)

    private static final long CHECK_INTERVAL_MS = 100;
    private static final long EXIT_SETTLE_MS = 100;

    static final class ClickedPortal {
        final long gobId;
        final String name;
        final String hash;
        final Coord localCoord;
        final long gridId;
        final HomePortalLearningService.Pending homeLearning;

        ClickedPortal(long gobId, String name, String hash, Coord localCoord, long gridId,
                HomePortalLearningService.Pending homeLearning) {
            this.gobId = gobId;
            this.name = name;
            this.hash = hash;
            this.localCoord = localCoord == null ? null : new Coord(localCoord.x, localCoord.y);
            this.gridId = gridId;
            this.homeLearning = homeLearning;
        }
    }

    static final class PendingTransition {
        final long fromGridId;
        final long toGridId;
        final long readyAt;
        final String expectedExitName;
        final ClickedPortal entrance;
        final Coord2d landingPosition;
        final Coord landingLocalCoord;

        PendingTransition(long fromGridId, long toGridId, long readyAt, String expectedExitName,
                ClickedPortal entrance, Coord2d landingPosition, Coord landingLocalCoord) {
            this.fromGridId = fromGridId;
            this.toGridId = toGridId;
            this.readyAt = readyAt;
            this.expectedExitName = expectedExitName;
            this.entrance = entrance;
            this.landingPosition = landingPosition == null ? null
                    : new Coord2d(landingPosition.x, landingPosition.y);
            this.landingLocalCoord = landingLocalCoord == null ? null
                    : new Coord(landingLocalCoord.x, landingLocalCoord.y);
        }
    }

    // Track combined overlay/home-learning state to detect false -> true transitions
    private boolean wasTrackingEnabled = false;

    // Layer mappings based on portal exit type
    // Maps portal exit name patterns to the layer the destination chunk should be assigned
    // The EXIT portal (what you see after traversing) determines the layer
    // Only "inside" and "cellar" are special - everything else is "outside" (walkable between grids)
    private static final Map<String, String> PORTAL_TO_LAYER = new HashMap<>();
    static {
        // Cellar
        PORTAL_TO_LAYER.put("cellardoor", "inside");     // Exiting cellar -> inside building
        PORTAL_TO_LAYER.put("cellarstairs", "cellar");   // Entering cellar -> cellar

        // Building interiors (entering from outside)
        PORTAL_TO_LAYER.put("stonemansion-door", "inside");
        PORTAL_TO_LAYER.put("logcabin-door", "inside");
        PORTAL_TO_LAYER.put("timberhouse-door", "inside");
        PORTAL_TO_LAYER.put("stonestead-door", "inside");
        PORTAL_TO_LAYER.put("greathall-door", "inside");
        PORTAL_TO_LAYER.put("stonetower-door", "inside");
        PORTAL_TO_LAYER.put("windmill-door", "inside");
        PORTAL_TO_LAYER.put("thatchedhut-door", "inside");
        PORTAL_TO_LAYER.put("primitivetent-door", "inside");

        // Stairs between floors (still inside)
        PORTAL_TO_LAYER.put("downstairs", "inside");
        PORTAL_TO_LAYER.put("upstairs", "inside");

        // Everything else (exiting buildings, mines) -> outside
        // Buildings, minehole, ladder all lead to "outside" (walkable between grids)
    }

    // Portal resource patterns to track
    // Note: "gate" is intentionally excluded - gates are passthrough openings, not teleporting portals
    // Buildings are included because clicking them teleports you inside (the door is implicit)
    private static final String[] PORTAL_PATTERNS = {
        "door",
        "cellar",
        "minehole",
        "ladder",
        "cavein",
        "caveout",
        "stairs",
        // Buildings - clicking these teleports you inside
        "stonemansion",
        "logcabin",
        "timberhouse",
        "stonestead",
        "greathall",
        "stonetower",
        "windmill",
        "thatchedhut",
        "primitivetent"
    };

    public PortalTraversalTracker(ChunkNavGraph graph, ChunkNavRecorder recorder, ChunkNavManager manager) {
        this(graph, recorder, manager, HomePortalLearningService.disabled());
    }

    public PortalTraversalTracker(ChunkNavGraph graph, ChunkNavRecorder recorder, ChunkNavManager manager,
            HomePortalLearningService homeLearning) {
        this(graph, recorder, manager, homeLearning, System::currentTimeMillis);
    }

    PortalTraversalTracker(ChunkNavGraph graph, ChunkNavRecorder recorder, ChunkNavManager manager,
            HomePortalLearningService homeLearning, LongSupplier clock) {
        this.graph = graph;
        this.recorder = recorder;
        this.manager = manager;
        this.homeLearning = homeLearning != null ? homeLearning : HomePortalLearningService.disabled();
        this.clock = clock;
    }

    /**
     * Call this periodically to check for portal traversals.
     * Safe to call frequently - internally throttled.
     */
    public void tick() {
        boolean chunkOverlayEnabled = Boolean.TRUE.equals(NConfig.get(NConfig.Key.chunkNavOverlay));
        boolean trackingEnabled = homeLearning.shouldTrack(chunkOverlayEnabled);

        // Detect tracking state change: OFF -> ON
        // When tracking is turned back on, reset state to prevent detecting stale grid changes
        // that happened while tracking was off. This fixes the bug where exiting a building,
        // turning off recording, walking away, then turning recording back on would cause
        // the exit portal to be recorded at the wrong location.
        if (trackingEnabled && !wasTrackingEnabled) {
            reset();
            // Set lastGridId to current grid so we start fresh from current state
            lastGridId = graph.getPlayerChunkId();
        }
        boolean trackingWasEnabled = wasTrackingEnabled;
        wasTrackingEnabled = trackingEnabled;

        if (!trackingEnabled) {
            if (trackingWasEnabled)
                reset();
            return;
        }

        long now = clock.getAsLong();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;
        doCheck(now);
    }

    private void doCheck(long now) {
        Gob player = NUtils.player();
        if (player == null) return;

        long currentGridId = graph.getPlayerChunkId();
        if (currentGridId == -1) return;

        // Check for grid change
        if (lastGridId != -1 && currentGridId != lastGridId) {
            onGridChanged(lastGridId, currentGridId, player, now);
        }

        // Update tracking state AFTER handling grid change
        lastGridId = currentGridId;

        PendingTransition ready = takeDueTransition(currentGridId);
        if (ready != null)
            completeTransition(ready);

        // Capture lastActions gob BEFORE grid change (like routes system does)
        // This preserves the clicked portal info even after the grid changes
        // Only capture if it's a NEW portal (different from the last one we processed)
        // This prevents re-capturing stale actions after we've already recorded the portal
        NCore.LastActions lastActions = NUtils.getUI().core.getLastActions();
        if (lastActions != null && lastActions.gob != null && lastActions.gob.ngob != null) {
            String gobName = lastActions.gob.ngob.name;
            // Only capture if it's a portal AND it's not the same one we already processed
            if (isPortalGob(gobName) && !isProcessedPortal(lastActions.gob.id)) {
                Coord portalCoord = getPortalLocalCoord(lastActions.gob, player);
                long portalGridId = -1;

                // Always use the PORTAL's actual grid, not the player's grid.
                // Player might be standing in grid A while clicking a portal in grid B.
                //
                // For building exteriors, calculate the DOOR position to determine grid.
                // Buildings can span two grids (e.g., stone mansion center at grid A, door at grid B).
                // We use getDoorGridInfo() which returns both gridId AND localCoord from
                // the same door position, ensuring they always refer to the same grid.
                //
                // For other portals (ladders, mineholes, cellars, regular doors),
                // use the portal gob's actual position to determine its grid.
                if (ChunkPortal.isBuildingExterior(gobName)) {
                    double offset = getBuildingDoorOffset(gobName);
                    if (offset > 0) {
                        DoorGridInfo doorInfo = getDoorGridInfo(lastActions.gob, player, offset);
                        if (doorInfo != null) {
                            // Both gridId and localCoord come from the door position
                            portalGridId = doorInfo.gridId;
                            portalCoord = doorInfo.localCoord;
                        } else {
                            long gobGridId = getGobGridId(lastActions.gob);
                            if (gobGridId != -1)
                                portalGridId = gobGridId;
                        }
                    } else {
                        // Interior door (no offset) - use gob position directly
                        long gobGridId = getGobGridId(lastActions.gob);
                        if (gobGridId != -1)
                            portalGridId = gobGridId;
                    }
                } else {
                    // Non-building portals (ladders, mineholes, cellars, doors):
                    // Use the portal's actual grid, not the player's grid.
                    // This handles the case where player clicks a portal near grid boundary
                    // while standing in an adjacent grid.
                    long gobGridId = getGobGridId(lastActions.gob);
                    if (gobGridId != -1)
                        portalGridId = gobGridId;
                }
                bindLastActionPortal(lastActions.gob, portalCoord, portalGridId, gobName);
            }
        }
    }

    /**
     * Called when player's grid ID changes.
     */
    private void onGridChanged(long fromGridId, long toGridId, Gob player, long now) {
        // A later grid boundary invalidates the earlier landing, even if it is a duplicate.
        pendingTransition = null;
        // Check for duplicate grid transition (same transition firing multiple times)
        if (fromGridId == lastProcessedFromGridId && toGridId == lastProcessedToGridId &&
            (now - lastProcessedTime) < DUPLICATE_PREVENTION_MS) {
            return;
        }

        // Check if player landed on their hearthfire - this indicates a teleport, not a portal traversal
        if (isPlayerOnHearthfire(player)) {
            // Player teleported to hearthfire - don't record this as a portal connection
            lastProcessedFromGridId = fromGridId;
            lastProcessedToGridId = toGridId;
            lastProcessedTime = now;
            clickedPortal = null;
            return;
        }

        // Mark this transition as processed
        lastProcessedFromGridId = fromGridId;
        lastProcessedToGridId = toGridId;
        lastProcessedTime = now;

        // Only known portal pairs need a settle window. Ordinary grid walking returns now.
        beginTransition(fromGridId, toGridId, player.rc, null);
    }

    boolean beginTransition(long fromGridId, long toGridId, Coord2d landingPosition,
            Coord landingLocalCoord) {
        pendingTransition = null;
        ClickedPortal entrance = clickedPortal;
        clickedPortal = null;
        if (entrance == null)
            return false;
        String expectedExitName = GateDetector.getDoorPair(entrance.name);
        if (expectedExitName == null)
            return false;
        pendingTransition = new PendingTransition(fromGridId, toGridId,
                clock.getAsLong() + EXIT_SETTLE_MS, expectedExitName, entrance,
                landingPosition, landingLocalCoord);
        return true;
    }

    PendingTransition takeDueTransition(long currentGridId) {
        PendingTransition transition = pendingTransition;
        if (transition == null)
            return null;
        if (currentGridId != transition.toGridId) {
            pendingTransition = null;
            return null;
        }
        if (clock.getAsLong() < transition.readyAt)
            return null;
        pendingTransition = null;
        return transition;
    }

    private void completeTransition(PendingTransition transition) {
        // The entrance and landing position are frozen before any later click or movement.
        Gob exitPortal;
        try {
            exitPortal = Finder.findGob(new NAlias(transition.expectedExitName));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (exitPortal == null || exitPortal.ngob == null) {
            return;
        }

        String exitName = exitPortal.ngob.name;
        String exitHash = getPortalHash(exitPortal);
        String entranceName = GateDetector.getDoorPair(exitName);

        // Resolve the landing position captured at the grid change, not where the player walks later.
        Coord exitLocalCoord = transition.landingLocalCoord != null
                ? transition.landingLocalCoord : getLocalCoord(transition.landingPosition);

        // Update the destination chunk's layer based on the exit portal
        updateChunkLayer(transition.toGridId, exitName);

        // Update instanceId context for subsequent chunk recordings
        updateInstanceIdAfterTraversal(transition.toGridId, exitName);
        confirmHomeLearning(transition.entrance.homeLearning, transition.toGridId, exitName);

        // Record: entrance portal on its actual grid connects to toGrid
        // We determine entranceGridId first so we can use it for the exit portal's back-connection
        long entranceGridId = transition.fromGridId;  // Default to player's grid, but prefer portal's actual grid

        if (entranceName != null) {
            Coord entranceCoord = null;
            String entranceHash = null;
            // For cave mouths getDoorPair() returns a base name (no ridge orientation
            // suffix), so prefer the concrete clicked gob name when we can confirm it.
            String entranceGobName = entranceName;

            // Use the immutable entrance snapshot captured while its grid was loaded.
            ClickedPortal clicked = transition.entrance;
            if (clicked.localCoord != null) {
                String cachedName = clicked.name;
                // Verify this is the entrance portal we're looking for
                // Use strict matching: must be exact match OR cachedName must be the building (entranceName)
                // NOT the reverse (don't match stonemansion-door when looking for stonemansion)
                if (isPortalGob(cachedName) &&
                        (cachedName.equals(entranceName) ||
                                cachedName.endsWith("/" + getSimpleName(entranceName)) ||
                                GateDetector.isSameDoor(cachedName, entranceName))) {
                    entranceCoord = clicked.localCoord;
                    entranceHash = clicked.hash;
                    entranceGobName = cachedName;
                    // Use the portal's actual grid ID (fixes boundary bug)
                    if (clicked.gridId != -1) {
                        entranceGridId = clicked.gridId;
                    }
                }
            }

            if (entranceCoord != null && entranceHash != null) {
                recordPortalConnection(entranceHash, entranceGobName, entranceGridId,
                        transition.toGridId, entranceCoord);

                // Update entry portal with where we appear in destination
                updatePortalExitCoord(entranceHash, entranceGridId, exitLocalCoord);

                // Update exit portal with where we came from (enables reverse navigation)
                updatePortalExitCoord(exitHash, transition.toGridId, entranceCoord);
            }
        }

        // Record: exit portal on toGrid connects back to entrance portal's grid
        // This uses entranceGridId which was determined from the portal's actual location (fixes boundary bug)
        recordPortalConnection(exitHash, exitName, transition.toGridId, entranceGridId, exitLocalCoord);
        if (transition.entrance.gobId != -1) {
            lastProcessedPortalGobId = transition.entrance.gobId;
            // A stale LastActions click may have been captured again during the settle window.
            if (clickedPortal != null && clickedPortal.gobId == lastProcessedPortalGobId)
                clickedPortal = null;
        }
    }

    /**
     * Record a portal connection in the graph.
     * @param localCoord The local tile coordinate within the chunk (can be null for default center)
     */
    private void recordPortalConnection(String gobHash, String gobName, long fromGridId, long toGridId, Coord localCoord) {
        // Update the portal in the source chunk
        ChunkNavData fromChunk = graph.getChunk(fromGridId);
        if (fromChunk == null) {
            // Chunk not recorded yet - try to record it now
            fromChunk = createMinimalChunk(fromGridId);
            if (fromChunk == null) {
                return;
            }
        }

        // Use provided localCoord or default to center (in tile coordinates 0-99)
        Coord portalCoord = localCoord != null ? localCoord : new Coord(CHUNK_SIZE / 2, CHUNK_SIZE / 2);

        // Find or create the portal - check by hash first, then by position+name
        // Do NOT use findPortalByName - that causes all buildings with same name to merge
        // Use position+name to avoid merging different portal types at same location (e.g., cellardoor vs stonemansion-door)
        ChunkPortal portal = fromChunk.findPortal(gobHash);
        if (portal == null && gobName != null) {
            portal = fromChunk.findPortalByPositionAndName(portalCoord, gobName, 3);
        }

        if (portal == null) {
            ChunkPortal.PortalType type = ChunkPortal.classifyPortal(gobName);
            if (type == null) type = ChunkPortal.PortalType.DOOR;

            portal = new ChunkPortal(gobHash, gobName, type, portalCoord);
            fromChunk.addOrUpdatePortal(portal);
            graph.addChunk(fromChunk); // Re-add to update portal index
        } else {
            // Update existing portal with new hash and position if provided
            portal.gobHash = gobHash;  // Update hash in case it was a synthetic one
            if (localCoord != null) {
                portal.localCoord = portalCoord;
            }
        }

        // Update the connection
        portal.connectsToGridId = toGridId;
        portal.lastTraversed = System.currentTimeMillis();

        // Also notify recorder
        recorder.recordPortalTraversal(gobHash, toGridId);
    }

    /**
     * Update a portal's exit coordinate (where you appear after using it).
     * This is called after we've found the exit portal to record the exact exit position.
     */
    private void updatePortalExitCoord(String gobHash, long chunkId, Coord exitLocalCoord) {
        if (exitLocalCoord == null) return;

        ChunkNavData chunk = graph.getChunk(chunkId);
        if (chunk == null) return;

        ChunkPortal portal = chunk.findPortal(gobHash);
        if (portal != null) {
            portal.exitLocalCoord = exitLocalCoord;
        }
    }

    /**
     * Get the local tile coordinate of a gob within its grid.
     */
    private Coord getGobLocalCoord(Gob gob) {
        return gob == null ? null : getLocalCoord(gob.rc);
    }

    private Coord getLocalCoord(Coord2d worldPosition) {
        if (worldPosition == null)
            return null;
        try {
            MCache mcache = NUtils.getGameUI().map.glob.map;
            Coord tileCoord = worldPosition.floor(MCache.tilesz);
            MCache.Grid grid = mcache.getgridt(tileCoord);
            if (grid != null) {
                return tileCoord.sub(grid.ul);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Get the appropriate local coordinate for a portal gob.
     * For buildings (stonemansion, etc.), offsets from center toward player to get door position.
     * For other portals (doors, cellars), uses the gob's actual position.
     */
    private Coord getPortalLocalCoord(Gob portalGob, Gob player) {
        if (portalGob == null || portalGob.ngob == null) {
            return null;
        }

        String name = portalGob.ngob.name;
        if (name == null) {
            return getGobLocalCoord(portalGob);
        }

        // Buildings need offset toward player to get door position
        // These are whole-building gobs where the door is on the edge, not at center
        double offset = getBuildingDoorOffset(name);
        if (offset > 0 && player != null) {
            return getDoorLocalCoord(portalGob, player, offset);
        }

        // Regular portals (doors, cellars, gates) - use actual gob position
        return getGobLocalCoord(portalGob);
    }

    /**
     * Get the door offset for a building type.
     * Returns 0 for non-building portals (use gob position directly).
     */
    private double getBuildingDoorOffset(String gobName) {
        if (gobName == null) return 0;
        String lower = gobName.toLowerCase();

        // Interior doors (seen from inside) - no offset needed
        if (lower.contains("-door")) return 0;

        // Buildings where the gob is the whole structure and door is on the edge
        // Offset is approximate distance from center to door in tiles
        if (lower.contains("stonemansion")) return 6;
        if (lower.contains("logcabin")) return 3;
        if (lower.contains("timberhouse")) return 3;
        if (lower.contains("stonestead")) return 4;
        if (lower.contains("greathall")) return 5;
        if (lower.contains("stonetower")) return 3;
        if (lower.contains("windmill")) return 3;
        if (lower.contains("thatchedhut")) return 2;

        return 0; // Not a building, use direct position
    }

    /**
     * Result class for door position lookups - contains both grid ID and local coord.
     * This ensures both values refer to the same grid (fixes boundary bug).
     */
    private static class DoorGridInfo {
        final long gridId;
        final Coord localCoord;

        DoorGridInfo(long gridId, Coord localCoord) {
            this.gridId = gridId;
            this.localCoord = localCoord;
        }
    }

    /**
     * Get the grid ID and local coordinate for a building's door position.
     * Calculates the door position by offsetting from building center toward player.
     * Returns null if calculation fails (caller should use fallback).
     *
     * This is the critical fix for the boundary bug: when a building spans two grids,
     * we need to use the DOOR position (not building center) to determine which grid
     * the portal belongs to. Both gridId and localCoord are derived from the same
     * door world position to ensure consistency.
     */
    private DoorGridInfo getDoorGridInfo(Gob buildingGob, Gob player, double offsetTiles) {
        try {
            MCache mcache = NUtils.getGameUI().map.glob.map;

            // Get building center in world coords
            Coord2d buildingPos = buildingGob.rc;
            Coord2d playerPos = player.rc;

            // Calculate direction from building to player
            Coord2d direction = playerPos.sub(buildingPos);
            double dist = direction.dist(Coord2d.z);
            if (dist < 1.0) {
                // Player is at building center - can't determine door direction
                return null;
            }

            // Normalize and scale by offset to get door position
            Coord2d normalized = new Coord2d(direction.x / dist, direction.y / dist);
            Coord2d doorWorldPos = buildingPos.add(normalized.mul(offsetTiles * MCache.tilesz.x));

            // Convert to tile coord and find its grid
            Coord tileCoord = doorWorldPos.floor(MCache.tilesz);
            MCache.Grid grid = mcache.getgridt(tileCoord);
            if (grid != null) {
                Coord localCoord = tileCoord.sub(grid.ul);
                return new DoorGridInfo(grid.id, localCoord);
            }
        } catch (Exception e) {
            // Fallback
        }
        return null;
    }

    /**
     * Get the "door position" for a building gob by offsetting from building center toward player.
     * This gives a stable position for the door even if player stands at slightly different spots.
     * @param buildingGob The building gob (e.g., stonemansion)
     * @param player The player gob
     * @param offsetTiles How many tiles to offset toward player (e.g., 5 for stonemansion)
     */
    private Coord getDoorLocalCoord(Gob buildingGob, Gob player, double offsetTiles) {
        DoorGridInfo info = getDoorGridInfo(buildingGob, player, offsetTiles);
        if (info != null) {
            return info.localCoord;
        }
        // Fallback to building center
        return getGobLocalCoord(buildingGob);
    }

    /**
     * Create a minimal chunk entry for a grid that wasn't recorded yet.
     */
    private ChunkNavData createMinimalChunk(long gridId) {
        try {
            MCache mcache = NUtils.getGameUI().map.glob.map;
            synchronized (mcache.grids) {
                for (MCache.Grid grid : mcache.grids.values()) {
                    if (grid.id == gridId) {
                        Coord gridCoord = grid.ul.div(CHUNK_SIZE);
                        ChunkNavData chunk = new ChunkNavData(gridId, gridCoord, grid.ul);
                        graph.addChunk(chunk);
                        return chunk;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Get the simple name from a full resource path.
     * e.g., "gfx/terobjs/arch/stonemansion" -> "stonemansion"
     */
    private String getSimpleName(String fullName) {
        if (fullName == null) return "";
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }

    /**
     * Check if a gob name is a portal type.
     */
    private boolean isPortalGob(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();

        for (String pattern : PORTAL_PATTERNS) {
            if (lower.contains(pattern)) {
                // Exclude water gates and similar non-traversable things
                if (lower.contains("water") || lower.contains("floodgate")) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Determine the layer for a destination grid based on the exit portal type.
     * Only "inside" and "cellar" are special - everything else is "outside".
     * @param exitPortalName The name of the exit portal (the portal you see after traversing)
     * @return The layer name for the destination grid
     */
    private String determineLayerFromExitPortal(String exitPortalName) {
        if (exitPortalName == null) return null;

        String lowerName = exitPortalName.toLowerCase();

        // Check PORTAL_TO_LAYER for inside/cellar mappings
        for (Map.Entry<String, String> entry : PORTAL_TO_LAYER.entrySet()) {
            if (lowerName.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        // Default: if it ends with "-door" it's likely inside a building
        if (lowerName.contains("-door")) {
            return "inside";
        }

        // Everything else (surface, mines, etc.) is "outside"
        return "outside";
    }

    /**
     * Update the layer of a chunk based on the exit portal.
     * Called when we traverse to a new grid and find an exit portal.
     */
    private void updateChunkLayer(long gridId, String exitPortalName) {
        String layer = determineLayerFromExitPortal(exitPortalName);
        if (layer == null) {
            return;
        }

        ChunkNavData chunk = graph.getChunk(gridId);
        if (chunk != null && !layer.equals(chunk.layer)) {
            chunk.layer = layer;
        }
    }

    /**
     * Get a consistent hash for a portal gob.
     */
    private String getPortalHash(Gob gob) {
        if (gob.ngob != null && gob.ngob.hash != null && !gob.ngob.hash.isEmpty()) {
            return gob.ngob.hash;
        }
        // Fallback - use gob id and position
        return "portal_" + gob.id + "_" + (int)gob.rc.x + "_" + (int)gob.rc.y;
    }

    /**
     * Check if the player is standing on a hearthfire.
     * This indicates a teleport (Hearth Fire skill) rather than walking through a portal.
     */
    private boolean isPlayerOnHearthfire(Gob player) {
        if (player == null) return false;

        try {
            // Search for fire gobs near the player
            // Fire gob resource is "gfx/terobjs/pow", but only model attribute 17 is a hearthfire
            ArrayList<Gob> fires = Finder.findGobs(new NAlias("gfx/terobjs/pow"));

            for (Gob fire : fires) {
                // Check if this is actually a hearthfire (model attribute 17)
                if (fire.ngob == null || fire.ngob.getModelAttribute() != 17) {
                    continue;
                }

                double dist = player.rc.dist(fire.rc);
                // If player is very close to a hearthfire (within ~2 tiles), they likely teleported
                if (dist < 11.0) {  // ~2 tiles in world units
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return false;
    }

    /**
     * Get the grid ID that a gob belongs to (based on its center position).
     */
    private long getGobGridId(Gob gob) {
        try {
            MCache mcache = NUtils.getGameUI().map.glob.map;
            Coord tileCoord = gob.rc.floor(MCache.tilesz);
            MCache.Grid grid = mcache.getgridt(tileCoord);
            if (grid != null) return grid.id;
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

        /**
     * Update the current instanceId after a portal traversal.
     * Rules:
     * - If destination chunk already has a known indoor instanceId, inherit it
     * - If an inside/cellar destination still carries 0 or SURFACE_INSTANCE, assign toGridId
     * - If going to surface (exiting mine/building), use SURFACE_INSTANCE
     * - If entering a new instance (mine, building interior, cellar), use toGridId as instanceId
     */
    private void updateInstanceIdAfterTraversal(long toGridId, String exitPortalName) {
        if (manager == null) return;

        ChunkNavData destChunk = graph.getChunk(toGridId);
        long newInstanceId = destinationInstanceAfterTraversal(destChunk, toGridId, exitPortalName);
        manager.setCurrentInstanceId(newInstanceId);
        long previousInstanceId = destChunk != null ? destChunk.instanceId : 0L;
        stampDestinationInstance(destChunk, newInstanceId);
        if (destChunk != null && previousInstanceId != newInstanceId) {
            removeCrossInstanceConnections(graph, destChunk);
        }
    }

    static boolean indoorHomeLayer(String layer) {
        return "inside".equals(layer) || "cellar".equals(layer);
    }

    static long destinationInstanceAfterTraversal(ChunkNavData destChunk, long toGridId,
            String exitPortalName) {
        if (isSurfaceExitPortal(exitPortalName))
            return ChunkNavManager.SURFACE_INSTANCE;

        String layer = destChunk != null && destChunk.layer != null && !destChunk.layer.isEmpty()
                ? destChunk.layer : null;
        if (indoorHomeLayer(layer)) {
            if (destChunk != null && ChunkNavManager.isInteriorInstanceId(destChunk.instanceId))
                return destChunk.instanceId;
            if (toGridId != -1L)
                return toGridId;
            return destChunk != null ? destChunk.instanceId : 0L;
        }
        if (destChunk != null && destChunk.instanceId == ChunkNavManager.SURFACE_INSTANCE
                && exitPortalName != null && toGridId != -1L) {
            return determineInstanceIdFromExitPortal(toGridId, exitPortalName);
        }
        if (destChunk != null && destChunk.instanceId != 0)
            return destChunk.instanceId;
        return determineInstanceIdFromExitPortal(toGridId, exitPortalName);
    }

    static void stampDestinationInstance(ChunkNavData destChunk, long instanceId) {
        if (destChunk == null)
            return;
        if (instanceId == -1L)
            return;
        if ((instanceId == ChunkNavManager.SURFACE_INSTANCE && !indoorHomeLayer(destChunk.layer))
                || (destChunk.instanceId == ChunkNavManager.SURFACE_INSTANCE
                        && ChunkNavManager.isInteriorInstanceId(instanceId))
                || destChunk.instanceId == 0
                || (indoorHomeLayer(destChunk.layer)
                        && !ChunkNavManager.isInteriorInstanceId(destChunk.instanceId))) {
            destChunk.instanceId = instanceId;
        }
    }

    static void removeCrossInstanceConnections(ChunkNavGraph graph, ChunkNavData chunk) {
        if (graph == null || chunk == null || chunk.instanceId == 0) return;
        boolean chunkChanged = false;
        for (ChunkNavData other : graph.getAllChunks()) {
            if (other == null || other.gridId == chunk.gridId || other.instanceId == 0
                    || other.instanceId == chunk.instanceId) {
                continue;
            }
            boolean otherChanged = other.connectedChunks.remove(chunk.gridId);
            otherChanged |= clearNeighborReference(other, chunk.gridId);
            chunkChanged |= chunk.connectedChunks.remove(other.gridId);
            chunkChanged |= clearNeighborReference(chunk, other.gridId);
            if (otherChanged) other.markUpdated();
        }
        if (chunkChanged) chunk.markUpdated();
    }

    private static boolean clearNeighborReference(ChunkNavData chunk, long gridId) {
        boolean changed = false;
        if (chunk.neighborNorth == gridId) { chunk.neighborNorth = -1; changed = true; }
        if (chunk.neighborSouth == gridId) { chunk.neighborSouth = -1; changed = true; }
        if (chunk.neighborEast == gridId) { chunk.neighborEast = -1; changed = true; }
        if (chunk.neighborWest == gridId) { chunk.neighborWest = -1; changed = true; }
        return changed;
    }

    static boolean isSurfaceExitPortal(String exitPortalName) {
        if (exitPortalName == null)
            return false;
        String lower = exitPortalName.toLowerCase();
        return ChunkPortal.isBuildingExterior(exitPortalName)
                || lower.contains("minehole")
                || lower.contains("cavein");
    }

    private static long determineInstanceIdFromExitPortal(long toGridId, String exitPortalName) {
        if (exitPortalName == null) return ChunkNavManager.SURFACE_INSTANCE;
        String lower = exitPortalName.toLowerCase();

        if (isSurfaceExitPortal(exitPortalName)) return ChunkNavManager.SURFACE_INSTANCE;
        // cellardoor -> we left cellar back into building interior
        if (lower.contains("cellardoor")) return toGridId;
        // All other exits (ladder, -door, cellarstairs, etc.) -> new instance
        return toGridId;
    }

    /**
     * Reset tracking state.
     */
    public void reset() {
        lastGridId = -1;
        lastProcessedFromGridId = -1;
        lastProcessedToGridId = -1;
        lastProcessedTime = 0;
        clickedPortal = null;
        pendingTransition = null;
        lastProcessedPortalGobId = -1;
    }

    boolean isProcessedPortal(long gobId) {
        return gobId == lastProcessedPortalGobId;
    }

    HomePortalLearningService.Pending bindLastActionPortal(Gob gob, Coord local, long gridId,
            String resource) {
        HomePortalLearningService.Pending learning = local == null || gridId == -1
                ? null : captureHomeLearning(gridId, local, resource);
        rememberClickedPortal(gob == null ? -1 : gob.id, resource,
                gob == null ? null : getPortalHash(gob), local, gridId, learning);
        return learning;
    }

    void rememberClickedPortal(long gobId, String resource, String hash, Coord local,
            long gridId, HomePortalLearningService.Pending learning) {
        clickedPortal = new ClickedPortal(gobId, resource, hash, local, gridId, learning);
    }

    private HomePortalLearningService.Pending captureHomeLearning(long gridId, Coord local,
            String portalResource) {
        long instanceId = manager == null ? ChunkNavManager.SURFACE_INSTANCE : manager.getCurrentInstanceId();
        return homeLearning.capture(gridId, local, portalResource, instanceId, layerOf(gridId));
    }

    private void confirmHomeLearning(HomePortalLearningService.Pending learning,
            long toGridId, String exitName) {
        if (learning == null || learning.portalCoord == null
                || learning.portalResource == null)
            return;
        String fromLayer = learning.sourceLayer;
        String toLayer = determineLayerFromExitPortal(exitName);
        if (toLayer == null)
            toLayer = "outside";
        ChunkNavData fromChunk = graph == null ? null : graph.getChunk(learning.sourceGridId);
        ChunkNavData toChunk = graph == null ? null : graph.getChunk(toGridId);
        if (fromChunk != null && fromChunk.layer != null && !fromChunk.layer.isEmpty())
            fromLayer = fromChunk.layer;
        if (toChunk != null && toChunk.layer != null && !toChunk.layer.isEmpty())
            toLayer = toChunk.layer;
        long fromInstance = learning.sourceInstanceId;
        long toInstance = manager == null ? 0L : manager.getCurrentInstanceId();
        if (toChunk != null && ChunkNavManager.isInteriorInstanceId(toChunk.instanceId))
            toInstance = toChunk.instanceId;
        else if (indoorHomeLayer(toLayer)
                && !ChunkNavManager.isInteriorInstanceId(toInstance)
                && toGridId != -1L)
            toInstance = toGridId;
        HomeInteriorRegistry.PortalIdentity root = new HomeInteriorRegistry.PortalIdentity(
                learning.sourceGridId,
                learning.portalCoord.x,
                learning.portalCoord.y,
                learning.portalResource);
        HomePortalInheritance.Traversal traversal = new HomePortalInheritance.Traversal(
                learning.sourceGridId, toGridId, fromInstance, toInstance,
                fromLayer, toLayer, ChunkPortal.classifyPortal(learning.portalResource),
                root, exitName, true, false, System.currentTimeMillis());
        homeLearning.confirm(learning, traversal);
    }

    private String layerOf(long gridId) {
        if (graph == null)
            return "outside";
        ChunkNavData chunk = graph.getChunk(gridId);
        if (chunk == null || chunk.layer == null || chunk.layer.isEmpty())
            return "outside";
        return chunk.layer;
    }
}
