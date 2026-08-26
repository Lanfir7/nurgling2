package nurgling;

import org.json.JSONObject;
import haven.Coord;

/**
 * Represents a saved tree location on the map
 * Simplified version of FishLocation - no percentage, moon, time, or equipment
 */
public class TreeLocation {
    private final String locationId;  // Unique ID combining segment + coordinates + tree name
    private final long segmentId;
    private final Coord tileCoords;
    private final String treeName;        // e.g., "Oak Tree", "Birch Tree"
    private final String treeResource;    // e.g., "gfx/terobjs/trees/oak"
    private final long timestamp;         // When it was saved
    private final int quantity;           // Number of nearby trees/bushes of the same type
    private final int growthPercent;      // Growth percentage (0-300+)

    public TreeLocation(long segmentId, Coord tileCoords, String treeName, String treeResource, int quantity) {
        this(segmentId, tileCoords, treeName, treeResource, quantity, 0);
    }

    public TreeLocation(long segmentId, Coord tileCoords, String treeName, String treeResource, int quantity, int growthPercent) {
        this(generateLocationId(segmentId, tileCoords, treeName), segmentId, tileCoords, treeName, treeResource,
                System.currentTimeMillis(), quantity, growthPercent);
    }

    TreeLocation(String locationId, long segmentId, Coord tileCoords, String treeName, String treeResource,
                 long timestamp, int quantity, int growthPercent) {
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.treeName = treeName;
        this.treeResource = treeResource;
        this.quantity = quantity;
        this.growthPercent = growthPercent;
        this.timestamp = timestamp;
        this.locationId = locationId;
    }

    public TreeLocation(JSONObject json) {
        this(json.getString("locationId"), json.getLong("segmentId"),
                new Coord(json.getInt("tileX"), json.getInt("tileY")),
                json.getString("treeName"), json.getString("treeResource"),
                json.getLong("timestamp"), json.optInt("quantity", 1), json.optInt("growthPercent", 0));
    }

    public static String generateLocationId(long segmentId, Coord tileCoords, String treeName) {
        return String.format("tree_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y,
                           treeName.replaceAll("[^a-zA-Z0-9]", "_"));
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("locationId", locationId);
        json.put("segmentId", segmentId);
        json.put("tileX", tileCoords.x);
        json.put("tileY", tileCoords.y);
        json.put("treeName", treeName);
        json.put("treeResource", treeResource);
        json.put("timestamp", timestamp);
        json.put("quantity", quantity);
        json.put("growthPercent", growthPercent);
        return json;
    }

    public TreeLocation relocated(long newSegmentId, Coord tileShift) {
        Coord nt = tileCoords.sub(tileShift);
        return new TreeLocation(generateLocationId(newSegmentId, nt, treeName), newSegmentId, nt,
                treeName, treeResource, timestamp, quantity, growthPercent);
    }

    // Getters
    public String getLocationId() { return locationId; }
    public long getSegmentId() { return segmentId; }
    public Coord getTileCoords() { return tileCoords; }
    public String getTreeName() { return treeName; }
    public String getTreeResource() { return treeResource; }
    public long getTimestamp() { return timestamp; }
    public int getQuantity() { return quantity; }
    public int getGrowthPercent() { return growthPercent; }

    public String getMapLabel() {
        return growthPercent > 0 ? growthPercent + "%" : "";
    }

    public String getListLabel() {
        return growthPercent > 0 ? treeName + " (" + growthPercent + "%)" : treeName;
    }
}
