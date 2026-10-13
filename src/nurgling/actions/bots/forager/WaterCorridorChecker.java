package nurgling.actions.bots.forager;

import haven.Coord;
import haven.Coord2d;
import haven.Line2d;
import haven.Loading;
import haven.MCache;

/** On foot, deep water cannot be waded. A straight corridor that crosses it means the target is
 *  on the other bank; PathFinder would otherwise grow its grid looking for a ford that is not there. */
public class WaterCorridorChecker {
    private WaterCorridorChecker() {}

    private static final int MAX_CORRIDOR_SAMPLE_TILES = 300;

    @FunctionalInterface
    interface TileName {
        String at(Coord tile) throws Loading;
    }

    public static boolean corridorBlocked(MCache map, Coord2d from, Coord2d to) {
        if (map == null) return false;
        return corridorBlocked(from, to, tile -> map.tilesetname(map.gettile(tile)));
    }

    /** Deep and very-deep water only. Shallow water stays crossable. */
    static boolean isImpassableWater(String name) {
        return "gfx/tiles/deep".equals(name) || "gfx/tiles/odeep".equals(name)
                || "gfx/tiles/vdeep".equals(name) || "gfx/tiles/ovdeep".equals(name);
    }

    static boolean corridorBlocked(Coord2d from, Coord2d to, TileName names) {
        if (from == null || to == null || names == null) return false;

        double dist = from.dist(to);
        if (dist < 0.01) {
            return impassable(names, from.floor(MCache.tilesz));
        }
        Coord2d cappedTo = to;
        double maxDist = MAX_CORRIDOR_SAMPLE_TILES * MCache.tilesz.x;
        if (dist > maxDist) {
            cappedTo = from.add(to.sub(from).mul(maxDist / dist));
        }

        Coord2d prev = null;
        for (Coord2d p : new Line2d.GridIsect(from, cappedTo, MCache.tilesz, true)) {
            if (prev != null) {
                Coord tile = prev.add(p).div(2).floor(MCache.tilesz);
                if (impassable(names, tile)) return true;
            }
            prev = p;
        }
        return false;
    }

    private static boolean impassable(TileName names, Coord tile) {
        try {
            return isImpassableWater(names.at(tile));
        } catch (Loading l) {
            return false;
        }
    }
}
