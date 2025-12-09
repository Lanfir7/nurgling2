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
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.resourceType = resourceType;
        this.timestamp = System.currentTimeMillis();
        this.locationId = generateLocationId(segmentId, tileCoords, resourceType);
    }

    public ProspectingLocation(JSONObject json) {
        this.locationId = json.getString("locationId");
        this.segmentId = json.getLong("segmentId");
        this.tileCoords = new Coord(json.getInt("tileX"), json.getInt("tileY"));
        this.resourceType = json.getString("resourceType");
        this.timestamp = json.getLong("timestamp");
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

    // Getters
    public String getLocationId() { return locationId; }
    public long getSegmentId() { return segmentId; }
    public Coord getTileCoords() { return tileCoords; }
    public String getResourceType() { return resourceType; }
    public long getTimestamp() { return timestamp; }
}
