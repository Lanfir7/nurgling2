package nurgling.routes;

import haven.Coord;
import haven.Coord2d;
import haven.Following;
import haven.Gob;
import haven.MCache;
import nurgling.NCore;
import nurgling.NUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Упрощенная версия Route без привязки к зонам.
 * Используется только для записи и воспроизведения маршрута.
 */
public class SimpleRoute {
    public int id;
    public String name;
    public String path = "";
    public final ArrayList<SimpleRoutePoint> waypoints = new ArrayList<>();
    public NCore.LastActions lastAction = null;
    public SimpleRoutePoint cachedRoutePoint = null;
    public boolean hasPassedGate = false;
    public Gob lastPassedGate = null;

    public SimpleRoute(String name) {
        this.name = name;
    }

    public void addHearthFireWaypoint(String name) {
        Gob player = NUtils.player();
        Coord2d rc = getRouteAnchorPosition();
        MCache cache = NUtils.getGameUI().ui.sess.glob.map;

        if(player == null || rc == null || cache == null) {
            return;
        }

        // Create a temporary waypoint to get its hash
        SimpleRoutePoint tempWaypoint = new SimpleRoutePoint(rc, cache, name);

        // Check if this waypoint already exists in the route
        SimpleRoutePoint existingWaypoint = findWaypointById(tempWaypoint.id);

        // Use existing waypoint if found, otherwise use the temporary one
        SimpleRoutePoint waypointToAdd = existingWaypoint != null ? existingWaypoint : tempWaypoint;

        // Add the waypoint with default connection values
        try {
            addPredefinedWaypoint(waypointToAdd, "", "", false);
        } catch (Exception e) {
            NUtils.getGameUI().msg("Failed to add waypoint: " + e.getMessage());
        }
    }

    public void addWaypoint() {
        Gob player = NUtils.player();
        Coord2d rc = getRouteAnchorPosition();
        MCache cache = NUtils.getGameUI().ui.sess.glob.map;

        if(player == null || rc == null || cache == null) {
            return;
        }

        // Create a temporary waypoint to get its hash
        SimpleRoutePoint tempWaypoint = new SimpleRoutePoint(rc, cache);
        
        // Check if this waypoint already exists in the route
        SimpleRoutePoint existingWaypoint = findWaypointById(tempWaypoint.id);
        
        // Use existing waypoint if found, otherwise use the temporary one
        SimpleRoutePoint waypointToAdd = existingWaypoint != null ? existingWaypoint : tempWaypoint;

        // Add the waypoint with default connection values
        addPredefinedWaypoint(waypointToAdd, "", "", false);
    }

    public void addPredefinedWaypoint(SimpleRoutePoint routePoint, String doorHash, String doorName, boolean isDoor) {
        try {
            if(!waypoints.isEmpty()) {
                SimpleRoutePoint existingWaypoint = findWaypointById(routePoint.id);

                routePoint = existingWaypoint != null ? existingWaypoint : routePoint;

                SimpleRoutePoint lastRoutePoint = waypoints.get(waypoints.size() - 1);

                // Add neighbors if they do not already exist.
                if(!routePoint.getNeighbors().contains(lastRoutePoint.id)) {
                    routePoint.addNeighbor(lastRoutePoint.id);
                }

                if(!lastRoutePoint.getNeighbors().contains(routePoint.id)) {
                    lastRoutePoint.addNeighbor(routePoint.id);
                }

                // Add connections between the points if connections do not already exists
                if(!routePoint.connections.containsKey(lastRoutePoint.id)) {
                    routePoint.addConnection(lastRoutePoint.id, String.valueOf(lastRoutePoint.id), doorHash, doorName, isDoor);
                }

                if(!lastRoutePoint.connections.containsKey(routePoint.id)) {
                    lastRoutePoint.addConnection(routePoint.id, String.valueOf(routePoint.id), doorHash, doorName, isDoor);
                }
            }

            synchronized (waypoints) {
                this.waypoints.add(routePoint);
            }
            NUtils.getGameUI().msg("Waypoint added: " + routePoint);
            NUtils.getGameUI().msg("Neighbors: " + routePoint.getNeighbors());
        } catch (Exception e) {
            NUtils.getGameUI().msg("Failed to add waypoint: " + e.getMessage());
        }
    }

    public void addPredefinedWaypointNoConnections(SimpleRoutePoint routePoint) {
        try {
            if(!waypoints.isEmpty()) {
                SimpleRoutePoint existingWaypoint = findWaypointById(routePoint.id);

                routePoint = existingWaypoint != null ? existingWaypoint : routePoint;
            }

            synchronized (waypoints) {
                this.waypoints.add(routePoint);
            }
            NUtils.getGameUI().msg("Waypoint added: " + routePoint);
            NUtils.getGameUI().msg("Neighbors: " + routePoint.getNeighbors());
        } catch (Exception e) {
            NUtils.getGameUI().msg("Failed to add waypoint: " + e.getMessage());
        }
    }

    public void addRandomWaypoint() {
        Coord2d rc = getRouteAnchorPosition();
        if (rc == null) {
            return;
        }

        // Create a temporary waypoint to get its hash
        SimpleRoutePoint tempWaypoint = new SimpleRoutePoint(rc, NUtils.getGameUI().ui.sess.glob.map);

        // Check if this waypoint already exists in the route
        SimpleRoutePoint existingWaypoint = findWaypointById(tempWaypoint.id);

        // Use existing waypoint if found, otherwise use the temporary one
        SimpleRoutePoint waypointToAdd = existingWaypoint != null ? existingWaypoint : tempWaypoint;

        try {
            synchronized (waypoints) {
                this.waypoints.add(waypointToAdd);
            }
            NUtils.getGameUI().msg("Waypoint added: " + waypointToAdd);
            NUtils.getGameUI().msg("Neighbors: " + waypointToAdd.getNeighbors());
        } catch (Exception e) {
            NUtils.getGameUI().msg("Failed to add waypoint: " + e.getMessage());
        }
    }

    /**
     * Для повозки пишем waypoint по лошади (не по игроку в телеге).
     */
    private Coord2d getRouteAnchorPosition() {
        Gob player = NUtils.player();
        if (player == null) {
            return null;
        }
        Following following = player.getattr(Following.class);
        if (following != null) {
            Gob vehicle = following.tgt();
            if (vehicle != null) {
                if (vehicle.ngob != null && vehicle.ngob.name != null && vehicle.ngob.name.contains("/vehicle/wagon")) {
                    Gob horse = findHorseForWagon(vehicle);
                    if (horse != null) {
                        return horse.rc;
                    }
                }
                return vehicle.rc;
            }
        }
        return player.rc;
    }

    private Gob findHorseForWagon(Gob wagon) {
        if (wagon == null || NUtils.getGameUI() == null || NUtils.getGameUI().ui == null ||
                NUtils.getGameUI().ui.sess == null || NUtils.getGameUI().ui.sess.glob == null) {
            return null;
        }
        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                if (gob == null || gob.ngob == null || gob.ngob.name == null) continue;
                Following fl = gob.getattr(Following.class);
                if (fl != null && fl.tgt == wagon.id && gob.ngob.name.contains("/kritter/horse/")) {
                    return gob;
                }
            }
        }
        return null;
    }

    public void deleteWaypoint(SimpleRoutePoint waypoint) {
        List<SimpleRoutePoint> toRemove = new ArrayList<>();

        for (SimpleRoutePoint point : waypoints) {
            List<Integer> neighbors = point.getNeighbors();
            if (neighbors != null && neighbors.contains(waypoint.id)) {
                neighbors.remove(Integer.valueOf(waypoint.id));
                point.removeConnection(waypoint.id);
            }

            if (point.id == waypoint.id) {
                toRemove.add(point);
            }
        }

        synchronized (waypoints) {
            waypoints.removeAll(toRemove);
        }
    }

    public SimpleRoutePoint getLastWaypoint() {
        if (waypoints.isEmpty()) return null;
        return waypoints.get(waypoints.size() - 1);
    }

    public SimpleRoutePoint getSecondToLastWaypoint() {
        if (waypoints.size() < 2) return null;
        return waypoints.get(waypoints.size() - 2);
    }

    private SimpleRoutePoint findWaypointById(int id) {
        for (SimpleRoutePoint point : waypoints) {
            if (point.id == id) {
                return point;
            }
        }
        return null;
    }

    public SimpleRoute(JSONObject obj) {
        this.name = obj.getString("name");
        this.id = obj.getInt("id");

        if (obj.has("path")) {
            this.path = obj.getString("path");
        } else if (obj.has("dir")) {
            this.path = "/" + obj.getString("path");
        }

        this.waypoints.clear();
        if (obj.has("waypoints")) {
            JSONArray arr = obj.getJSONArray("waypoints");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject point = arr.getJSONObject(i);
                long gridId = point.getLong("gridId");
                JSONObject localCoord = point.getJSONObject("localCoord");
                int x = localCoord.getInt("x");
                int y = localCoord.getInt("y");
                String hearthFirePlayerName = "";
                if(point.has("hearthFirePlayerName")) {
                    hearthFirePlayerName = point.getString("hearthFirePlayerName");
                }

                SimpleRoutePoint waypoint = new SimpleRoutePoint(gridId, new Coord(x, y), hearthFirePlayerName);
                
                // Load original position if it exists
                if (point.has("originalGridId") && point.has("originalLocalCoord")) {
                    waypoint.originalGridId = point.getLong("originalGridId");
                    JSONObject originalLocalCoord = point.getJSONObject("originalLocalCoord");
                    waypoint.originalLocalCoord = new Coord(originalLocalCoord.getInt("x"), originalLocalCoord.getInt("y"));
                }
                
                // Load neighbors if they exist
                if (point.has("neighbors")) {
                    JSONArray neighbors = point.getJSONArray("neighbors");
                    for (int j = 0; j < neighbors.length(); j++) {
                        waypoint.addNeighbor(neighbors.getInt(j));
                    }
                }
                
                // Load connections if they exist
                if (point.has("connections")) {
                    JSONObject connections = point.getJSONObject("connections");
                    for (String neighborHash : connections.keySet()) {
                        JSONObject conn = connections.getJSONObject(neighborHash);
                        String connectionTo = conn.has("connectionTo") ? conn.getString("connectionTo") : "";
                        String connGobHash = conn.has("gobHash") ? conn.getString("gobHash") : "";
                        String connGobName = conn.has("gobName") ? conn.getString("gobName") : "";
                        boolean isDoor = conn.has("isDoor") ? conn.getBoolean("isDoor") : false;
                        waypoint.addConnection(Integer.parseInt(neighborHash), connectionTo, connGobHash, connGobName, isDoor);
                    }
                }

                synchronized (waypoints) {
                    waypoints.add(waypoint);
                }
            }
        }
    }

    public SimpleRoute(JSONObject obj, Map<Integer, SimpleRoutePoint> routePointMap) {
        this.name = obj.getString("name");
        this.id = obj.getInt("id");

        if (obj.has("path")) {
            this.path = obj.getString("path");
        } else if (obj.has("dir")) {
            this.path = "/" + obj.getString("path");
        }

        this.waypoints.clear();
        if (obj.has("waypoints")) {
            JSONArray arr = obj.getJSONArray("waypoints");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject point = arr.getJSONObject(i);
                int id = point.getInt("id");
                long gridId = point.getLong("gridId");
                String hearthFirePlayerName = "";
                if(point.has("hearthFirePlayerName")) {
                    hearthFirePlayerName = point.getString("hearthFirePlayerName");
                }

                SimpleRoutePoint waypoint;
                if (routePointMap.containsKey(id)) {
                    waypoint = routePointMap.get(id);
                } else {
                    JSONObject localCoord = point.getJSONObject("localCoord");
                    int x = localCoord.getInt("x");
                    int y = localCoord.getInt("y");
                    waypoint = new SimpleRoutePoint(gridId, new Coord(x, y), hearthFirePlayerName);

                    // Load original position if it exists
                    if (point.has("originalGridId") && point.has("originalLocalCoord")) {
                        waypoint.originalGridId = point.getLong("originalGridId");
                        JSONObject originalLocalCoordObj = point.getJSONObject("originalLocalCoord");
                        waypoint.originalLocalCoord = new Coord(originalLocalCoordObj.getInt("x"), originalLocalCoordObj.getInt("y"));
                    }

                    // Load neighbors if they exist
                    if (point.has("neighbors")) {
                        JSONArray neighbors = point.getJSONArray("neighbors");
                        for (int j = 0; j < neighbors.length(); j++) {
                            waypoint.addNeighbor(neighbors.getInt(j));
                        }
                    }

                    // Load connections if they exist
                    if (point.has("connections")) {
                        JSONObject connections = point.getJSONObject("connections");
                        for (String neighborHash : connections.keySet()) {
                            JSONObject conn = connections.getJSONObject(neighborHash);
                            String connectionTo = conn.has("connectionTo") ? conn.getString("connectionTo") : "";
                            String connGobHash = conn.has("gobHash") ? conn.getString("gobHash") : "";
                            String connGobName = conn.has("gobName") ? conn.getString("gobName") : "";
                            boolean isDoor = conn.has("isDoor") ? conn.getBoolean("isDoor") : false;
                            waypoint.addConnection(Integer.parseInt(neighborHash), connectionTo, connGobHash, connGobName, isDoor);
                        }
                    }
                    routePointMap.put(id, waypoint);
                }
                synchronized (waypoints) {
                    waypoints.add(waypoint);
                }
            }
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("path", path);
        
        // Save waypoints
        JSONArray waypointsArray = new JSONArray();
        for (SimpleRoutePoint waypoint : waypoints) {
            JSONObject waypointJson = new JSONObject();
            waypointJson.put("id", waypoint.id);
            waypointJson.put("gridId", waypoint.gridId);
            waypointJson.put("hearthFirePlayerName", waypoint.hearthFirePlayerName);
            waypointJson.put("localCoord", new JSONObject()
                .put("x", waypoint.localCoord.x)
                .put("y", waypoint.localCoord.y));
            
            // Save original position for drag limiting
            waypointJson.put("originalGridId", waypoint.originalGridId);
            waypointJson.put("originalLocalCoord", new JSONObject()
                .put("x", waypoint.originalLocalCoord.x)
                .put("y", waypoint.originalLocalCoord.y));
            
            // Save neighbors
            JSONArray neighborsArray = new JSONArray();
            for (int neighborId : waypoint.getNeighbors()) {
                neighborsArray.put(neighborId);
            }
            waypointJson.put("neighbors", neighborsArray);
            
            // Save connections
            JSONObject connectionsJson = new JSONObject();
            for (int neighborHash : waypoint.getConnectedNeighbors()) {
                SimpleRoutePoint.Connection conn = waypoint.getConnection(neighborHash);
                if (conn != null) {
                    JSONObject connJson = new JSONObject();
                    connJson.put("connectionTo", conn.connectionTo);
                    connJson.put("gobHash", conn.gobHash);
                    connJson.put("gobName", conn.gobName);
                    connJson.put("isDoor", conn.isDoor);
                    connectionsJson.put(String.valueOf(neighborHash), connJson);
                }
            }
            waypointJson.put("connections", connectionsJson);
            
            waypointsArray.put(waypointJson);
        }
        json.put("waypoints", waypointsArray);
        
        return json;
    }
}

