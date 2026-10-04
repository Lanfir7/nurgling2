package nurgling.actions.bots.road;

import haven.Coord2d;

import java.util.List;

/** Polyline math for an approximate road route. No game session types. */
public final class RoadGeometry {
    private RoadGeometry() {}

    /** Tile where the road line actually starts: two tiles in front of the stone are skipped. */
    public static Coord2d anchor(Coord2d milestoneRc, double angle, double tileSize) {
        double dist = RoadRules.ANCHOR_SIGN * (RoadRules.ROAD_OFFSET_TILES + 1) * tileSize;
        return milestoneRc.add(Math.cos(angle) * dist, Math.sin(angle) * dist);
    }

    public static double polylineLength(List<Coord2d> pts) {
        double len = 0;
        if (pts == null) return 0;
        for (int i = 1; i < pts.size(); i++) {
            len += pts.get(i - 1).dist(pts.get(i));
        }
        return len;
    }

    /** Arc length from the start of the polyline to the closest point to {@code p}. */
    public static double project(List<Coord2d> pts, Coord2d p) {
        if (pts == null || pts.isEmpty() || p == null) return 0;
        if (pts.size() == 1) return 0;
        double best = Double.POSITIVE_INFINITY;
        double bestS = 0;
        double acc = 0;
        for (int i = 1; i < pts.size(); i++) {
            Coord2d a = pts.get(i - 1);
            Coord2d b = pts.get(i);
            double seg = a.dist(b);
            double t = 0;
            if (seg > 1e-6) {
                Coord2d ab = b.sub(a);
                t = clamp01(p.sub(a).dot(ab) / (seg * seg));
            }
            Coord2d q = a.add(b.sub(a).mul(t));
            double d = p.dist(q);
            if (d < best) {
                best = d;
                bestS = acc + seg * t;
            }
            acc += seg;
        }
        return bestS;
    }

    public static Coord2d pointAt(List<Coord2d> pts, double s) {
        if (pts == null || pts.isEmpty()) return null;
        if (pts.size() == 1 || s <= 0) return pts.get(0);
        double left = s;
        for (int i = 1; i < pts.size(); i++) {
            Coord2d a = pts.get(i - 1);
            Coord2d b = pts.get(i);
            double seg = a.dist(b);
            boolean last = i == pts.size() - 1;
            if (left <= seg || last) {
                if (seg < 1e-9) return b;
                double t = clamp01(left / seg);
                return a.add(b.sub(a).mul(t));
            }
            left -= seg;
        }
        return pts.get(pts.size() - 1);
    }

    /** Unit direction of the segment that contains arc length {@code s}. */
    public static Coord2d tangentAt(List<Coord2d> pts, double s) {
        if (pts == null || pts.size() < 2) return Coord2d.of(1, 0);
        double left = Math.max(0, s);
        for (int i = 1; i < pts.size(); i++) {
            Coord2d a = pts.get(i - 1);
            Coord2d b = pts.get(i);
            double seg = a.dist(b);
            boolean last = i == pts.size() - 1;
            if (seg < 1e-9) continue;
            if (left < seg || last) return b.sub(a).norm();
            left -= seg;
        }
        return Coord2d.of(1, 0);
    }

    public static double angleOf(Coord2d dir) {
        if (dir == null) return 0;
        return Math.atan2(dir.y, dir.x);
    }

    /**
     * Arc length of the next corner whose heading changes by more than {@code maxTurn}.
     * A straight route returns its full length.
     */
    public static double untilTurn(List<Coord2d> pts, double s0, double maxTurn) {
        double length = polylineLength(pts);
        if (pts == null || pts.size() < 3) return length;
        double acc = 0;
        for (int i = 1; i < pts.size() - 1; i++) {
            Coord2d incoming = pts.get(i).sub(pts.get(i - 1));
            Coord2d outgoing = pts.get(i + 1).sub(pts.get(i));
            acc += incoming.abs();
            if (acc <= s0 + 1e-6) continue;
            if (incoming.abs() < 1e-6 || outgoing.abs() < 1e-6) continue;
            if (turnAngle(incoming, outgoing) > maxTurn)
                return acc;
        }
        return length;
    }

    private static double turnAngle(Coord2d a, Coord2d b) {
        double d = Math.abs(angleOf(b) - angleOf(a));
        if (d > Math.PI) d = 2 * Math.PI - d;
        return d;
    }

    private static double clamp01(double t) {
        if (t < 0) return 0;
        if (t > 1) return 1;
        return t;
    }
}
