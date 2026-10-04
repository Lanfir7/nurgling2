package nurgling.actions.bots.road;

import haven.Coord2d;

/** What the planner needs to know about the ground. Implementations may talk to the live map. */
public interface RoadTerrain {
    /** Tile height data is available here. */
    boolean loaded(Coord2d p);

    /** The milestone footprint is flat and does not overlap another object. */
    boolean siteOk(Coord2d pos, double angle);

    /** The road line from the previous anchor to this stone is free of ridges, water and objects. */
    boolean lineClear(Coord2d from, Coord2d to);

    /**
     * The route point two tiles in front of the stone. An obstacle on that tile blocks the step;
     * trees between the stones do not.
     */
    default boolean routePointClear(Coord2d point) {
        return point != null && lineClear(point, point);
    }

    /** A cliff edge between the two points. The road can never cross it. */
    default boolean cliffBetween(Coord2d from, Coord2d to) {
        return false;
    }

    /**
     * After placing, the game walks the character straight from the old stone to the new one.
     * Anything on that line stops the walk and the stone is never laid.
     */
    default boolean walkClear(Coord2d from, Coord2d to) {
        return true;
    }

    /** Five free tiles to every milestone already standing. */
    default boolean clearOfMilestones(Coord2d pos) {
        return true;
    }
}
