package nurgling.tools;

import haven.*;
import haven.res.ui.gobcp.Gobcopy;
import nurgling.NHitBox;
import nurgling.GhostAlpha;
import nurgling.pf.NHitBoxD;

/**
 * Object-to-object placement snapping for Ctrl-held placement (lifted objects
 * and building previews).
 *
 * <p>While the placement ghost ({@link haven.MapView.Plob}) is being moved with
 * Ctrl held, this computes a position that snaps the ghost's footprint edge
 * flush against the edges of nearby objects, enabling tight packing. X and Y
 * are decided independently (CAD-style), so the ghost can snap to one object on
 * one axis while staying free (or snapping to another object) on the other.
 *
 * <p>Snapping only engages when a neighbour's edge is within {@link #CAPTURE} of
 * the ghost's edge; outside that range the ghost stays free. If the ghost's own
 * footprint can't be resolved, {@code snap()} returns {@code null} and the
 * caller falls back to free placement.
 *
 * <p>v1 treats all footprints as axis-aligned circumscribed boxes; neighbours
 * rotated off a 90° multiple get a looser (circumscribed) bound. Precise
 * edge-projection against rotated neighbours is a future refinement.
 */
public class PlacementSnap {
    /** Capture distance: how close an edge must be before it grabs. One tile. */
    public static final double CAPTURE = MCache.tilesz.x; // 11.0

    /** Cheap pre-filter radius for neighbour enumeration (covers large buildings). */
    private static final double SEARCH_RADIUS = 150.0;

    private PlacementSnap() {}

    /**
     * Compute the snapped world position for the placement ghost at cursor
     * world-coordinate {@code mc}, given the ghost's current rotation
     * ({@code plob.a}).
     *
     * @return snapped {@link Coord2d}, or {@code null} if the ghost footprint
     *         could not be resolved (caller should fall back to free placement).
     */
    public static Coord2d snap(MapView.Plob plob, Coord2d mc) {
        NHitBox pbox = resolvePlobHitBox(plob);
        if(pbox == null)
            return(null);

        // Ghost footprint as an axis-aligned box at the current rotation.
        // Edges are measured from the cursor: an asymmetric obstacle (herbalist
        // table) is not centred, and half-extents would leave a gap.
        NHitBoxD pd = pbox.toWorld(mc, plob.a);
        Coord2d pul = pd.getCircumscribedUL();
        Coord2d pbr = pd.getCircumscribedBR();
        double left = pul.x - mc.x;
        double right = pbr.x - mc.x;
        double top = pul.y - mc.y;
        double bottom = pbr.y - mc.y;

        MapView mv = plob.mv();
        Gob player = mv.player();
        long plid = (player != null) ? player.id : -1;

        // Best (closest) snap found per axis, only within CAPTURE.
        double bestDX = CAPTURE, bestDY = CAPTURE;
        Double snapX = null, snapY = null;

        synchronized(mv.glob.oc) {
            for(Gob gob : mv.glob.oc) {
                if(gob instanceof OCache.Virtual || gob.attr.isEmpty())
                    continue;
                if(gob.getClass().getName().contains("GlobEffector"))
                    continue;
                if(gob.getattr(GhostAlpha.class) != null)   // other placement ghosts
                    continue;
                if(gob.getattr(Following.class) != null)     // carried / lifted things
                    continue;
                if(gob.id == plid)
                    continue;

                NHitBox nb = gob.ngob.hitBox;
                if(nb == null && gob.ngob.name != null)
                    nb = NHitBox.findCustom(gob.ngob.name);
                if(nb == null)
                    continue;
                if(gob.rc.dist(mc) > SEARCH_RADIUS)
                    continue;

                NHitBoxD nd = nb.toWorld(gob.rc, gob.a);
                Coord2d nul = nd.getCircumscribedUL();
                Coord2d nbr = nd.getCircumscribedBR();

                // X snapping only makes sense when the ghost overlaps the
                // neighbour's Y span (i.e. they are side-by-side), within CAPTURE.
                boolean yAdjacent = (mc.y + bottom > nul.y - CAPTURE) && (mc.y + top < nbr.y + CAPTURE);
                if(yAdjacent) {
                    double[] r = best(mc.x, flushCandidates(left, right, nul.x, nbr.x), bestDX);
                    if(r != null) { bestDX = r[0]; snapX = r[1]; }
                }

                boolean xAdjacent = (mc.x + right > nul.x - CAPTURE) && (mc.x + left < nbr.x + CAPTURE);
                if(xAdjacent) {
                    double[] r = best(mc.y, flushCandidates(top, bottom, nul.y, nbr.y), bestDY);
                    if(r != null) { bestDY = r[0]; snapY = r[1]; }
                }
            }
        }

        if(snapX == null && snapY == null)
            return(null); // nothing within capture → caller places freely
        return(Coord2d.of(snapX != null ? snapX : mc.x,
                          snapY != null ? snapY : mc.y));
    }

    /**
     * Cursor positions that put this footprint flush with a neighbour.
     * {@code near}/{@code far} are the ghost edges relative to the cursor.
     */
    static double[] flushCandidates(double near, double far, double neighbourNear, double neighbourFar) {
        return new double[] {
            neighbourNear - far,
            neighbourFar - near,
            neighbourNear - near,
            neighbourFar - far
        };
    }

    /**
     * From candidate coordinates, return {currentBest distance, winning
     * coordinate} if any beats {@code curBest} (the closest-so-far distance from
     * {@code from}); otherwise {@code null}.
     */
    private static double[] best(double from, double[] candidates, double curBest) {
        double bd = curBest;
        double bc = Double.NaN;
        for(double c : candidates) {
            double d = Math.abs(c - from);
            if(d < bd) { bd = d; bc = c; }
        }
        return(Double.isNaN(bc) ? null : new double[]{bd, bc});
    }

    /**
     * Resolve the placement ghost's own footprint. Tries, in order: the ghost's
     * computed ngob hitbox; the copied source gob's hitbox (lifted objects use a
     * {@link Gobcopy} sprite); a custom hitbox by resource name; then the
     * resource's Neg / Obstacle collision layers. Returns {@code null} if none
     * resolve.
     */
    private static NHitBox resolvePlobHitBox(MapView.Plob plob) {
        if(plob.ngob != null && plob.ngob.hitBox != null)
            return(plob.ngob.hitBox);

        ResDrawable rd = plob.getattr(ResDrawable.class);
        if(rd == null)
            return(null);

        // Lifted object: the preview wraps a copy of the real gob.
        if(rd.spr instanceof Gobcopy) {
            Gob tg = ((Gobcopy) rd.spr).gob;
            if(tg != null && tg.ngob != null && tg.ngob.hitBox != null)
                return(tg.ngob.hitBox);
        }

        Resource res = rd.getres();
        if(res == null)
            return(null);

        NHitBox custom = NHitBox.findCustom(res.name);
        if(custom != null)
            return(custom);

        for(Resource.Layer lay : res.getLayers()) {
            if(lay instanceof Resource.Neg) {
                Resource.Neg neg = (Resource.Neg) lay;
                return(new NHitBox(neg.ac, neg.bc));
            }
        }
        for(Resource.Layer lay : res.getLayers()) {
            if(lay instanceof Resource.Obstacle)
                return(NHitBox.fromObstacle(((Resource.Obstacle) lay).p));
        }
        return(null);
    }
}
