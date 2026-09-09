package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orders placement slots from the corner where an area drag started. */
final class PlacementSweep {
    private PlacementSweep() { }

    static int[] axis(int min, int max, int dragStart, int dragEnd) {
        if (max < min) return new int[0];
        int[] values = new int[max - min + 1];
        boolean reverse = dragEnd < dragStart;
        for (int i = 0; i < values.length; i++) {
            values[i] = reverse ? max - i : min + i;
        }
        return values;
    }

    static List<Coord2d> order(List<Coord2d> positions, Coord start, Coord end, int limit) {
        ArrayList<Coord2d> ordered = new ArrayList<>(positions);
        final boolean reverseX = start != null && end != null && end.x < start.x;
        final boolean reverseY = start != null && end != null && end.y < start.y;
        Comparator<Coord2d> byX = Comparator.comparingDouble(pos -> pos.x);
        Comparator<Coord2d> byY = Comparator.comparingDouble(pos -> pos.y);
        if (reverseX) byX = byX.reversed();
        if (reverseY) byY = byY.reversed();
        ordered.sort(byX.thenComparing(byY));

        int size = Math.min(ordered.size(), Math.max(0, limit));
        return new ArrayList<>(ordered.subList(0, size));
    }
}
