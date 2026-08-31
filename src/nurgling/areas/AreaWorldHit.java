package nurgling.areas;

import haven.Coord2d;
import haven.Pair;

public final class AreaWorldHit {
    public enum Kind { BOUNDARY, GOB, AREA, TILE, SERVER }

    private AreaWorldHit() {}

    /** Inclusive axis-aligned hit, matching {@link NArea#checkHit}. */
    public static boolean contains(Pair<Coord2d, Coord2d> rcArea, Coord2d pos) {
        if (rcArea == null || pos == null || rcArea.a == null || rcArea.b == null)
            return false;
        Coord2d begin = rcArea.a;
        Coord2d end = rcArea.b;
        return pos.x >= begin.x && pos.x <= end.x && pos.y >= begin.y && pos.y <= end.y;
    }

    public static double areaSize(Pair<Coord2d, Coord2d> rcArea) {
        if (rcArea == null || rcArea.a == null || rcArea.b == null)
            return Double.POSITIVE_INFINITY;
        return Math.abs((rcArea.b.x - rcArea.a.x) * (rcArea.b.y - rcArea.a.y));
    }

    /**
     * Smallest (most specific) area whose {@link NArea#getRCArea()} contains {@code mc}.
     * Independent of editor/overlay visibility and {@link AreaLabelSync#labelsClickable}.
     */
    public static NArea smallestContaining(Iterable<NArea> areas, Coord2d mc) {
        if (areas == null || mc == null)
            return null;
        NArea best = null;
        double bestSize = Double.POSITIVE_INFINITY;
        for (NArea area : areas) {
            if (area == null || !area.checkHit(mc))
                continue;
            double size = areaSize(area.getRCArea());
            if (size < bestSize) {
                bestSize = size;
                best = area;
            }
        }
        return best;
    }

    /**
     * Ctrl+RMB dispatch: claim bounds stay on the server; registered gob actions
     * beat area menus; otherwise the smallest containing area; else tile/server.
     */
    public static Kind decide(boolean boundaryGob, boolean gobHasActions, boolean pointInArea, boolean tileHasActions) {
        if (boundaryGob)
            return Kind.BOUNDARY;
        if (gobHasActions)
            return Kind.GOB;
        if (pointInArea)
            return Kind.AREA;
        if (tileHasActions)
            return Kind.TILE;
        return Kind.SERVER;
    }
}
