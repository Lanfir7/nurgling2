package nurgling.actions.bots.road;

import haven.Button;
import haven.Coord;
import haven.Coord2d;
import haven.Drawable;
import haven.GItem;
import haven.Gob;
import haven.WItem;
import haven.Loading;
import haven.MCache;
import haven.OCache;
import haven.Widget;
import haven.Window;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NISBox;
import nurgling.NHitBox;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Build;
import nurgling.actions.CloseTargetWindow;
import nurgling.actions.Drink;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.actions.bots.BuildMaterialHelper;
import nurgling.actions.bots.Chipper;
import nurgling.areas.NContext;
import nurgling.areas.NGlobalCoord;
import nurgling.i18n.L10n;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerWaypoint;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitBuildState;
import nurgling.tasks.WaitPlob;
import nurgling.tools.Finder;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static haven.OCache.posres;

/**
 * Extends an existing milestone road along an approximate route.
 * The player places the first stone. Each further stone is "Extend" from the current end.
 */
public final class RoadBuilder {
    private final ForagerPath route;
    private final boolean stone;

    public RoadBuilder(ForagerPath route, boolean stone) {
        this.route = route;
        this.stone = stone;
    }

    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.map == null || gui.mmap == null || gui.mmap.sessloc == null)
            return Results.ERROR(L10n.get("road.err.segment"));
        if (route == null || route.waypoints == null || route.waypoints.size() < 2)
            return Results.ERROR(L10n.get("road.err.short"));

        List<Coord2d> pts = new ArrayList<Coord2d>();
        for (ForagerWaypoint wp : route.waypoints) {
            Coord2d p = wp.toWorldCoord(gui.mmap.sessloc);
            if (p == null)
                return Results.ERROR(L10n.get("road.err.segment"));
            pts.add(p);
        }

        Gob last = nearestEnd(gui, pts.get(0));
        if (last == null)
            return Results.ERROR(L10n.get("road.err.no_start"));
        NHitBox hitBox = hitBox(last);
        if (hitBox == null)
            return Results.ERROR(L10n.get("road.err.no_hitbox"));

        NContext context = new NContext(gui);
        BuildMaterialHelper helper = new BuildMaterialHelper(context, gui);
        Build.Ingredient ingredient = stone
                ? helper.getStone(RoadRules.MATERIAL_COUNT)
                : helper.getBlocks(RoadRules.MATERIAL_COUNT);
        // The zone is stored as "Stone", but the pocket holds Apatite, Basalt and the other rocks.
        if (stone)
            ingredient.name = Chipper.stones;
        Build.Command cmd = new Build.Command();
        cmd.name = RoadRules.WINDOW_NAME;
        cmd.windowName = RoadRules.WINDOW_NAME;
        cmd.ingredients.add(ingredient);
        Build build = new Build(context, cmd, null);

        LiveRoadTerrain terrain = new LiveRoadTerrain(gui, hitBox);
        RoadPlanner planner = new RoadPlanner(pts, MCache.tilesz.x, terrain, RoadRules.maxSegment(stone));
        Set<haven.Coord> rejected = new HashSet<haven.Coord>();
        int attempts = 0;
        int placed = 0;
        int guard = 0;
        int scouts = 0;
        int failedReuse = 0;
        boolean fresh;
        while (!planner.finished(last.rc)) {
            if (++guard > 400)
                return Results.ERROR(L10n.get("road.err.give_up"));

            int have = countMaterials(gui.getInventory().getItems(ingredient.name));
            int missing = RoadRules.MATERIAL_COUNT - have;
            if (missing > 0) {
                dropPreview(gui);
                long id = last.id;
                NGlobalCoord bookmark = new NGlobalCoord(last.rc);
                Build.Ingredient ask = new Build.Ingredient(ingredient.coord, ingredient.nArea, ingredient.name, missing);
                ArrayList<Build.Ingredient> one = new ArrayList<Build.Ingredient>();
                one.add(ask);
                if (!build.refill(gui, one)) {
                    String why = build.lastRefillFailure();
                    return Results.ERROR(why != null ? why : L10n.get("road.err.materials"));
                }
                if (!backToStone(gui, id, bookmark))
                    return Results.ERROR(L10n.get("road.err.return"));
                Gob again = Finder.findGob(id);
                if (again == null) {
                    Coord2d back = bookmark.getCurrentCoord();
                    again = nearestEnd(gui, back != null ? back : last.rc);
                }
                if (again == null)
                    return Results.ERROR(L10n.get("road.err.no_start"));
                last = again;
            }

            RoadPlanner.log("=== last stone " + last.rc + " deg=" + Math.round(Math.toDegrees(last.a))
                    + " strictWalk=" + terrain.strictWalk + " route=" + pts);
            RoadPlanner.Candidate cand = planner.next(last.rc, last.a, rejected);
            RoadPlanner.log("chosen " + (cand == null ? "none" : cand.pos + " step=" + cand.step));
            if (cand == null) {
                // The next tile is often past the loaded map while the player still stands at the last stone.
                dropPreview(gui);
                gui.msg(L10n.get("road.err.no_spot_why", planner.notLoaded, planner.occupied,
                        planner.nearStone, planner.blockedStart, planner.rejected, planner.blockedLine, planner.blockedWalk));
                if (scouts >= 5 || !walkAhead(pts, last.rc))
                    return Results.ERROR(L10n.get("road.err.no_spot_why", planner.notLoaded, planner.occupied,
                            planner.nearStone, planner.blockedStart, planner.rejected, planner.blockedLine, planner.blockedWalk));
                scouts++;
                continue;
            }
            // After a rejected spot the game usually keeps the hologram, so the next spot is sent right away.
            if (gui.map.placing == null || failedReuse > 0) {
                dropPreview(gui);
                failedReuse = 0;
                Results approach = approachNear(last);
                if (!approach.IsSuccess())
                    return approach;
                Gob live = Finder.findGob(last.id);
                if (live != null) last = live;

                if (!extend(gui, last)) {
                    if (++attempts > 12)
                        return Results.ERROR(L10n.get("road.err.extend"));
                    continue;
                }

                gui.ui.core.addTask(WaitPlob.withSoftTimeout(false, 40, gui));
                if (gui.map.placing == null) {
                    if (++attempts > 12)
                        return Results.ERROR(L10n.get("road.err.place"));
                    continue;
                }
                fresh = true;
            } else {
                fresh = false;
            }
            // The game walks the character straight to the site. Get close first so the walk is short and clear.
            Gob me = NUtils.player();
            if (me != null && !terrain.straightWalkClear(me.rc, cand.pos)) {
                Coord2d toward = RoadGeometry.anchor(cand.pos, cand.angle, MCache.tilesz.x).sub(cand.pos);
                Coord2d stage = cand.pos.add(toward.norm().mul(MCache.tilesz.x * 2));
                boolean reached = walkTo(stage, 1);
                if (!reached && gui.map.placing != null) {
                    // No way around the wall: placing now would send the character straight into it.
                    rejected.add(planner.tileOf(cand.pos));
                    if (++attempts > 12) {
                        dropPreview(gui);
                        return Results.ERROR(L10n.get("road.err.give_up"));
                    }
                    continue;
                }
                if (gui.map.placing == null) {
                    // Walking dropped the hologram: only spots in a straight line from the old stone work here.
                    terrain.strictWalk = true;
                    if (++attempts > 12)
                        return Results.ERROR(L10n.get("road.err.place"));
                    continue;
                }
            }
            final Coord2d want = cand.pos;
            gui.map.wdgmsg("place", want.floor(posres),
                    (int) Math.round(cand.angle * 32768 / Math.PI), 1, 0);
            NUtils.addTask(new NTask() {
                int still;
                int ticks;

                @Override
                public boolean check() {
                    if (findNear(want, MCache.tilesz.x) != null)
                        return true;
                    if (++ticks > 400)
                        return true;
                    Gob walker = NUtils.player();
                    if (walker != null && walker.getv() > 0.15) {
                        still = 0;
                        return false;
                    }
                    return ++still > 25;
                }
            });
            if (findNear(want, MCache.tilesz.x) == null) {
                if (!fresh)
                    failedReuse++;
                rejected.add(planner.tileOf(want));
                // Each rejection tries the next spot beside it; every third one also shortens the step.
                if (++attempts % 3 == 0)
                    planner.limitStep(cand.step - 2);
                if (attempts > 12) {
                    dropPreview(gui);
                    return Results.ERROR(L10n.get("road.err.give_up"));
                }
                continue;
            }

            Results built = finishPlaced(gui, cand.pos);
            if (!built.IsSuccess())
                return built;

            Gob placedGob = waitMilestone(cand.pos);
            if (placedGob == null)
                return Results.ERROR(L10n.get("road.err.no_stone"));
            last = placedGob;
            attempts = 0;
            scouts = 0;
            rejected.clear();
            planner.resetReach();
            placed++;
        }
        gui.msg(L10n.get("road.built", placed));
        return Results.SUCCESS();
    }

    /**
     * The Extend dialog is also captioned Milestone. Building has to use the window
     * with the Build button, and a sidestep while that dialog is up never starts the work.
     */
    private static Results finishPlaced(NGameUI gui, Coord2d pos) throws InterruptedException {
        Window extend = gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Extend");
        if (extend != null)
            new CloseTargetWindow(extend).run(gui);
        dropPreview(gui);

        Gob cons = findNear(pos, MCache.tilesz.x);
        if (cons == null)
            return Results.ERROR(L10n.get("road.err.no_stone"));
        Results approach = approachNear(cons);
        if (!approach.IsSuccess())
            return approach;
        cons = findNear(pos, MCache.tilesz.x);
        if (cons == null)
            return Results.ERROR(L10n.get("road.err.no_stone"));

        NUtils.rclickGob(cons);
        final Window[] opened = new Window[1];
        NUtils.addTask(new NTask() {
            int ticks;

            @Override
            public boolean check() {
                opened[0] = gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Build");
                return opened[0] != null || ++ticks > 80;
            }
        });
        if (opened[0] == null)
            return Results.ERROR(L10n.get("road.err.place"));

        for (int attempt = 0; attempt < 6; attempt++) {
            Window window = gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Build");
            if (window == null)
                break;
            for (NISBox slot : window.children(NISBox.class)) {
                int free = slot.calcFreeSpace();
                if (free > 0)
                    slot.put(free);
            }
            final Window filling = window;
            NUtils.addTask(new NTask() {
                int ticks;

                @Override
                public boolean check() {
                    return slotsFull(filling) || ++ticks > 30;
                }
            });
            window = gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Build");
            if (window == null)
                break;
            NUtils.startBuild(window);
            NUtils.addTask(new NTask() {
                int count;

                @Override
                public boolean check() {
                    return NUtils.getGameUI().prog != null || count++ > 100;
                }
            });
            WaitBuildState state = new WaitBuildState();
            NUtils.addTask(state);
            if (state.getState() == WaitBuildState.State.TIMEFORDRINK) {
                if (!(new Drink(0.9, false).run(gui)).IsSuccess())
                    return Results.ERROR("Drink is not found");
                continue;
            }
            if (state.getState() == WaitBuildState.State.DANGER)
                return Results.ERROR("Low energy");
            Gob now = findNear(pos, MCache.tilesz.x);
            if (now != null && now.ngob != null && !NParser.checkName(now.ngob.name, "gfx/terobjs/consobj"))
                return Results.SUCCESS();
        }
        Gob now = findNear(pos, MCache.tilesz.x);
        if (now != null && now.ngob != null && RoadRules.isMilestone(now.ngob.name))
            return Results.SUCCESS();
        return Results.ERROR(L10n.get("road.err.no_stone"));
    }

    private static boolean slotsFull(Window window) {
        boolean any = false;
        for (NISBox slot : window.children(NISBox.class)) {
            any = true;
            if (slot.calcFreeSpace() > 0)
                return false;
        }
        return any;
    }

    /**
     * Walk along the route so the next milestone tile is loaded.
     * Planning from the last stone skips every tile the map has not loaded yet.
     */
    private static boolean walkAhead(List<Coord2d> route, Coord2d from) throws InterruptedException {
        double tile = MCache.tilesz.x;
        double length = RoadGeometry.polylineLength(route);
        double s0 = RoadGeometry.project(route, from);
        if (length - s0 < tile * RoadRules.MIN_SEGMENT_TILES)
            return false;
        Coord2d look = RoadGeometry.pointAt(route, Math.min(s0 + 20 * tile, length));
        Gob player = NUtils.player();
        if (player == null || player.rc == null || look == null)
            return false;
        if (player.rc.dist(look) <= tile * 3)
            return false;
        return walkTo(look, 4);
    }

    /** Walks around trees to a point beside the stone. The stone tile itself is blocked for the path search. */
    private static Results approachNear(Gob gob) throws InterruptedException {
        Gob player = NUtils.player();
        if (gob == null || gob.rc == null)
            return Results.FAIL();
        if (player != null && player.rc.dist(gob.rc) <= MCache.tilesz.x * 3)
            return Results.SUCCESS();
        Coord2d spot = gob.rc;
        if (player != null) {
            Coord2d away = player.rc.sub(gob.rc);
            if (away.abs() > 1)
                spot = gob.rc.add(away.norm().mul(MCache.tilesz.x * 2));
        }
        walkTo(spot, 2);
        player = NUtils.player();
        if (player != null && player.rc.dist(gob.rc) <= MCache.tilesz.x * 4)
            return Results.SUCCESS();
        return Results.FAIL();
    }

    /**
     * Path search only. A straight walk runs into cliffs and trees.
     * When the goal itself is unreachable, a reachable tile next to it will do.
     */
    private static boolean walkTo(Coord2d goal, int nearTiles) throws InterruptedException {
        double tile = MCache.tilesz.x;
        double near = tile * nearTiles;
        Gob player = NUtils.player();
        if (player != null && player.rc.dist(goal) <= near)
            return true;
        if (pathTo(goal))
            return true;
        for (int r = 2; r <= Math.max(2, nearTiles); r += 2) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                if (pathTo(goal.add(Math.cos(a) * r * tile, Math.sin(a) * r * tile)))
                    return true;
            }
        }
        return false;
    }

    private static boolean pathTo(Coord2d goal) throws InterruptedException {
        try {
            return new PathFinder(goal).run(NUtils.getGameUI()).IsSuccess();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Removes the local placement hologram so a rejected spot does not stay on the ground. */
    private static void dropPreview(NGameUI gui) {
        if (gui == null || gui.map == null || gui.map.placing == null)
            return;
        gui.map.uimsg("unplace");
    }

    /** Only our construction or the finished milestone. A tree beside the spot must not count as placed. */
    private static Gob findNear(Coord2d pos, double reach) {
        if (pos == null) return null;
        Gob best = null;
        double bestD = reach;
        OCache oc = NUtils.getGameUI().map.glob.oc;
        long playerId = NUtils.playerID();
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob == null || gob.rc == null || gob.id == playerId || gob instanceof OCache.Virtual)
                    continue;
                if (gob instanceof haven.MapView.Plob) continue;
                String name = resName(gob);
                if (name == null || !(name.startsWith("gfx/terobjs/consobj") || RoadRules.isMilestone(name)))
                    continue;
                double d = gob.rc.dist(pos);
                if (d <= bestD) {
                    bestD = d;
                    best = gob;
                }
            }
        }
        return best;
    }

    /** A stack of twenty stones is twenty stones, not one inventory slot. */
    static int countMaterials(Iterable<WItem> items) {
        int total = 0;
        if (items == null) return 0;
        for (WItem w : items) {
            int n = 1;
            try {
                if (w != null && w.item instanceof NGItem) {
                    GItem.Amount amount = ((NGItem) w.item).getInfo(GItem.Amount.class);
                    if (amount != null)
                        n = Math.max(1, amount.itemnum());
                }
            } catch (Loading ignored) {
            }
            total += n;
        }
        return total;
    }

    /** Right-click opens the Milestone window. Extend is a button there, not a flower-menu petal. */
    private static boolean extend(NGameUI gui, Gob stone) throws InterruptedException {
        haven.FlowerMenu stray = gui.ui.root.findchild(haven.FlowerMenu.class);
        if (stray != null)
            stray.choose(null);
        // A button in a stale window from the previous try does nothing.
        Window old;
        while ((old = gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Extend")) != null) {
            new CloseTargetWindow(old).run(gui);
            if (gui.getWindowWithButton(RoadRules.WINDOW_NAME, "Extend") == old)
                break;
        }
        NUtils.rclickGob(stone);
        final Button[] hit = new Button[1];
        NUtils.addTask(new NTask() {
            int ticks;

            @Override
            public boolean check() {
                hit[0] = findExtend(gui);
                return hit[0] != null || ++ticks > 80;
            }
        });
        if (hit[0] == null)
            return false;
        hit[0].click();
        return true;
    }

    private static Button findExtend(NGameUI gui) {
        if (gui == null) return null;
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof Window)) continue;
            for (Button b : w.children(Button.class)) {
                if (b.text == null || b.text.text == null) continue;
                for (String want : RoadRules.EXTEND_PETALS) {
                    if (b.text.text.equalsIgnoreCase(want))
                        return b;
                }
            }
        }
        return null;
    }

    private static Gob waitMilestone(final Coord2d pos) throws InterruptedException {
        final Gob[] found = new Gob[1];
        NUtils.addTask(new NTask() {
            int ticks;

            @Override
            public boolean check() {
                Gob g = findNear(pos, MCache.tilesz.x);
                if (g != null && g.ngob != null && RoadRules.isMilestone(g.ngob.name)) {
                    found[0] = g;
                    return true;
                }
                return ++ticks > 80;
            }
        });
        return found[0];
    }

    private static NHitBox hitBox(Gob stone) {
        if (stone != null && stone.ngob != null && stone.ngob.hitBox != null)
            return stone.ngob.hitBox;
        String name = resName(stone);
        if (name != null) {
            NHitBox custom = NHitBox.findCustom(name);
            if (custom != null) return custom;
        }
        return new NHitBox(Coord.of(-4, -4), Coord.of(4, 4));
    }

    /**
     * The stone the player is standing at wins. Otherwise the closest milestone to the route start.
     * An end stone ({@code -e}) is preferred when several are equally close.
     */
    static Gob nearestEnd(NGameUI gui, Coord2d routeStart) {
        Gob player = NUtils.player();
        Coord2d playerRc = player == null ? null : player.rc;
        double tile = MCache.tilesz.x;
        double playerReach = 8 * tile;
        double routeReach = 40 * tile;
        Gob bestPlayerEnd = null;
        double bestPlayerEndD = playerReach;
        Gob bestPlayer = null;
        double bestPlayerD = playerReach;
        Gob bestRouteEnd = null;
        double bestRouteEndD = routeReach;
        Gob bestRoute = null;
        double bestRouteD = routeReach;
        OCache oc = gui.map.glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob == null || gob == player || gob.rc == null) continue;
                if (gob instanceof OCache.Virtual || gob.attr == null || gob.attr.isEmpty()) continue;
                String name = resName(gob);
                if (!RoadRules.isMilestone(name)) continue;
                boolean end = RoadRules.isRoadEnd(name);
                if (playerRc != null) {
                    double d = gob.rc.dist(playerRc);
                    if (d <= bestPlayerD) {
                        bestPlayerD = d;
                        bestPlayer = gob;
                    }
                    if (end && d <= bestPlayerEndD) {
                        bestPlayerEndD = d;
                        bestPlayerEnd = gob;
                    }
                }
                if (routeStart != null) {
                    double d = gob.rc.dist(routeStart);
                    if (d <= bestRouteD) {
                        bestRouteD = d;
                        bestRoute = gob;
                    }
                    if (end && d <= bestRouteEndD) {
                        bestRouteEndD = d;
                        bestRouteEnd = gob;
                    }
                }
            }
        }
        if (bestPlayerEnd != null) return bestPlayerEnd;
        if (bestPlayer != null) return bestPlayer;
        if (bestRouteEnd != null) return bestRouteEnd;
        return bestRoute;
    }

    private static String resName(Gob gob) {
        if (gob == null) return null;
        if (gob.ngob != null && gob.ngob.name != null) return gob.ngob.name;
        try {
            Drawable drawable = gob.getattr(Drawable.class);
            if (drawable != null && drawable.getres() != null)
                return drawable.getres().name;
        } catch (Loading ignored) {
        }
        return null;
    }

    private static boolean backToStone(NGameUI gui, long gobId, NGlobalCoord bookmark) throws InterruptedException {
        Gob live = Finder.findGob(gobId);
        if (live != null && NUtils.player() != null &&
                NUtils.player().rc.dist(live.rc) < MCache.tilesz.x * 40)
            return approachNear(live).IsSuccess();
        if (bookmark == null) return false;
        Coord2d here = bookmark.getCurrentCoord();
        if (here != null && walkTo(here, 3))
            return true;
        if (!(gui.map instanceof NMapView)) return false;
        ChunkNavManager nav = ((NMapView) gui.map).getChunkNavManager();
        if (nav == null || !nav.isInitialized() || bookmark.getLocalTile() == null)
            return false;
        ChunkPath path = nav.planToGridCoord(bookmark.getGridId(), bookmark.getLocalTile());
        if (path == null) return false;
        return nav.navigateWithPath(path, null, gui).IsSuccess();
    }
}
