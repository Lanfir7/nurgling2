package nurgling.routes;

import nurgling.NConfig;
import nurgling.profiles.ConfigFactory;
import nurgling.profiles.ProfileAwareService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Менеджер для управления простыми маршрутами без привязки к зонам.
 */
public class SimpleRouteManager implements ProfileAwareService {
    private final Map<Integer, SimpleRoute> routes = new HashMap<>();
    private Map<Integer, SimpleRoutePoint> routePointMap = new HashMap<>();
    private String genus;
    private String configPath;

    public SimpleRouteManager() {
        this.configPath = NConfig.getGlobalInstance().getRoutesPath();
        // Используем отдельный файл для простых маршрутов
        this.configPath = this.configPath.replace(".json", "_simple.json");
        loadRoutes();
    }

    /**
     * Constructor for profile-aware initialization
     */
    public SimpleRouteManager(String genus) {
        this.genus = genus;
        initializeForProfile(genus);
    }

    // ProfileAwareService implementation

    @Override
    public void initializeForProfile(String genus) {
        this.genus = genus;
        NConfig config = ConfigFactory.getConfig(genus);
        this.configPath = config.getRoutesPath();
        this.configPath = this.configPath.replace(".json", "_simple.json");
        load();
    }

    @Override
    public String getGenus() {
        return genus;
    }

    @Override
    public void load() {
        loadRoutes();
    }

    @Override
    public void save() {
        saveRoutes();
    }

    public void updateRoute(SimpleRoute route) {
        routes.put(route.id, route);
    }

    public void loadRoutes() {
        if (new File(configPath).exists()) {
            StringBuilder contentBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(configPath), StandardCharsets.UTF_8)) {
                stream.forEach(s -> contentBuilder.append(s).append("\n"));
            } catch (IOException ignore) {
            }

            if (!contentBuilder.toString().isEmpty()) {
                JSONObject main = new JSONObject(contentBuilder.toString());
                JSONArray array = (JSONArray) main.get("routes");
                for (int i = 0; i < array.length(); i++) {
                    SimpleRoute route = new SimpleRoute((JSONObject) array.get(i), this.routePointMap);
                    routes.put(route.id, route);
                }
            }
        }
    }

    public void saveRoutes() {
        try {
            JSONObject main = new JSONObject();
            JSONArray routesArray = new JSONArray();
            
            for (SimpleRoute route : routes.values()) {
                routesArray.put(route.toJson());
            }
            
            main.put("routes", routesArray);
            
            Files.write(Paths.get(configPath), main.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save simple routes: " + e.getMessage());
        }
    }

    public Map<Integer, SimpleRoute> getRoutes() {
        return routes;
    }

    public SimpleRoute getRoute(int id) {
        return routes.get(id);
    }

    public void addRoute(SimpleRoute route) {
        routes.put(route.id, route);
    }

    public void deleteRoute(SimpleRoute route) {
        // Remove route points that are not used in any remaining routes
        Set<Integer> referencedPointIds = new HashSet<>();
        for (SimpleRoute remainingRoute : routes.values()) {
            if (remainingRoute.id == route.id) continue;
            for (SimpleRoutePoint routePoint : remainingRoute.waypoints) {
                referencedPointIds.add(routePoint.id);
            }
        }

        // Only clean up RoutePoints not used in any remaining routes
        for (SimpleRoutePoint routePoint : route.waypoints) {
            if (!referencedPointIds.contains(routePoint.id)) {
                deleteRoutePointFromNeighborsAndConnections(routePoint);
            }
        }

        routes.remove(route.id);
    }

    public void deleteRoutePointFromNeighborsAndConnections(SimpleRoutePoint routePoint) {
        for (SimpleRoute route : routes.values()) {
            for (SimpleRoutePoint point : route.waypoints) {
                if (point.neighbors != null && point.neighbors.contains(routePoint.id)) {
                    point.neighbors.remove(Integer.valueOf(routePoint.id));
                    point.removeConnection(routePoint.id);
                }
            }
        }
    }

    public void updateConnections(SimpleRoutePoint newRoutePoint, int newId) {
        String oldIdStr = String.valueOf(newRoutePoint.id);

        for (SimpleRoute route : routes.values()) {
            for (SimpleRoutePoint routePoint : route.waypoints) {
                if(routePoint.id == newId) {
                    mergeConnectionsAndNeighbors(newRoutePoint, routePoint);
                } else if (routePoint.id == newRoutePoint.id) {
                    mergeConnectionsAndNeighbors(newRoutePoint, routePoint);
                }
                // Update neighbors list if oldId is found
                for (int i = 0; i < routePoint.neighbors.size(); i++) {
                    if (routePoint.neighbors.get(i) == newRoutePoint.id) {
                        routePoint.neighbors.set(i, newId);
                    }
                }

                // Use a list to accumulate keys to be removed or updated
                List<Integer> keysToRemove = new ArrayList<>();
                Map<Integer, SimpleRoutePoint.Connection> updatedConnections = new HashMap<>();

                // Iterate through the map and collect modifications
                for (Map.Entry<Integer, SimpleRoutePoint.Connection> entry : routePoint.connections.entrySet()) {
                    SimpleRoutePoint.Connection connection = entry.getValue();

                    // Check if connectionTo matches oldId (using String comparison)
                    if (connection.connectionTo.equals(oldIdStr)) {
                        // Update the connectionTo with the newId
                        connection.connectionTo = String.valueOf(newId);

                        // Mark the current key for removal and collect the updated connection
                        keysToRemove.add(entry.getKey());
                        updatedConnections.put(newId, connection);
                    }
                }

                // Now remove the old entries
                for (Integer key : keysToRemove) {
                    routePoint.connections.remove(key);
                }

                // Add the new entries with the updated key (newId)
                for (Map.Entry<Integer, SimpleRoutePoint.Connection> entry : updatedConnections.entrySet()) {
                    routePoint.connections.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void mergeConnectionsAndNeighbors(SimpleRoutePoint a, SimpleRoutePoint b) {
        // Merge neighbors (b → a)
        List<Integer> bNeighbors = new ArrayList<>(b.neighbors);
        for (int neighbor : bNeighbors) {
            if (!a.neighbors.contains(neighbor) && neighbor != a.id) {
                a.neighbors.add(neighbor);
            }
        }

        // Merge neighbors (a → b)
        List<Integer> aNeighbors = new ArrayList<>(a.neighbors);
        for (int neighbor : aNeighbors) {
            if (!b.neighbors.contains(neighbor) && neighbor != b.id) {
                b.neighbors.add(neighbor);
            }
        }

        // Merge connections (b → a)
        for (Map.Entry<Integer, SimpleRoutePoint.Connection> entry : b.connections.entrySet()) {
            a.connections.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Merge connections (a → b)
        for (Map.Entry<Integer, SimpleRoutePoint.Connection> entry : a.connections.entrySet()) {
            b.connections.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }
}

