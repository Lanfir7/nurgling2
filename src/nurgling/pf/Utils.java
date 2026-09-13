package nurgling.pf;

import haven.*;
import nurgling.NUtils;

public class Utils
{
    public static Coord toPfGrid(Coord2d coord)
    {
        return coord.div(MCache.tilehsz).round();
    }

    public static Coord2d pfGridToWorld(Coord coord)
    {
        return coord.mul(MCache.tilehsz);
    }

    /**
     * Gob-stream window around the player: 9×9 cells of 100 world units (~81 tiles).
     * Same math as NMiniMap.drawview / ExploredArea. Pair is half-open [ul, br).
     */
    public static Pair<Coord2d, Coord2d> visibleBounds(Coord2d playerRc) {
        if (playerRc == null) {
            return null;
        }
        Coord2d sgridsz = new Coord2d(100, 100);
        Coord2d ul = playerRc.floor(sgridsz).sub(4, 4).mul(sgridsz);
        return Pair.of(ul, ul.add(sgridsz.mul(9)));
    }

    /**
     * Check if a world coordinate is inside the player's 81-tile visible area.
     * Uses the same calculation as ExploredArea and NMiniMap for consistency.
     *
     * @param coord2d the world coordinate to check
     * @return true if inside visible area, false otherwise
     */
    public static boolean inVisibleArea(Coord2d coord2d) {
        Gob player = NUtils.player();
        if (player == null || coord2d == null) {
            return false;
        }
        Pair<Coord2d, Coord2d> vis = visibleBounds(player.rc);
        return coord2d.x >= vis.a.x && coord2d.x < vis.b.x &&
                coord2d.y >= vis.a.y && coord2d.y < vis.b.y;
    }

    /**
     * True when the whole drop rectangle is already inside the gob-stream window,
     * so walking to a zone corner is unnecessary. {@code area.b} is treated as
     * exclusive, matching {@link nurgling.areas.NArea#getRCArea()}.
     */
    public static boolean areaFullyInVisibleArea(Pair<Coord2d, Coord2d> area) {
        Gob player = NUtils.player();
        if (player == null) {
            return false;
        }
        return areaFullyInVisibleArea(area, player.rc);
    }

    public static boolean areaFullyInVisibleArea(Pair<Coord2d, Coord2d> area, Coord2d playerRc) {
        if (area == null || area.a == null || area.b == null || playerRc == null) {
            return false;
        }
        Pair<Coord2d, Coord2d> vis = visibleBounds(playerRc);
        double minX = Math.min(area.a.x, area.b.x);
        double maxX = Math.max(area.a.x, area.b.x);
        double minY = Math.min(area.a.y, area.b.y);
        double maxY = Math.max(area.a.y, area.b.y);
        return minX >= vis.a.x && minY >= vis.a.y && maxX <= vis.b.x && maxY <= vis.b.y;
    }
}
