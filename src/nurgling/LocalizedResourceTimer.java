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

    private final String resourceId;  // Unique ID combining segment + coordinates + resource type
    private final long segmentId;
    private final haven.Coord tileCoords;
    private final String resourceName;
    private final String resourceType; // e.g., "gfx/terobjs/map/tarpit"
    private final long startTime;     // Unix timestamp when timer was set
    private final long duration;      // Duration in milliseconds
    private final String description; // User-friendly description like "Tar Pit"
    private final long autoRemoveAfterMs;
    private final String iconRes;
    
    public LocalizedResourceTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                                  String resourceType, long duration, String description) {
        this(segmentId, tileCoords, resourceName, resourceType, duration, description, 0L, null);
    }

    public LocalizedResourceTimer(long segmentId, haven.Coord tileCoords, String resourceName,
                                  String resourceType, long duration, String description,
                                  long autoRemoveAfterMs, String iconRes) {
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.duration = duration;
        this.description = description;
        this.startTime = Instant.now().toEpochMilli();
        this.resourceId = generateResourceId(segmentId, tileCoords, resourceType);
        this.autoRemoveAfterMs = autoRemoveAfterMs;
        this.iconRes = emptyToNull(iconRes);
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
    }
    
    public LocalizedResourceTimer(JSONObject json) {
        this.resourceId = json.getString("resourceId");
        this.segmentId = json.getLong("segmentId");
        this.tileCoords = new haven.Coord(json.getInt("tileX"), json.getInt("tileY"));
        this.resourceName = json.getString("resourceName");
        this.resourceType = json.getString("resourceType");
        this.startTime = json.getLong("startTime");
        this.duration = json.getLong("duration");
        this.description = json.getString("description");
        this.autoRemoveAfterMs = json.optLong("autoRemoveAfterMs", 0L);
        this.iconRes = emptyToNull(json.has("iconRes") && !json.isNull("iconRes") ? json.getString("iconRes") : null);
    }
    
    private static String generateResourceId(long segmentId, haven.Coord tileCoords, String resourceType) {
        return String.format("res_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y, 
                           resourceType.replaceAll("[^a-zA-Z0-9]", "_"));
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
        return json;
    }

    /**
     * Same clock and resource, new map-file place. Used when {@link haven.MapFile} merges segments:
     * markers already do {@code tc.sub(soff.mul(cmaps))}; timers have to follow or they stay on the
     * abandoned segment.
     */
    public LocalizedResourceTimer relocated(long newSegmentId, haven.Coord tileShift) {
        haven.Coord nt = tileCoords.sub(tileShift);
        return new LocalizedResourceTimer(
                generateResourceId(newSegmentId, nt, resourceType),
                newSegmentId, nt, resourceName, resourceType,
                startTime, duration, description, autoRemoveAfterMs, iconRes);
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
}
