package nurgling.widgets.compass;

import haven.Coord;

import java.util.ArrayList;
import java.util.List;

final class NCompassHits {
    private static final class Hit {
        final Coord center;
        final int radius;
        final NCompassTarget target;

        Hit(Coord center, int radius, NCompassTarget target) {
            this.center = center;
            this.radius = radius;
            this.target = target;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    void clear() {
        hits.clear();
    }

    void add(Coord center, int radius, NCompassTarget target) {
        if (center != null && target != null)
            hits.add(new Hit(center, Math.max(0, radius), target));
    }

    NCompassTarget find(Coord point) {
        if (point == null)
            return null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.center.dist(point) <= hit.radius)
                return hit.target;
        }
        return null;
    }
}
