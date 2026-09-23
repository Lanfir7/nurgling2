package nurgling.actions.bots;

import haven.Coord;

/** Geometry-only choice of a safe Stone Column tile beside the miner. */
final class MasterMinerSupportPlacement {
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

    /** Chooses the first open cardinal tile in a stable order. */
    static Coord chooseAdjacent(Coord origin, OpenTile openTile) {
        return chooseAdjacent(origin, openTile, tile -> true);
    }

    /** Uses the miner's tile after returning; the stored origin is only a fallback. */
    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile) {
        return chooseAdjacent(returnedMinerTile == null ? storedOrigin : returnedMinerTile, openTile);
    }

    static Coord chooseAdjacent(Coord origin, OpenTile openTile, OpenTile availableTile) {
        if (origin == null || openTile == null || availableTile == null) {
            return null;
        }
        for (Coord ray : CARDINAL_RAYS) {
            Coord seed = origin.add(ray);
            if (openTile.test(seed) && availableTile.test(seed)) {
                return seed;
            }
        }
        return null;
    }

    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile,
                                OpenTile availableTile) {
        return chooseAdjacent(returnedMinerTile == null ? storedOrigin : returnedMinerTile, openTile, availableTile);
    }

    /** Only already-mined underground floor is a valid construction tile. */
    static boolean isOpenCaveTileName(String resourceName) {
        if (resourceName == null) {
            return false;
        }
        return resourceName.equals("gfx/tiles/mine")
                || resourceName.startsWith("gfx/tiles/deepcave")
                || resourceName.startsWith("gfx/tiles/deeptangle")
                || resourceName.contains("/cavein")
                || resourceName.contains("/caveout");
    }
}
