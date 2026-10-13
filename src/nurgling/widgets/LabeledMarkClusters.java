package nurgling.widgets;

import haven.Coord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Badges for the visible marks of one map segment. */
final class LabeledMarkClusters {
    static final class Cluster {
        final String type;
        final Coord anchor;
        final List<LabeledMinimapMark> marks = new ArrayList<>();

        Cluster(LabeledMinimapMark best) {
            type = best.resourceType;
            anchor = best.tileCoords;
            marks.add(best);
        }

        LabeledMinimapMark best() { return marks.get(0); }
        LabeledMinimapMark worst() { return marks.get(marks.size() - 1); }

        Coord screenPos(Coord viewTc, double scale, Coord halfSize) {
            return new Coord((int)Math.round((anchor.x - viewTc.x) / scale) + halfSize.x,
                             (int)Math.round((anchor.y - viewTc.y) / scale) + halfSize.y);
        }
    }

    private static final Comparator<LabeledMinimapMark> BEST_FIRST =
        Comparator.comparingDouble((LabeledMinimapMark mark) -> mark.quality).reversed()
                  .thenComparing(LabeledMinimapMark::getLocationId);

    private final List<LabeledMinimapMark> source;
    private final double scale;
    private final boolean grouping;
    final List<Cluster> clusters;

    LabeledMarkClusters(List<LabeledMinimapMark> visible, double scale, int mergePx,
                        boolean grouping, Predicate<LabeledMinimapMark> mined) {
        this.source = new ArrayList<>(visible);
        this.scale = scale;
        this.grouping = grouping;
        List<LabeledMinimapMark> grouped = new ArrayList<>();
        List<Cluster> built = new ArrayList<>();
        for(LabeledMinimapMark mark : visible) {
            if(grouping && mined.test(mark))
                grouped.add(mark);
            else
                built.add(new Cluster(mark));
        }
        grouped.sort(BEST_FIRST);
        built.addAll(group(grouped, Math.max(1.0, mergePx * scale)));
        built.sort(Comparator.comparingDouble((Cluster cluster) -> cluster.best().quality));
        clusters = Collections.unmodifiableList(built);
    }

    boolean matches(List<LabeledMinimapMark> visible, double scale, boolean grouping) {
        return this.scale == scale && this.grouping == grouping && source.equals(visible);
    }

    private static List<Cluster> group(List<LabeledMinimapMark> bestFirst, double radius) {
        List<Cluster> out = new ArrayList<>();
        Map<String, Map<Long, List<Cluster>>> grids = new HashMap<>();
        double radiusSquared = radius * radius;
        for(LabeledMinimapMark mark : bestFirst) {
            Map<Long, List<Cluster>> grid = grids.computeIfAbsent(mark.resourceType, key -> new HashMap<>());
            int cx = (int)Math.floor(mark.tileCoords.x / radius);
            int cy = (int)Math.floor(mark.tileCoords.y / radius);
            Cluster nearest = null;
            double nearestDistance = radiusSquared;
            for(int ox = -1; ox <= 1; ox++) {
                for(int oy = -1; oy <= 1; oy++) {
                    List<Cluster> cell = grid.get(cellKey(cx + ox, cy + oy));
                    if(cell == null)
                        continue;
                    for(Cluster candidate : cell) {
                        double dx = candidate.anchor.x - mark.tileCoords.x;
                        double dy = candidate.anchor.y - mark.tileCoords.y;
                        double distance = dx * dx + dy * dy;
                        if(distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = candidate;
                        }
                    }
                }
            }
            if(nearest != null) {
                nearest.marks.add(mark);
            } else {
                Cluster cluster = new Cluster(mark);
                grid.computeIfAbsent(cellKey(cx, cy), key -> new ArrayList<>()).add(cluster);
                out.add(cluster);
            }
        }
        return out;
    }

    private static long cellKey(int x, int y) {
        return ((long)x << 32) ^ (y & 0xffffffffL);
    }
}
