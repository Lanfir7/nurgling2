package nurgling.tools;

import haven.Coord2d;
import haven.Following;
import haven.Glob;
import haven.Gob;
import haven.Loading;
import haven.MCache;
import haven.OCache;
import nurgling.GhostAlpha;
import nurgling.NHitBox;
import nurgling.pf.NHitBoxD;

import java.util.function.Predicate;

/** Flatness and collision checks shared by the placement hint and the road planner. */
public final class MilestoneSiteCheck {
    private MilestoneSiteCheck() {}

    public static boolean flat(MCache map, NHitBox hitBox, Coord2d position, double angle, double tolerance) {
        if (map == null || hitBox == null || position == null) return false;
        Coord2d[] corners = {
                hitBox.begin,
                Coord2d.of(hitBox.begin.x, hitBox.end.y),
                Coord2d.of(hitBox.end.x, hitBox.begin.y),
                hitBox.end,
                Coord2d.of((hitBox.begin.x + hitBox.end.x) / 2.0, (hitBox.begin.y + hitBox.end.y) / 2.0)
        };
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        for (Coord2d corner : corners) {
            Coord2d sample = position.add((corner.x * cos) - (corner.y * sin),
                    (corner.x * sin) + (corner.y * cos));
            double z = map.getcz(sample);
            min = Math.min(min, z);
            max = Math.max(max, z);
        }
        return (max - min) <= tolerance;
    }

    public static boolean collides(Glob glob, NHitBox hitBox, Coord2d position, double angle, Predicate<Gob> ignore) {
        if (glob == null || hitBox == null || position == null) return false;
        NHitBoxD candidate = new NHitBoxD(hitBox.begin, hitBox.end, position, angle);
        synchronized (glob.oc) {
            for (Gob gob : glob.oc) {
                if (skip(gob, ignore)) continue;
                if (overlaps(gob, candidate)) return true;
            }
        }
        return false;
    }

    /** True when some object's hitbox crosses the segment. {@code halfWidth} is in world units. */
    /** True when an object's footprint covers this world point. */
    public static boolean pointHitsGob(Glob glob, Coord2d point, Predicate<Gob> ignore) {
        if (glob == null || point == null) return false;
        synchronized (glob.oc) {
            for (Gob gob : glob.oc) {
                if (skip(gob, ignore)) continue;
                NHitBox obstacle = gob.ngob.hitBox;
                if (obstacle == null && gob.ngob.name != null)
                    obstacle = NHitBox.findCustom(gob.ngob.name);
                if (obstacle != null && new NHitBoxD(obstacle.begin, obstacle.end, gob.rc, gob.a).contains(point, true))
                    return true;
            }
        }
        return false;
    }

    public static boolean segmentHitsGob(Glob glob, Coord2d from, Coord2d to, double halfWidth, Predicate<Gob> ignore) {
        if (glob == null || from == null || to == null) return false;
        if (from.dist(to) < 0.01) return false;
        NHitBoxD segment = NHitBoxD.shaftBoxObjectFactory(from, to, halfWidth);
        synchronized (glob.oc) {
            for (Gob gob : glob.oc) {
                if (skip(gob, ignore)) continue;
                if (overlaps(gob, segment)) return true;
            }
        }
        return false;
    }

    private static boolean skip(Gob gob, Predicate<Gob> ignore) {
        if (gob == null) return true;
        if (ignore != null && ignore.test(gob)) return true;
        if (gob instanceof OCache.Virtual) return true;
        if (gob.getattr(GhostAlpha.class) != null) return true;
        if (gob.getattr(Following.class) != null) return true;
        return gob.attr == null || gob.attr.isEmpty() || gob.ngob == null;
    }

    private static boolean overlaps(Gob gob, NHitBoxD candidate) {
        NHitBox obstacle = gob.ngob.hitBox;
        if (obstacle == null && gob.ngob.name != null)
            obstacle = NHitBox.findCustom(gob.ngob.name);
        return obstacle != null &&
                new NHitBoxD(obstacle.begin, obstacle.end, gob.rc, gob.a).intersects(candidate, false);
    }

    /** Swallow a map that is still loading. Callers that must match the old hint keep the throw. */
    public static boolean flatOrUnloaded(MCache map, NHitBox hitBox, Coord2d position, double angle, double tolerance) {
        try {
            return flat(map, hitBox, position, angle, tolerance);
        } catch (Loading l) {
            return false;
        }
    }
}
