package nurgling.actions.bots;

import haven.Coord;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Geometry-only choice of a safe Stone Column tile beside the active tunnel. */
final class MasterMinerSupportPlacement {
    private static final int MAX_SEARCH_DISTANCE = 3;
    private static final Coord[] CARDINAL_RAYS = {
            new Coord(0, -1), // north
            new Coord(-1, 0), // west
            new Coord(1, 0),  // east
            new Coord(0, 1)   // south
    };
    interface OpenTile {
        boolean test(Coord tile);
    }

    private MasterMinerSupportPlacement() {
    }

    /**
     * Chooses an isolated one-cell side niche. Each adjacent open tile is explored as a bounded
     * cardinal component with the miner's tile blocked, so a turning tunnel is never a niche.
     * Ties use CARDINAL_RAYS order.
     */
    static Coord chooseAdjacent(Coord origin, OpenTile openTile) {
        return chooseAdjacent(origin, openTile, tile -> true);
    }

    static Coord chooseAdjacent(Coord origin, OpenTile openTile, OpenTile availableTile) {
        if (origin == null || openTile == null || availableTile == null) {
            return null;
        }
        for (Coord ray : CARDINAL_RAYS) {
            Coord seed = origin.add(ray);
            if (openTile.test(seed) && availableTile.test(seed) && isIsolatedNiche(origin, seed, openTile)) {
                return seed;
            }
        }
        return null;
    }

    private static boolean isIsolatedNiche(Coord origin, Coord seed, OpenTile openTile) {
        Set<Coord> visited = new HashSet<>();
        ArrayDeque<Coord> pending = new ArrayDeque<>();
        visited.add(seed);
        pending.add(seed);
        while (!pending.isEmpty()) {
            Coord current = pending.removeFirst();
            for (Coord ray : CARDINAL_RAYS) {
                Coord next = current.add(ray);
                if (next.equals(origin) || origin.dist(next) > MAX_SEARCH_DISTANCE
                        || !openTile.test(next) || !visited.add(next)) {
                    continue;
                }
                if (visited.size() > 1) {
                    return false;
                }
                pending.addLast(next);
            }
        }
        return true;
    }

    /** Only already-mined underground floor is a valid construction tile. */
    static boolean isOpenCaveTileName(String resourceName) {
        if (resourceName == null) {
            return false;
        }
        return resourceName.startsWith("gfx/tiles/deepcave")
                || resourceName.startsWith("gfx/tiles/deeptangle")
                || resourceName.contains("/cavein")
                || resourceName.contains("/caveout");
    }
}
