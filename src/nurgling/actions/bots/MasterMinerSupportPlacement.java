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

    /** Chooses a side tile beside the main passage. */
    static Coord chooseAdjacent(Coord origin, OpenTile openTile) {
        return chooseAdjacent(origin, origin, openTile, tile -> false, tile -> true);
    }

    /** Uses the click corridor first, then a nearby return tile if its direction is clearer. */
    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile) {
        return chooseAdjacent(storedOrigin, returnedMinerTile, openTile, tile -> false, tile -> true);
    }

    static Coord chooseAdjacent(Coord origin, OpenTile openTile, OpenTile availableTile) {
        return chooseAdjacent(origin, origin, openTile, tile -> false, availableTile);
    }

    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile,
                                OpenTile availableTile) {
        return chooseAdjacent(storedOrigin, returnedMinerTile, openTile, tile -> false, availableTile);
    }

    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile,
                                OpenTile mineableTile, OpenTile availableTile) {
        return chooseAdjacent(storedOrigin, returnedMinerTile, openTile, tile -> true,
                mineableTile, availableTile);
    }

    static Coord chooseAdjacent(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile,
                                OpenTile knownTile, OpenTile mineableTile, OpenTile availableTile) {
        if (openTile == null || knownTile == null || mineableTile == null || availableTile == null)
            return null;
        Coord origin = corridorOrigin(storedOrigin, returnedMinerTile, openTile);
        Coord[] sides = sideRays(origin, openTile);
        if (sides == null) return null;

        for (int pass = 0; pass < 2; pass++) {
            for (Coord ray : sides) {
                Coord seed = origin.add(ray);
                if (seed.equals(storedOrigin) || !availableTile.test(seed)
                        || !hasKnownSide(origin, seed, ray, knownTile)) continue;
                if (pass == 1 && !isLeaf(origin, seed, openTile)) continue;
                if (pass == 0 ? openTile.test(seed) : !openTile.test(seed) && mineableTile.test(seed))
                    return seed;
            }
        }
        return null;
    }

    static Coord corridorOrigin(Coord storedOrigin, Coord returnedMinerTile, OpenTile openTile) {
        if (openTile == null) return null;
        boolean nearbyReturn = storedOrigin == null || returnedMinerTile != null
                && Math.abs(storedOrigin.x - returnedMinerTile.x)
                + Math.abs(storedOrigin.y - returnedMinerTile.y) <= 1;
        // Prefer a complete corridor over a nearby end tile; the return path can leave the
        // miner one tile off the click position, including inside an existing side branch.
        if (isStraightCorridor(storedOrigin, openTile)) return storedOrigin;
        if (nearbyReturn && isStraightCorridor(returnedMinerTile, openTile)) return returnedMinerTile;
        if (sideRays(storedOrigin, openTile) != null) return storedOrigin;
        return nearbyReturn && sideRays(returnedMinerTile, openTile) != null
                ? returnedMinerTile : null;
    }

    static boolean isSideBranch(Coord origin, Coord target, OpenTile openTile) {
        return isSideBranch(origin, target, openTile, tile -> true, true);
    }

    static boolean isSideBranch(Coord origin, Coord target, OpenTile openTile,
                                OpenTile knownTile, boolean requireLeaf) {
        Coord[] sides = sideRays(origin, openTile);
        if (sides == null || target == null) return false;
        for (Coord ray : sides) {
            if (origin.add(ray).equals(target))
                return hasKnownSide(origin, target, ray, knownTile)
                        && (!requireLeaf || isLeaf(origin, target, openTile));
        }
        return false;
    }

    private static boolean hasKnownSide(Coord origin, Coord target, Coord ray, OpenTile knownTile) {
        if (!knownTile.test(origin) || !knownTile.test(origin.add(-ray.x, -ray.y))) return false;
        if (!knownTile.test(target)) return false;
        for (Coord neighbor : CARDINAL_RAYS) {
            if (!knownTile.test(target.add(neighbor))) return false;
        }
        return true;
    }

    private static Coord[] sideRays(Coord origin, OpenTile openTile) {
        if (origin == null || !openTile.test(origin)) return null;
        boolean north = openTile.test(origin.add(0, -1));
        boolean south = openTile.test(origin.add(0, 1));
        boolean west = openTile.test(origin.add(-1, 0));
        boolean east = openTile.test(origin.add(1, 0));
        int count = (north ? 1 : 0) + (south ? 1 : 0)
                + (west ? 1 : 0) + (east ? 1 : 0);
        boolean vertical = north && south;
        boolean horizontal = west && east;
        if (vertical == horizontal) {
            if (count == 1) {
                vertical = north || south;
            } else if (count == 2) {
                // At the corridor end, a side spur can make the two visible neighbors
                // look like a bend. The longer mined run identifies the main passage.
                int verticalRun = Math.max(openRun(origin, CARDINAL_RAYS[0], openTile),
                        openRun(origin, CARDINAL_RAYS[3], openTile));
                int horizontalRun = Math.max(openRun(origin, CARDINAL_RAYS[1], openTile),
                        openRun(origin, CARDINAL_RAYS[2], openTile));
                if ((verticalRun < 2 && horizontalRun < 2) || verticalRun == horizontalRun)
                    return null;
                vertical = verticalRun > horizontalRun;
            } else {
                return null;
            }
        }
        return vertical ? new Coord[]{CARDINAL_RAYS[1], CARDINAL_RAYS[2]}
                : new Coord[]{CARDINAL_RAYS[0], CARDINAL_RAYS[3]};
    }

    private static int openRun(Coord origin, Coord ray, OpenTile openTile) {
        int run = 0;
        for (int step = 1; step <= 5 && openTile.test(origin.add(ray.x * step, ray.y * step)); step++)
            run++;
        return run;
    }

    private static boolean isStraightCorridor(Coord origin, OpenTile openTile) {
        if (origin == null || !openTile.test(origin)) return false;
        boolean vertical = openTile.test(origin.add(0, -1)) && openTile.test(origin.add(0, 1));
        boolean horizontal = openTile.test(origin.add(-1, 0)) && openTile.test(origin.add(1, 0));
        return vertical != horizontal;
    }

    private static boolean isLeaf(Coord origin, Coord target, OpenTile openTile) {
        for (Coord ray : CARDINAL_RAYS) {
            Coord adjacent = target.add(ray);
            if (!adjacent.equals(origin) && openTile.test(adjacent)) return false;
        }
        return true;
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
