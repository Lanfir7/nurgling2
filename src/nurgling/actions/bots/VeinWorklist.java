package nurgling.actions.bots;

import haven.Coord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private final List<Coord> minedTiles = new ArrayList<Coord>();

    public VeinWorklist(String type, Coord seed) {
        this.type = type;
        if (seed != null) {
            markMined(seed);
        }
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

    public void scanVisible(Function<Coord, String> visibleType, Function<Coord, Boolean> safe) {
        if (visibleType == null || safe == null) {
            return;
        }
        List<Coord> snapshot = new ArrayList<Coord>(minedTiles);
        for (Coord m : snapshot) {
            for (int[] d : NEIGHBORS) {
                Coord n = new Coord(m.x + d[0], m.y + d[1]);
                offer(n, visibleType.apply(n), Boolean.TRUE.equals(safe.apply(n)));
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
        mined.add(k);
        queued.remove(k);
        queue.remove(tile);
        if (!minedTiles.contains(tile)) {
            minedTiles.add(tile);
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    private boolean adjacentToMined(Coord tile) {
        for (Coord m : minedTiles) {
            if (Math.max(Math.abs(tile.x - m.x), Math.abs(tile.y - m.y)) == 1) {
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
