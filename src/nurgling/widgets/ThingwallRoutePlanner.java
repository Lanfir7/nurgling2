package nurgling.widgets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure route model for learned Thingwall markers. */
public final class ThingwallRoutePlanner {
    private ThingwallRoutePlanner() {
    }

    public static final class Province {
        public final String id;
        public final String name;
        public final List<String> neighbors;

        public Province(String id, String name, Collection<String> neighbors) {
            this.id = id;
            this.name = name;
            this.neighbors = new ArrayList<>(neighbors == null ? Collections.<String>emptyList() : neighbors);
        }
    }

    public static final class Marker {
        public final String provinceId;
        public final String name;
        public final long segment;
        public final int x;
        public final int y;
        public final boolean learned;

        public Marker(String provinceId, String name, long segment, int x, int y, boolean learned) {
            this.provinceId = provinceId;
            this.name = name;
            this.segment = segment;
            this.x = x;
            this.y = y;
            this.learned = learned;
        }
    }

    public static final class Route {
        public final String destinationId;
        public final String destinationName;
        public final List<String> path;
        /** Province names in path order; these are the names expected by Thingwall dialogs. */
        public final List<String> hopNames;
        public final double distanceTiles;

        private Route(String destinationId, String destinationName, List<String> path,
                      List<String> hopNames, double distanceTiles) {
            this.destinationId = destinationId;
            this.destinationName = destinationName;
            this.path = Collections.unmodifiableList(path);
            this.hopNames = Collections.unmodifiableList(hopNames);
            this.distanceTiles = distanceTiles;
        }
    }

    /** A server-advertised one-hop route, usable without a province/map snapshot. */
    public static Route directRoute(String destinationId, String destinationName, String sourceName,
                                    double distanceTiles) {
        return(new Route(destinationId, destinationName,
            Arrays.asList(sourceName, destinationName), Arrays.asList(sourceName, destinationName), distanceTiles));
    }

    /** Combines graph routes with advertised direct routes, keeping graph routes on name collisions. */
    public static List<Route> mergeRoutes(Collection<Route> graphRoutes, Collection<Route> directRoutes) {
        Map<String, Route> result = new LinkedHashMap<>();
        addRoutes(result, graphRoutes);
        addRoutes(result, directRoutes);
        List<Route> routes = new ArrayList<>(result.values());
        Collections.sort(routes, routeOrder());
        return(routes);
    }

    public static List<Route> routes(String currentId, Collection<Province> provinces,
                                     Collection<Marker> markers, long segment) {
        if(currentId == null)
            return(Collections.emptyList());
        Map<String, Province> graph = new HashMap<>();
        if(provinces != null) {
            for(Province province : provinces) {
                if((province != null) && (province.id != null))
                    graph.put(province.id, province);
            }
        }
        Map<String, Marker> learned = learnedMarkers(markers, segment);
        Marker source = learned.get(currentId);
        if((source == null) || !graph.containsKey(currentId))
            return(Collections.emptyList());

        Map<String, String> previous = shortestPaths(currentId, graph, learned.keySet());
        List<Route> result = new ArrayList<>();
        for(Map.Entry<String, Marker> entry : learned.entrySet()) {
            String destinationId = entry.getKey();
            if(destinationId.equals(currentId) || !previous.containsKey(destinationId))
                continue;
            List<String> path = pathTo(destinationId, previous);
            List<String> hopNames = new ArrayList<>(path.size());
            boolean valid = true;
            for(String id : path) {
                Province province = graph.get(id);
                Marker marker = learned.get(id);
                String name = (province == null) ? null : province.name;
                if((name == null) || name.isEmpty())
                    name = (marker == null) ? null : marker.name;
                if((name == null) || name.isEmpty()) {
                    valid = false;
                    break;
                }
                hopNames.add(name);
            }
            if(!valid)
                continue;
            Marker target = entry.getValue();
            result.add(new Route(destinationId, target.name, path, hopNames,
                Math.hypot(target.x - source.x, target.y - source.y)));
        }
        Collections.sort(result, routeOrder());
        return(result);
    }

    private static void addRoutes(Map<String, Route> result, Collection<Route> routes) {
        if(routes == null)
            return;
        for(Route route : routes) {
            if((route != null) && (route.destinationName != null))
                result.putIfAbsent(route.destinationName, route);
        }
    }

    private static Comparator<Route> routeOrder() {
        return(new Comparator<Route>() {
            public int compare(Route a, Route b) {
                int compared = String.CASE_INSENSITIVE_ORDER.compare(a.destinationName, b.destinationName);
                if(compared != 0)
                    return(compared);
                compared = a.destinationName.compareTo(b.destinationName);
                return((compared != 0) ? compared : a.destinationId.compareTo(b.destinationId));
            }
        });
    }

    private static Map<String, Marker> learnedMarkers(Collection<Marker> markers, long segment) {
        Map<String, Marker> result = new LinkedHashMap<>();
        if(markers == null)
            return(result);
        for(Marker marker : markers) {
            if((marker == null) || !marker.learned || (marker.segment != segment)
                    || (marker.provinceId == null) || (marker.name == null))
                continue;
            Marker previous = result.get(marker.provinceId);
            if((previous == null) || markerOrder(marker, previous) < 0)
                result.put(marker.provinceId, marker);
        }
        return(result);
    }

    private static int markerOrder(Marker a, Marker b) {
        int compared = a.name.compareTo(b.name);
        if(compared != 0)
            return(compared);
        compared = Integer.compare(a.x, b.x);
        return((compared != 0) ? compared : Integer.compare(a.y, b.y));
    }

    private static Map<String, String> shortestPaths(String source, Map<String, Province> graph,
                                                       Set<String> learned) {
        Map<String, String> previous = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        previous.put(source, null);
        queue.add(source);
        while(!queue.isEmpty()) {
            Province province = graph.get(queue.remove());
            if(province == null)
                continue;
            List<String> neighbors = new ArrayList<>(province.neighbors);
            Collections.sort(neighbors);
            for(String neighbor : neighbors) {
                if(!learned.contains(neighbor) || !graph.containsKey(neighbor) || previous.containsKey(neighbor))
                    continue;
                previous.put(neighbor, province.id);
                queue.add(neighbor);
            }
        }
        return(previous);
    }

    private static List<String> pathTo(String destination, Map<String, String> previous) {
        ArrayList<String> reversed = new ArrayList<>();
        for(String current = destination; current != null; current = previous.get(current))
            reversed.add(current);
        Collections.reverse(reversed);
        return(reversed);
    }
}
