package nurgling.actions.bots.road;

import haven.Coord;
import haven.Coord2d;
import haven.Drawable;
import haven.Gob;
import haven.Line2d;
import haven.Loading;
import haven.MCache;
import haven.OCache;
import nurgling.NGameUI;
import nurgling.NHitBox;
import nurgling.navigation.ChunkNavRecorder;
import nurgling.tools.MilestoneSiteCheck;

/** Live-map checks for {@link RoadPlanner}. A tile that is still loading counts as unusable. */
public final class LiveRoadTerrain implements RoadTerrain {
    private final NGameUI gui;
    private final NHitBox hitBox;

    public LiveRoadTerrain(NGameUI gui, NHitBox hitBox) {
        this.gui = gui;
        this.hitBox = hitBox;
    }

    @Override
    public boolean loaded(Coord2d p) {
        MCache map = map();
        if (map == null || p == null) return false;
        try {
            return map.tilesetname(map.gettile(p.floor(MCache.tilesz))) != null;
        } catch (Loading l) {
            return false;
        }
    }

    @Override
    public boolean siteOk(Coord2d pos, double angle) {
        if (map() == null || hitBox == null || gui.map == null || gui.map.glob == null) return false;
        // Forest floor is rarely flat to half a unit. Overlap with a tree still rejects the tile;
        // a red road line is left for the game to reject when the stone is actually placed.
        try {
            String name = map().tilesetname(map().gettile(pos.floor(MCache.tilesz)));
            if (rock(name) || wetTile(map(), pos.floor(MCache.tilesz)))
                return false;
        } catch (Loading l) {
            return false;
        }
        final Gob player = gui.map.player();
        return !MilestoneSiteCheck.collides(gui.map.glob, hitBox, pos, angle, gob -> gob == player);
    }

    @Override
    public boolean cliffBetween(Coord2d from, Coord2d to) {
        MCache map = map();
        return map != null && crosses(map, from, to, true);
    }

    /**
     * A broken ridge is a cliff on the surface. In a mine every floor tile beside a wall has one,
     * so there only the rock itself blocks.
     */
    private static boolean crosses(MCache map, Coord2d from, Coord2d to, boolean rockBlocks) {
        if (from == null || to == null) return false;
        if (from.dist(to) < 0.01)
            return badTile(map, from.floor(MCache.tilesz), rockBlocks);
        if (crossesLine(map, from, to, rockBlocks))
            return true;
        // A line through the very corner of a wall slips between two rock tiles; widen it a little,
        // but not at the ends: stones stand right against the wall.
        double tile = MCache.tilesz.x;
        if (from.dist(to) <= 2 * tile)
            return false;
        Coord2d d = to.sub(from).norm();
        Coord2d a = from.add(d.mul(tile)), b = to.sub(d.mul(tile));
        Coord2d side = Coord2d.of(-d.y, d.x).mul(tile * 0.45);
        return crossesLine(map, a.add(side), b.add(side), rockBlocks) ||
                crossesLine(map, a.sub(side), b.sub(side), rockBlocks);
    }

    private static boolean crossesLine(MCache map, Coord2d from, Coord2d to, boolean rockBlocks) {
        Coord2d prev = null;
        for (Coord2d p : new Line2d.GridIsect(from, to, MCache.tilesz, true)) {
            if (prev != null && badTile(map, prev.add(p).div(2).floor(MCache.tilesz), rockBlocks))
                return true;
            prev = p;
        }
        return false;
    }

    private static boolean badTile(MCache map, Coord tile, boolean rockBlocks) {
        try {
            String name = map.tilesetname(map.gettile(tile));
            if (rock(name)) return rockBlocks;
            if (name.startsWith("gfx/tiles/mine")) return false;
            return haven.resutil.Ridges.brokenp(map, tile);
        } catch (Loading l) {
            return false;
        }
    }

    private static boolean rock(String name) {
        return ChunkNavRecorder.isBlockedTileName(name);
    }

    public boolean strictWalk;

    /** Nothing at all between the points: no rock, cliff, water or object. */
    public boolean straightWalkClear(Coord2d from, Coord2d to) {
        boolean was = strictWalk;
        strictWalk = true;
        try {
            return walkClear(from, to);
        } finally {
            strictWalk = was;
        }
    }

    @Override
    public boolean walkClear(Coord2d from, Coord2d to) {
        MCache map = map();
        if (map == null || gui.map == null || gui.map.glob == null || from == null || to == null)
            return false;
        // Mine walls are walked around before placing, unless that turned out to drop the hologram.
        if (crosses(map, from, to, strictWalk) || wet(map, from, to)) {
            RoadPlanner.log("   walk: tiles strict=" + strictWalk);
            return false;
        }
        final Gob player = gui.map.player();
        Coord2d dir = to.sub(from);
        double len = dir.abs();
        if (len < 1)
            return true;
        // The character stops a little short of the site, and starts beside the old stone, not inside
        // the rubble heaped around it.
        Coord2d start = from.add(dir.mul(Math.min(2 * MCache.tilesz.x, len / 2) / len));
        Coord2d end = from.add(dir.mul(Math.max(0, len - MCache.tilesz.x) / len));
        final String[] hit = {null};
        boolean blocked = MilestoneSiteCheck.segmentHitsGob(gui.map.glob, start, end, 2.5,
                gob -> {
                    boolean skip = gob == player || (gob.ngob != null && RoadRules.isMilestone(gob.ngob.name));
                    if (!skip && gob.ngob != null) hit[0] = gob.ngob.name + "@" + gob.rc;
                    return skip;
                });
        if (blocked)
            RoadPlanner.log("   walk: gob " + hit[0]);
        return !blocked;
    }

    @Override
    public boolean clearOfMilestones(Coord2d pos) {
        if (pos == null || gui.map == null || gui.map.glob == null || gui.map.glob.oc == null)
            return false;
        OCache oc = gui.map.glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob == null || gob.rc == null) continue;
                if (gob instanceof OCache.Virtual || gob instanceof haven.MapView.Plob) continue;
                if (!RoadRules.isMilestone(milestoneName(gob))) continue;
                if (RoadRules.tooClose(gob.rc.dist(pos), MCache.tilesz.x)) return false;
            }
        }
        return true;
    }

    private static String milestoneName(Gob gob) {
        if (gob.ngob != null && gob.ngob.name != null) return gob.ngob.name;
        try {
            Drawable drawable = gob.getattr(Drawable.class);
            if (drawable != null && drawable.getres() != null)
                return drawable.getres().name;
        } catch (Loading ignored) {
        }
        return null;
    }

    @Override
    public boolean routePointClear(Coord2d point) {
        MCache map = map();
        if (map == null || point == null || gui.map == null || gui.map.glob == null) return false;
        if (wetTile(map, point.floor(MCache.tilesz)) || badTile(map, point.floor(MCache.tilesz), true))
            return false;
        final Gob player = gui.map.player();
        // The path starts on this tile. A trunk on it blocks the step; a tree beside the line does not.
        return !MilestoneSiteCheck.pointHitsGob(gui.map.glob, point,
                gob -> gob == player || (gob.ngob != null && RoadRules.isMilestone(gob.ngob.name)));
    }

    @Override
    public boolean lineClear(Coord2d from, Coord2d to) {
        MCache map = map();
        if (map == null || gui.map == null || gui.map.glob == null) return false;
        if (wet(map, from, to))
            return false;
        final Gob player = gui.map.player();
        // A narrow line: a trunk must stand on the path. A tree one tile to the side does not block the whole corridor.
        return !MilestoneSiteCheck.segmentHitsGob(gui.map.glob, from, to, 1.0,
                gob -> gob == player || (gob.ngob != null && RoadRules.isMilestone(gob.ngob.name)));
    }

    private MCache map() {
        if (gui == null || gui.map == null || gui.map.glob == null) return null;
        return gui.map.glob.map;
    }

    private static boolean wet(MCache map, Coord2d from, Coord2d to) {
        if (from.dist(to) < 0.01)
            return wetTile(map, from.floor(MCache.tilesz));
        Coord2d prev = null;
        for (Coord2d p : new Line2d.GridIsect(from, to, MCache.tilesz, true)) {
            if (prev != null) {
                Coord tile = prev.add(p).div(2).floor(MCache.tilesz);
                if (wetTile(map, tile)) return true;
            }
            prev = p;
        }
        return false;
    }

    private static boolean wetTile(MCache map, Coord tile) {
        try {
            String name = map.tilesetname(map.gettile(tile));
            if (name == null) return false;
            return name.startsWith("gfx/tiles/water") || name.startsWith("gfx/tiles/deep");
        } catch (Loading l) {
            return false;
        }
    }
}
