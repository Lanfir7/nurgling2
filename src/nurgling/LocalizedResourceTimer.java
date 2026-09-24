package nurgling;

import org.json.JSONObject;
import java.time.Instant;

/**
 * Represents a timer for a localized resource node
 */
public class LocalizedResourceTimer {
    public static final long BOUGH_PYRE_READY_MS = 15 * 60 * 1000L;
    public static final long BOUGH_PYRE_AUTO_REMOVE_MS = 30 * 60 * 1000L;
    public static final String BOUGH_PYRE_TYPE = "nurgling/boughpyre";
    public static final String BOUGH_PYRE_ICON = "nurgling/bots/icons/boughpyre/u";
    /** No server grid yet. Map-file segment ids are local and must not be shared. */
    public static final long NO_GRID = Long.MIN_VALUE;

    private final String resourceId;
    private final long segmentId;
    private final haven.Coord tileCoords;
    private final String resourceName;
    private final String resourceType; // e.g., "gfx/terobjs/map/tarpit"
    private final long startTime;     // Unix timestamp when timer was set
    private final long duration;      // Duration in milliseconds
    private final String description; // User-friendly description like "Tar Pit"
    private final long autoRemoveAfterMs;
    private final String iconRes;
    private final long gridId;
    private final haven.Coord gridOffset;
    /** Segment and tile belong to this client's map file. A shared row starts false until resolved. */
    private final boolean localPlacement;
    
    public LocalizedResourceTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                                  String resourceType, long duration, String description) {
        this(segmentId, tileCoords, resourceName, resourceType, duration, description, 0L, null);
    }

    public LocalizedResourceTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                                  String resourceType, long duration, String description,
                                  long autoRemoveAfterMs, String iconRes) {
        this(segmentId, tileCoords, resourceName, resourceType, duration, description,
                autoRemoveAfterMs, iconRes, NO_GRID, null);
    }

    public LocalizedResourceTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                                  String resourceType, long duration, String description,
                                  long autoRemoveAfterMs, String iconRes,
                                  long gridId, haven.Coord gridOffset) {
        this(idFor(segmentId, tileCoords, resourceType, gridId, gridOffset),
                segmentId, tileCoords, resourceName, resourceType,
                Instant.now().toEpochMilli(), duration, description,
                autoRemoveAfterMs, iconRes, gridId, gridOffset, true);
    }
    
    /**
     * Constructor for loading from database with explicit start time (UTC milliseconds).
     */
    public LocalizedResourceTimer(String resourceId, long segmentId, haven.Coord tileCoords,
                                  String resourceName, String resourceType,
                                  long startTimeUtc, long duration, String description) {
        this(resourceId, segmentId, tileCoords, resourceName, resourceType,
                startTimeUtc, duration, description, 0L, null);
    }

    public LocalizedResourceTimer(String resourceId, long segmentId, haven.Coord tileCoords,
                                  String resourceName, String resourceType,
                                  long startTimeUtc, long duration, String description,
                                  long autoRemoveAfterMs, String iconRes) {
        this(resourceId, segmentId, tileCoords, resourceName, resourceType,
                startTimeUtc, duration, description, autoRemoveAfterMs, iconRes,
                NO_GRID, null, true);
    }

    private LocalizedResourceTimer(String resourceId, long segmentId, haven.Coord tileCoords,
                                   String resourceName, String resourceType,
                                   long startTimeUtc, long duration, String description,
                                   long autoRemoveAfterMs, String iconRes,
                                   long gridId, haven.Coord gridOffset, boolean localPlacement) {
        this.resourceId = resourceId;
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.startTime = startTimeUtc;
        this.duration = duration;
        this.description = description;
        this.autoRemoveAfterMs = autoRemoveAfterMs;
        this.iconRes = emptyToNull(iconRes);
        this.gridId = gridId;
        this.gridOffset = gridOffset;
        this.localPlacement = localPlacement;
    }

    /**
     * Same node on every client: server grid id plus the tile inside that grid.
     * Segment ids are random per map file and must not be part of the shared identity.
     */
    public static LocalizedResourceTimer unplaced(long gridId, haven.Coord gridOffset,
                                                  String resourceName, String resourceType,
                                                  long startTimeUtc, long duration, String description) {
        return new LocalizedResourceTimer(
                sharedResourceId(gridId, gridOffset, resourceType),
                0L, new haven.Coord(0, 0), resourceName, resourceType,
                startTimeUtc, duration, description, 0L, null,
                gridId, gridOffset, false);
    }
    
    public LocalizedResourceTimer(JSONObject json) {
        this.resourceType = json.getString("resourceType");
        this.gridId = json.has("gridId") && !json.isNull("gridId") ? json.getLong("gridId") : NO_GRID;
        this.gridOffset = (json.has("ox") && json.has("oy") && !json.isNull("ox") && !json.isNull("oy"))
                ? new haven.Coord(json.getInt("ox"), json.getInt("oy")) : null;
        String storedId = json.getString("resourceId");
        this.resourceId = (gridId != NO_GRID && gridOffset != null)
                ? sharedResourceId(gridId, gridOffset, resourceType) : storedId;
        this.segmentId = json.getLong("segmentId");
        this.tileCoords = new haven.Coord(json.getInt("tileX"), json.getInt("tileY"));
        this.resourceName = json.getString("resourceName");
        this.startTime = json.getLong("startTime");
        this.duration = json.getLong("duration");
        this.description = json.getString("description");
        this.autoRemoveAfterMs = json.optLong("autoRemoveAfterMs", 0L);
        this.iconRes = emptyToNull(json.has("iconRes") && !json.isNull("iconRes") ? json.getString("iconRes") : null);
        this.localPlacement = true;
    }

    static String legacyResourceId(long segmentId, haven.Coord tileCoords, String resourceType) {
        return String.format("res_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y,
                           sanitize(resourceType));
    }

    public static String sharedResourceId(long gridId, haven.Coord gridOffset, String resourceType) {
        return String.format("res_g_%d_%d_%d_%s", gridId, gridOffset.x, gridOffset.y, sanitize(resourceType));
    }

    private static String idFor(long segmentId, haven.Coord tileCoords, String resourceType,
                                long gridId, haven.Coord gridOffset) {
        if (gridId != NO_GRID && gridOffset != null)
            return sharedResourceId(gridId, gridOffset, resourceType);
        return legacyResourceId(segmentId, tileCoords, resourceType);
    }

    private static String sanitize(String resourceType) {
        return resourceType == null ? "" : resourceType.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
    
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("resourceId", resourceId);
        json.put("segmentId", segmentId);
        json.put("tileX", tileCoords.x);
        json.put("tileY", tileCoords.y);
        json.put("resourceName", resourceName);
        json.put("resourceType", resourceType);
        json.put("startTime", startTime);
        json.put("duration", duration);
        json.put("description", description);
        if (autoRemoveAfterMs > 0)
            json.put("autoRemoveAfterMs", autoRemoveAfterMs);
        if (iconRes != null)
            json.put("iconRes", iconRes);
        if (hasGrid()) {
            json.put("gridId", gridId);
            json.put("ox", gridOffset.x);
            json.put("oy", gridOffset.y);
        }
        return json;
    }

    /**
     * Same clock and resource, new map-file place. Used when {@link haven.MapFile} merges segments:
     * markers already do {@code tc.sub(soff.mul(cmaps))}; timers have to follow or they stay on the
     * abandoned segment.
     */
    public LocalizedResourceTimer relocated(long newSegmentId, haven.Coord tileShift) {
        haven.Coord nt = tileCoords.sub(tileShift);
        String id = hasGrid() ? resourceId : legacyResourceId(newSegmentId, nt, resourceType);
        return new LocalizedResourceTimer(
                id, newSegmentId, nt, resourceName, resourceType,
                startTime, duration, description, autoRemoveAfterMs, iconRes,
                gridId, gridOffset, true);
    }

    public LocalizedResourceTimer withGrid(long gridId, haven.Coord gridOffset) {
        return new LocalizedResourceTimer(
                sharedResourceId(gridId, gridOffset, resourceType),
                segmentId, tileCoords, resourceName, resourceType,
                startTime, duration, description, autoRemoveAfterMs, iconRes,
                gridId, gridOffset, localPlacement);
    }

    public LocalizedResourceTimer withLocalPlace(long segmentId, haven.Coord tileCoords) {
        return new LocalizedResourceTimer(
                resourceId, segmentId, tileCoords, resourceName, resourceType,
                startTime, duration, description, autoRemoveAfterMs, iconRes,
                gridId, gridOffset, true);
    }

    public LocalizedResourceTimer withSchedule(long startTimeUtc, long duration, String description) {
        return new LocalizedResourceTimer(
                resourceId, segmentId, tileCoords, resourceName, resourceType,
                startTimeUtc, duration, description, autoRemoveAfterMs, iconRes,
                gridId, gridOffset, localPlacement);
    }

    /**
     * Check if the timer has expired
     */
    public boolean isExpired() {
        return getRemainingTime() <= 0;
    }

    public boolean isEphemeral() {
        return autoRemoveAfterMs > 0;
    }

    public boolean shouldAutoRemove() {
        if (!isEphemeral())
            return false;
        return Instant.now().toEpochMilli() - startTime >= autoRemoveAfterMs;
    }

    /**
     * Keep until auto-remove. Countdown (0–15 min) and Ready (15–30 min) both survive relog.
     */
    public boolean shouldPersist() {
        if (shouldAutoRemove())
            return false;
        if (isExpired() && !isEphemeral())
            return false;
        return true;
    }
    
    /**
     * Get remaining time in milliseconds
     */
    public long getRemainingTime() {
        long elapsed = Instant.now().toEpochMilli() - startTime;
        return Math.max(0, duration - elapsed);
    }
    
    /**
     * Get remaining time formatted as "Xh Ym" or "Expired"
     */
    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        if (remaining <= 0) {
            return "Ready";
        }
        
        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
    
    // Getters
    public String getResourceId() { return resourceId; }
    public long getSegmentId() { return segmentId; }
    public haven.Coord getTileCoords() { return tileCoords; }
    public String getDescription() { return description; }
    public String getResourceName() { return resourceName; }
    public String getResourceType() { return resourceType; }
    /** Start time in UTC milliseconds (from Instant.now().toEpochMilli()) */
    public long getStartTime() { return startTime; }
    /** Duration in milliseconds */
    public long getDuration() { return duration; }
    public long getAutoRemoveAfterMs() { return autoRemoveAfterMs; }
    public String getIconRes() { return iconRes; }
    public long getGridId() { return gridId; }
    public haven.Coord getGridOffset() { return gridOffset; }
    public boolean hasGrid() { return gridId != NO_GRID && gridOffset != null; }
    public boolean hasLocalPlacement() { return localPlacement; }
}
