package nurgling.navigation;

import haven.Coord2d;
import haven.Gob;
import nurgling.NHitBox;
import nurgling.tasks.GateDetector;

/**
 * Fence and wall gates on the chunk-nav map.
 * Walkability 1 is a gate opening: the route may cross it, but the character
 * has to click the gate open and click it shut again after stepping clear.
 * Doors, stairs and cave mouths stay ordinary passages (walkability 0).
 */
public final class ChunkNavGates {
    public static final byte WALKABLE = 0;
    public static final byte GATE = 1;
    public static final byte BLOCKED = 2;

    /** Tiles to walk past the leaf before closing, when that still leaves the gate in click range. */
    public static final double PASS_CLEARANCE_TILES = 1.0;
    /** Furthest stand from the gate center that can still right-click it. */
    public static final double MAX_CLICK_TILES = 1.1;
    /** How far the straight step may reach and still open a gate before walking on. */
    public static final double APPROACH_TILES = 40.0;

    private ChunkNavGates() {}

    public static byte classify(boolean terrainBlocked, boolean gobBlocked, boolean gateCell) {
        if (terrainBlocked) return BLOCKED;
        // A neighboring wall's box often covers the opening. The gate still has to stay a passage.
        if (gateCell) return GATE;
        if (gobBlocked) return BLOCKED;
        return WALKABLE;
    }

    public static boolean isTraversable(byte walkability) {
        return walkability <= GATE;
    }

    public static final class Pose {
        public final Coord2d center;
        public final Coord2d through;
        public final Coord2d span;
        public final double halfThin;
        public final double halfSpan;

        Pose(Coord2d center, Coord2d through, Coord2d span, double halfThin, double halfSpan) {
            this.center = center;
            this.through = through;
            this.span = span;
            this.halfThin = halfThin;
            this.halfSpan = halfSpan;
        }
    }

    public static Pose fromGob(Gob gob) {
        if (!GateDetector.isGate(gob) || gob.ngob.hitBox == null) return null;
        NHitBox hitBox = gob.ngob.hitBox;
        return fromHitbox(gob.rc, gob.a, hitBox.begin.x, hitBox.begin.y, hitBox.end.x, hitBox.end.y);
    }

    public static Pose fromHitbox(Coord2d center, double angle, double beginX, double beginY, double endX, double endY) {
        double width = Math.abs(endX - beginX);
        double height = Math.abs(endY - beginY);
        boolean thinX = width <= height;
        Coord2d through = (thinX ? new Coord2d(1, 0) : new Coord2d(0, 1)).rot(angle);
        Coord2d span = new Coord2d(-through.y, through.x);
        double halfThin = (thinX ? width : height) / 2.0;
        double halfSpan = (thinX ? height : width) / 2.0;
        return new Pose(center, through, span, halfThin, halfSpan);
    }

    /** Point where the segment crosses the gate opening, or null. */
    public static Coord2d crossingPoint(Coord2d from, Coord2d to, Pose pose) {
        if (from == null || to == null || pose == null) return null;
        double sideFrom = dot(from.sub(pose.center), pose.through);
        double sideTo = dot(to.sub(pose.center), pose.through);
        if (sideFrom * sideTo >= 0) return null;
        double denom = sideFrom - sideTo;
        if (denom == 0) return null;
        double t = sideFrom / denom;
        if (t < 0 || t > 1) return null;
        Coord2d at = from.add(to.sub(from).mul(t));
        double along = Math.abs(dot(at.sub(pose.center), pose.span));
        if (along > pose.halfSpan + 1.0) return null;
        return at;
    }

    /** Stand just outside the leaf, on the same side as {@code from}. */
    public static Coord2d standOff(Pose pose, Coord2d from, double margin) {
        double side = Math.signum(dot(from.sub(pose.center), pose.through));
        if (side == 0) side = 1;
        return pose.center.add(pose.through.mul(side * (pose.halfThin + margin)));
    }

    /**
     * A point on the far side, about a tile past the leaf, still close enough to click the gate shut.
     */
    public static Coord2d exitPoint(Pose pose, Coord2d from, double tileSize) {
        double side = Math.signum(dot(from.sub(pose.center), pose.through));
        if (side == 0) side = 1;
        double desired = pose.halfThin + tileSize * PASS_CLEARANCE_TILES;
        double dist = Math.min(desired, tileSize * MAX_CLICK_TILES);
        if (dist < pose.halfThin) dist = pose.halfThin;
        return pose.center.add(pose.through.mul(-side * dist));
    }

    private static double dot(Coord2d a, Coord2d b) {
        return a.x * b.x + a.y * b.y;
    }
}
