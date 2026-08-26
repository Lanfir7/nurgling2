package nurgling;

import org.json.JSONObject;
import haven.Coord;

/**
 * Represents a saved prospecting location on the map
 * Similar to TreeLocation but for prospecting results
 */
public class ProspectingLocation {
    private final String locationId;  // Unique ID combining segment + coordinates + resource type
    private final long segmentId;
    private final Coord tileCoords;
    private final String resourceType;  // e.g., "diabase", "ore", "water", "void"
    private final long timestamp;      // When it was saved

    public ProspectingLocation(long segmentId, Coord tileCoords, String resourceType) {
        this(generateLocationId(segmentId, tileCoords, resourceType), segmentId, tileCoords, resourceType,
                System.currentTimeMillis());
    }

    ProspectingLocation(String locationId, long segmentId, Coord tileCoords, String resourceType, long timestamp) {
        this.locationId = locationId;
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.resourceType = resourceType;
        this.timestamp = timestamp;
    }

    public ProspectingLocation(JSONObject json) {
        this(json.getString("locationId"), json.getLong("segmentId"),
                new Coord(json.getInt("tileX"), json.getInt("tileY")),
                json.getString("resourceType"), json.getLong("timestamp"));
    }

    public static String generateLocationId(long segmentId, Coord tileCoords, String resourceType) {
        return String.format("prospect_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y,
                           resourceType.replaceAll("[^a-zA-Z0-9]", "_"));
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("locationId", locationId);
        json.put("segmentId", segmentId);
        json.put("tileX", tileCoords.x);
        json.put("tileY", tileCoords.y);
        json.put("resourceType", resourceType);
        json.put("timestamp", timestamp);
        return json;
    }

    public ProspectingLocation relocated(long newSegmentId, Coord tileShift) {
        Coord nt = tileCoords.sub(tileShift);
        return new ProspectingLocation(generateLocationId(newSegmentId, nt, resourceType),
                newSegmentId, nt, resourceType, timestamp);
    }

    // Getters
    public String getLocationId() { return locationId; }
    public long getSegmentId() { return segmentId; }
    public Coord getTileCoords() { return tileCoords; }
    public String getResourceType() { return resourceType; }
    public long getTimestamp() { return timestamp; }
}
