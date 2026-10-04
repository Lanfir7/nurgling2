package nurgling.actions.bots.road;

import haven.Coord;
import haven.Coord2d;

import java.util.List;
import java.util.Set;

/**
 * Picks the next milestone along an approximate route.
 * Tries the longest step first, then a sideways shift inside the corridor, then a shorter step.
 */
public final class RoadPlanner {
    public static final class Candidate {
        public final Coord2d pos;
        public final double angle;
        public final int step;

        public Candidate(Coord2d pos, double angle, int step) {
            this.pos = pos;
            this.angle = angle;
            this.step = step;
        }
    }

    private final List<Coord2d> route;
    private final double tile;
    private final RoadTerrain terrain;
    private final int maxSegment;
    private int maxStep;

    public RoadPlanner(List<Coord2d> route, double tileSize, RoadTerrain terrain) {
        this(route, tileSize, terrain, RoadRules.MAX_SEGMENT_TILES);
    }

    public RoadPlanner(List<Coord2d> route, double tileSize, RoadTerrain terrain, int maxSegment) {
        this.route = route;
        this.tile = tileSize;
        this.terrain = terrain;
        this.maxSegment = maxSegment;
        this.maxStep = maxSegment;
    }

    /** After the server rejects a spot, don't try that far again until the next stone is placed. */
    public void limitStep(int tiles) {
        maxStep = Math.max(RoadRules.MIN_SEGMENT_TILES, Math.min(maxStep, tiles));
    }

    public void resetReach() {
        maxStep = maxSegment;
    }

    public boolean finished(Coord2d lastRc) {
        if (route == null || route.isEmpty() || lastRc == null) return true;
        return lastRc.dist(route.get(route.size() - 1)) <= RoadRules.FINISH_RADIUS_TILES * tile;
    }

    public Candidate next(Coord2d lastRc, double lastAngle, Set<Coord> rejectedTiles) {
        if (route == null || route.size() < 2 || lastRc == null || terrain == null || tile <= 0)
            return null;
        double length = RoadGeometry.polylineLength(route);
        double s0 = RoadGeometry.project(route, lastRc);
        notLoaded = occupied = nearStone = blockedStart = blockedLine = blockedWalk = rejected = 0;
        fallback = null;
        Candidate clear = null;
        // Any spot that moves the road toward the goal will do: nearer, farther, left or right of the route.
        // The longest step whose straight road stays near the route wins; a bend only shortens it when cut too far.
        for (int step = maxStep; step >= RoadRules.MIN_SEGMENT_TILES && clear == null; step--) {
            double s = Math.min(s0 + step * tile, length);
            Coord2d p = RoadGeometry.pointAt(route, s);
            if (cutsTooFar(lastRc, s0, s, p))
                continue;
            Coord2d tangent = RoadGeometry.tangentAt(route, s);
            Coord2d normal = Coord2d.of(-tangent.y, tangent.x);
            for (int lat : LATERALS) {
                Candidate c = tryAt(p, normal, lat, null, step, lastRc, lastAngle, s0, rejectedTiles);
                if (c != null) {
                    clear = c;
                    break;
                }
            }
            // At a bend in a mine only a road that starts right on the corner gets around it.
            if (clear == null) {
                double reach = RoadGeometry.anchor(Coord2d.z, 0, tile).abs();
                Coord2d centre = snap(p);
                for (int dx = -4; dx <= 4 && clear == null; dx++) {
                    for (int dy = -4; dy <= 4 && clear == null; dy++) {
                        Coord2d at = centre.add(dx * tile, dy * tile);
                        if (Math.abs(at.dist(centre) - reach) > tile / 2)
                            continue;
                        double facing = RoadGeometry.angleOf(centre.sub(at));
                        clear = tryAt(at, normal, 0, facing, step, lastRc, lastAngle, s0, rejectedTiles);
                    }
                }
            }
        }
        // A stump or bush on the line may still be fine for the game; a much longer step is worth the try.
        if (fallback != null && (clear == null || fallback.step > clear.step + 4))
            return fallback;
        return clear;
    }

    private Candidate fallback;

    private boolean cutsTooFar(Coord2d lastRc, double s0, double s, Coord2d p) {
        double limit = RoadRules.CUT_CORNER_TILES * tile;
        for (double t = s0 + tile; t < s; t += tile) {
            if (distToSegment(RoadGeometry.pointAt(route, t), lastRc, p) > limit)
                return true;
        }
        return false;
    }

    /** A stone standing on the route past its own road start sits in the way of the next road. */
    private boolean blocksRouteAhead(Coord2d pos, double from) {
        double length = RoadGeometry.polylineLength(route);
        double until = Math.min(from + maxSegment * tile, length);
        for (double t = from + tile; t <= until; t += tile / 2) {
            if (RoadGeometry.pointAt(route, t).dist(pos) < 1.5 * tile)
                return true;
        }
        return false;
    }

    private static double distToSegment(Coord2d q, Coord2d a, Coord2d b) {
        Coord2d ab = b.sub(a);
        double len2 = ab.x * ab.x + ab.y * ab.y;
        if (len2 < 1e-9) return q.dist(a);
        double t = Math.max(0, Math.min(1, q.sub(a).dot(ab) / len2));
        return q.dist(a.add(ab.mul(t)));
    }

    private static final int[] LATERALS = {3, -3, 2, -2, 4, -4, 1, -1, 5, -5, 0, 6, -6, 7, -7, 8, -8};

    private Candidate tryAt(Coord2d p, Coord2d normal, int lat, Double facing, int step, Coord2d lastRc,
                            double lastAngle, double s0, Set<Coord> rejectedTiles) {
        Coord2d pos = snap(p.add(normal.mul(lat * tile)));
        Coord2d toward = p.sub(pos);
        double angle = facing != null ? facing
                : toward.abs() < tile / 2 ? RoadGeometry.angleOf(normal) : RoadGeometry.angleOf(toward);
        if (rejectedTiles != null && rejectedTiles.contains(tileOf(pos))) {
            rejected++;
            return dbg("rejectedTile", pos, angle, step, lat);
        }
        if (pos.dist(lastRc) > (maxSegment + RoadRules.ROAD_OFFSET_TILES + 1) * tile)
            return dbg("tooFar", pos, angle, step, lat);
        if (RoadGeometry.project(route, pos) < s0 + (RoadRules.MIN_SEGMENT_TILES - 1) * tile)
            return dbg("noProgress", pos, angle, step, lat);
        Coord2d routePoint = RoadGeometry.anchor(pos, angle, tile);
        if (!terrain.loaded(pos) || !terrain.loaded(routePoint)) {
            notLoaded++;
            return dbg("notLoaded", pos, angle, step, lat);
        }
        if (!terrain.siteOk(pos, angle)) {
            occupied++;
            return dbg("siteOccupied", pos, angle, step, lat);
        }
        if (!terrain.clearOfMilestones(pos)) {
            nearStone++;
            return dbg("nearStone", pos, angle, step, lat);
        }
        if (!terrain.routePointClear(routePoint)) {
            blockedStart++;
            return dbg("routePointBlocked", pos, angle, step, lat);
        }
        Coord2d prevRoad = RoadGeometry.anchor(lastRc, lastAngle, tile);
        if (distToSegment(pos, prevRoad, routePoint) < 1.5 * tile) {
            blockedLine++;
            return dbg("stoneOnRoad", pos, angle, step, lat);
        }
        // The next road leaves from the face; with the route behind the stone it would run through it.
        double along = RoadGeometry.project(route, routePoint);
        Coord2d ahead = RoadGeometry.tangentAt(route, Math.min(along + tile, RoadGeometry.polylineLength(route)));
        if (Math.cos(angle) * ahead.x + Math.sin(angle) * ahead.y < -0.3) {
            blockedLine++;
            return dbg("facesBack", pos, angle, step, lat);
        }
        if (blocksRouteAhead(pos, along)) {
            blockedLine++;
            return dbg("blocksNextRoad", pos, angle, step, lat);
        }
        if (terrain.cliffBetween(prevRoad, routePoint) || terrain.cliffBetween(pos, routePoint)) {
            blockedLine++;
            return dbg("cliffOrRock prevRoad=" + prevRoad, pos, angle, step, lat);
        }
        // The character gets there by the path finder; only the last straight stretch is the game's walk.
        Coord2d stage = pos.add(routePoint.sub(pos).norm().mul(2 * tile));
        if (!terrain.walkClear(stage, pos)) {
            blockedWalk++;
            return dbg("walkBlocked", pos, angle, step, lat);
        }
        // The game refuses a stone whose road from the previous one runs into a tree or a wall.
        if (!terrain.lineClear(prevRoad, routePoint)) {
            blockedLine++;
            if (fallback == null)
                fallback = new Candidate(pos, angle, step);
            return dbg("lineHitsGob", pos, angle, step, lat);
        }
        dbg("OK", pos, angle, step, lat);
        return new Candidate(pos, angle, step);
    }

    // TEMP road debug
    private static Candidate dbg(String why, Coord2d pos, double angle, int step, int lat) {
        log("step=" + step + " lat=" + lat + " pos=" + pos
                + " deg=" + Math.round(Math.toDegrees(angle)) + " " + why);
        return null;
    }

    public static void log(String line) {
        try (java.io.FileWriter w = new java.io.FileWriter("road-debug.log", true)) {
            w.write(line + System.lineSeparator());
        } catch (java.io.IOException ignored) {
        }
    }

    public int notLoaded, occupied, nearStone, blockedStart, blockedLine, blockedWalk, rejected;

    Coord tileOf(Coord2d p) {
        return Coord.of((int) Math.floor(p.x / tile), (int) Math.floor(p.y / tile));
    }

    private Coord2d snap(Coord2d p) {
        Coord tc = tileOf(p);
        return Coord2d.of(tc.x * tile + tile / 2.0, tc.y * tile + tile / 2.0);
    }

}
