package nurgling.actions.bots;

import haven.Coord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class VeinWorklist {
    public static final int[][] NEIGHBORS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}
    };

    private final String type;
    private final Set<Long> mined = new HashSet<Long>();
    private final Set<Long> queued = new HashSet<Long>();
    private final List<Coord> queue = new ArrayList<Coord>();
    // Only unmined neighbours can reveal more of the vein. Keeping the boundary
    // avoids querying every historical tile (and its support) after each stone.
    private final Map<Long, Coord> boundary = new LinkedHashMap<Long, Coord>();

    public VeinWorklist(String type, Coord seed) {
        this(type, seed, true);
    }

    private VeinWorklist(String type, Coord seed, boolean seedAlreadyMined) {
        this.type = type;
        if (seedAlreadyMined && seed != null) {
            markMined(seed);
        } else if (seed != null) {
            queued.add(key(seed));
            queue.add(seed);
        }
    }

    /** Starts a vein by mining the selected rock before looking for newly opened neighbours. */
    public static VeinWorklist withInitialTarget(String type, Coord seed) {
        return new VeinWorklist(type, seed, false);
    }

    public void offer(Coord tile, String visibleType, boolean safe) {
        if (tile == null || type == null || !type.equals(visibleType) || !safe) {
            return;
        }
        long k = key(tile);
        if (mined.contains(k) || queued.contains(k) || !adjacentToMined(tile)) {
            return;
        }
        queued.add(k);
        queue.add(tile);
    }

    /** Requeues a target that was selected but could not be mined yet. */
    public void requeue(Coord tile) {
        if (tile == null || mined.contains(key(tile)) || queued.contains(key(tile))) {
            return;
        }
        queued.add(key(tile));
        queue.add(tile);
    }

    public void scanVisible(Function<Coord, String> visibleType, Function<Coord, Boolean> safe) {
        if (visibleType == null || safe == null) {
            return;
        }
        for (Coord tile : boundary.values()) {
            if (queued.contains(key(tile))) {
                continue;
            }
            String currentType = visibleType.apply(tile);
            if (type != null && type.equals(currentType)) {
                offer(tile, currentType, Boolean.TRUE.equals(safe.apply(tile)));
            }
        }
    }

    public Coord takeNearest(Coord playerTile) {
        if (playerTile == null || queue.isEmpty()) {
            return null;
        }
        int bestI = 0;
        int bestD = dist2(playerTile, queue.get(0));
        for (int i = 1; i < queue.size(); i++) {
            int d = dist2(playerTile, queue.get(i));
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        Coord chosen = queue.remove(bestI);
        queued.remove(key(chosen));
        return chosen;
    }

    public void markMined(Coord tile) {
        if (tile == null) {
            return;
        }
        long k = key(tile);
        queued.remove(k);
        queue.remove(tile);
        if (!mined.add(k)) {
            return;
        }
        boundary.remove(k);
        for (int[] d : NEIGHBORS) {
            Coord neighbour = new Coord(tile.x + d[0], tile.y + d[1]);
            long nk = key(neighbour);
            if (!mined.contains(nk)) {
                boundary.putIfAbsent(nk, neighbour);
            }
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    private boolean adjacentToMined(Coord tile) {
        for (int[] d : NEIGHBORS) {
            long neighbour = ((long) (tile.x + d[0]) << 32) | ((tile.y + d[1]) & 0xffffffffL);
            if (mined.contains(neighbour)) {
                return true;
            }
        }
        return false;
    }

    private static int dist2(Coord a, Coord b) {
        int dx = a.x - b.x;
        int dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private static long key(Coord c) {
        return ((long) c.x << 32) | (c.y & 0xffffffffL);
    }
}
