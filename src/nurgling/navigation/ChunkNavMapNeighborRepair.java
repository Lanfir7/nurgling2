package nurgling.navigation;

import haven.Coord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs ordinary-walk topology from Haven's persistent MapFile coordinates.
 * Callers obtain references while holding the MapFile read lock.
 */
final class ChunkNavMapNeighborRepair {
    private ChunkNavMapNeighborRepair() {
    }

    interface GridLookup {
        GridRef find(long gridId);
    }

    static final class GridRef {
        final long gridId;
        final long segmentId;
        final Coord segmentCoord;

        GridRef(long gridId, long segmentId, Coord segmentCoord) {
            this.gridId = gridId;
            this.segmentId = segmentId;
            this.segmentCoord = segmentCoord;
        }
    }

    static void repair(ChunkNavGraph graph, GridLookup lookup, long currentInstanceId, long playerGridId) {
        repair(graph, lookup, currentInstanceId, false, playerGridId);
    }

    static RepairResult repair(ChunkNavGraph graph, GridLookup lookup, long currentInstanceId,
                               boolean currentInstanceConfirmed, long playerGridId) {
        if (graph == null || lookup == null || playerGridId < 0) {
            return RepairResult.none();
        }

        Map<Long, ChunkNavData> chunks = new HashMap<>();
        Map<Long, GridRef> refs = new HashMap<>();
        Map<MapCoord, Long> coordinateIndex = new HashMap<>();
        for (ChunkNavData chunk : graph.getAllChunks()) {
            if (chunk == null || chunk.layer == null) {
                continue;
            }
            GridRef ref = lookup.find(chunk.gridId);
            if (ref != null && ref.segmentCoord != null) {
                chunks.put(chunk.gridId, chunk);
                refs.put(chunk.gridId, ref);
                coordinateIndex.put(new MapCoord(ref.segmentId, chunk.layer, ref.segmentCoord), chunk.gridId);
            }
        }
        if (!chunks.containsKey(playerGridId)) return RepairResult.none();

        Map<Long, List<Link>> links = indexLinks(chunks, refs, coordinateIndex);
        Set<Long> component = component(playerGridId, links);
        if (component.size() < 2) return RepairResult.none();

        Selection selection = selectCanonical(component, chunks, currentInstanceId,
                currentInstanceConfirmed);
        if (selection.instanceId != 0) {
            repairComponent(graph, chunks, component, links, selection.instanceId);
            return new RepairResult(selection.trusted ? selection.instanceId : 0, component);
        }
        if (allSameInstance(component, chunks)) {
            repairComponent(graph, chunks, component, links, chunks.get(playerGridId).instanceId);
        }
        return RepairResult.none();
    }

    private static Map<Long, List<Link>> indexLinks(Map<Long, ChunkNavData> chunks,
                                                     Map<Long, GridRef> refs,
                                                     Map<MapCoord, Long> coordinateIndex) {
        Map<Long, List<Link>> links = new HashMap<>();
        for (Map.Entry<Long, ChunkNavData> entry : chunks.entrySet()) {
            long id = entry.getKey();
            GridRef ref = refs.get(id);
            for (ChunkNavData.Direction direction : ChunkNavData.Direction.values()) {
                Long otherId = coordinateIndex.get(new MapCoord(ref.segmentId, entry.getValue().layer,
                        adjacent(ref.segmentCoord, direction)));
                if (otherId == null || id >= otherId) continue;
                Link link = new Link(id, otherId, direction);
                links.computeIfAbsent(id, ignored -> new ArrayList<>()).add(link);
                links.computeIfAbsent(otherId, ignored -> new ArrayList<>()).add(link);
            }
        }
        return links;
    }

    private static Set<Long> component(long first, Map<Long, List<Link>> links) {
        Set<Long> component = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(first);
        while (!pending.isEmpty()) {
            long id = pending.removeFirst();
            if (!component.add(id)) {
                continue;
            }
            for (Link link : links.getOrDefault(id, java.util.Collections.emptyList())) {
                long other = link.other(id);
                if (!component.contains(other)) {
                    pending.addLast(other);
                }
            }
        }
        return component;
    }

    private static Selection selectCanonical(Set<Long> component, Map<Long, ChunkNavData> chunks,
                                             long currentInstanceId, boolean currentInstanceConfirmed) {
        // Mines share the "outside" layer with surface. The current player component,
        // rather than the layer name, is the cave-instance safety anchor.
        if (currentInstanceConfirmed && currentInstanceId != 0) {
            return new Selection(currentInstanceId, true);
        }
        Map<Long, Integer> counts = new HashMap<>();
        int total = 0;
        for (Long id : component) {
            long instanceId = chunks.get(id).instanceId;
            if (instanceId != 0) {
                counts.put(instanceId, counts.getOrDefault(instanceId, 0) + 1);
                total++;
            }
        }
        long candidate = 0;
        int candidateCount = 0;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > candidateCount) {
                candidate = entry.getKey();
                candidateCount = entry.getValue();
            }
        }
        if (candidateCount > total - candidateCount) return new Selection(candidate, true);
        return Selection.none();
    }

    private static boolean allSameInstance(Set<Long> component, Map<Long, ChunkNavData> chunks) {
        long instanceId = Long.MIN_VALUE;
        for (Long id : component) {
            long next = chunks.get(id).instanceId;
            if (instanceId != Long.MIN_VALUE && instanceId != next) return false;
            instanceId = next;
        }
        return true;
    }

    private static void repairComponent(ChunkNavGraph graph, Map<Long, ChunkNavData> chunks,
                                        Set<Long> component, Map<Long, List<Link>> links,
                                        long targetInstance) {
        Map<Long, NeighborState> desired = desiredTopology(component, links);
        Set<ChunkNavData> changedComponentChunks = new HashSet<>();
        for (ChunkNavData chunk : graph.getAllChunks()) {
            if (!component.contains(chunk.gridId)) {
                boolean changed = chunk.connectedChunks.removeAll(component);
                changed |= clearNeighborsPointingAt(chunk, component);
                if (changed) chunk.markUpdated();
            }
        }

        for (Long id : component) {
            ChunkNavData chunk = chunks.get(id);
            if (chunk.instanceId != targetInstance) {
                chunk.instanceId = targetInstance;
                changedComponentChunks.add(chunk);
            }
        }

        // All component members now share an instance, so restoring reciprocal
        // edges below can never expose a temporary cross-instance connection.
        for (Long id : component) {
            ChunkNavData chunk = chunks.get(id);
            NeighborState topology = desired.get(id);
            boolean changed = setNeighbors(chunk, topology);
            if (!chunk.connectedChunks.equals(topology.connected)) {
                chunk.connectedChunks.clear();
                chunk.connectedChunks.addAll(topology.connected);
                changed = true;
            }
            if (changed) changedComponentChunks.add(chunk);
        }
        for (ChunkNavData chunk : changedComponentChunks) {
            chunk.markUpdated();
        }
    }

    private static Map<Long, NeighborState> desiredTopology(Set<Long> component,
                                                              Map<Long, List<Link>> links) {
        Map<Long, NeighborState> desired = new HashMap<>();
        for (Long id : component) {
            desired.put(id, new NeighborState());
        }
        for (Long id : component) {
            for (Link link : links.getOrDefault(id, java.util.Collections.emptyList())) {
                if (!component.contains(link.firstId) || !component.contains(link.secondId) || id != link.firstId) {
                    continue;
                }
                desired.get(link.firstId).set(link.direction, link.secondId);
                desired.get(link.secondId).set(link.direction.opposite(), link.firstId);
            }
        }
        return desired;
    }

    private static Coord adjacent(Coord coord, ChunkNavData.Direction direction) {
        switch (direction) {
            case NORTH: return coord.add(0, -1);
            case SOUTH: return coord.add(0, 1);
            case EAST: return coord.add(1, 0);
            case WEST: return coord.add(-1, 0);
            default: return coord;
        }
    }

    private static boolean clearNeighborsPointingAt(ChunkNavData chunk, Set<Long> ids) {
        boolean changed = false;
        if (ids.contains(chunk.neighborNorth)) { chunk.neighborNorth = -1; changed = true; }
        if (ids.contains(chunk.neighborSouth)) { chunk.neighborSouth = -1; changed = true; }
        if (ids.contains(chunk.neighborEast)) { chunk.neighborEast = -1; changed = true; }
        if (ids.contains(chunk.neighborWest)) { chunk.neighborWest = -1; changed = true; }
        return changed;
    }

    private static boolean setNeighbors(ChunkNavData chunk, NeighborState desired) {
        boolean changed = chunk.neighborNorth != desired.north || chunk.neighborSouth != desired.south
                || chunk.neighborEast != desired.east || chunk.neighborWest != desired.west;
        if (changed) {
            chunk.neighborNorth = desired.north;
            chunk.neighborSouth = desired.south;
            chunk.neighborEast = desired.east;
            chunk.neighborWest = desired.west;
        }
        return changed;
    }

    static final class RepairResult {
        final long trustedInstanceId;
        final Set<Long> repairedComponent;

        RepairResult(long trustedInstanceId, Set<Long> repairedComponent) {
            this.trustedInstanceId = trustedInstanceId;
            this.repairedComponent = repairedComponent;
        }

        static RepairResult none() {
            return new RepairResult(0, java.util.Collections.emptySet());
        }
    }

    private static final class Selection {
        final long instanceId;
        final boolean trusted;

        Selection(long instanceId, boolean trusted) {
            this.instanceId = instanceId;
            this.trusted = trusted;
        }

        static Selection none() {
            return new Selection(0, false);
        }
    }

    private static final class MapCoord {
        final long segmentId;
        final String layer;
        final Coord coord;

        MapCoord(long segmentId, String layer, Coord coord) {
            this.segmentId = segmentId;
            this.layer = layer;
            this.coord = coord;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MapCoord)) return false;
            MapCoord that = (MapCoord) other;
            return segmentId == that.segmentId && layer.equals(that.layer) && coord.equals(that.coord);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(segmentId);
            result = 31 * result + layer.hashCode();
            return 31 * result + coord.hashCode();
        }
    }

    private static final class NeighborState {
        long north = -1;
        long south = -1;
        long east = -1;
        long west = -1;
        final Set<Long> connected = new HashSet<>();

        void set(ChunkNavData.Direction direction, long id) {
            switch (direction) {
                case NORTH: north = id; break;
                case SOUTH: south = id; break;
                case EAST: east = id; break;
                case WEST: west = id; break;
                default: break;
            }
            connected.add(id);
        }
    }

    private static final class Link {
        final long firstId;
        final long secondId;
        final ChunkNavData.Direction direction;

        Link(long firstId, long secondId, ChunkNavData.Direction direction) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.direction = direction;
        }

        long other(long id) {
            return id == firstId ? secondId : firstId;
        }
    }
}
