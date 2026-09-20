package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import haven.MiniMap;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerSection;
import nurgling.routes.ForagerWaypoint;
import nurgling.tasks.WaitDuration;
import nurgling.tools.Finder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Walk-only forward traversal of a saved Forager route; waypoint actions are intentionally ignored. */
public final class FollowRoute implements Action {
    private static final double MAX_HOP_DISTANCE = 250.0;
    private static final double MILESTONE_APPROACH_DIST = 20.0;
    private static final int DEST_MAP_READY_ATTEMPTS = 40;
    private static final long DEST_MAP_READY_POLL_MS = 200;
    private static final double DRINK_BELOW = 0.5;
    private static final double DRINK_TARGET = 0.9;
    private static final long DRINK_RETRY_MS = 60_000;

    private final ForagerPath path;
    private final RouteWalkControl control;
    private final boolean routePreclaimed;
    private final List<Coord2d> stallSpots = new ArrayList<>();
    private long lastFailedDrinkMs;
    private boolean reportedNoWater;

    public FollowRoute(ForagerPath path, RouteWalkControl control) {
        this(path, control, false);
    }

    public FollowRoute(ForagerPath path, RouteWalkControl control, boolean routePreclaimed) {
        this.path = path;
        this.control = control;
        this.routePreclaimed = routePreclaimed;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        String terminalStatus = "routewalker.failed";
        boolean ownsRoute = routePreclaimed;
        try {
            String invalid = validationErrorKey(path, gui);
            if (invalid != null) return Results.ERROR(L10n.get(invalid));
            if (routePreclaimed) {
                synchronized (gui) {
                    if (gui.activeBotPath != path) return Results.ERROR(L10n.get("routewalker.busy"));
                }
            } else if (!claimRoute(gui, path)) {
                return Results.ERROR(L10n.get("routewalker.busy"));
            } else {
                ownsRoute = true;
            }

            control.progress(0, path.waypoints.size());
            control.status("routewalker.status_starting");
            awaitResume(gui);
            MiniMap.Location sessloc = gui.mmap.sessloc;
            Gob player = NUtils.player();
            if (sessloc == null) return Results.ERROR(L10n.get("routewalker.no_location"));
            if (player == null) return Results.ERROR(L10n.get("routewalker.no_player"));

            path.generateSections(sessloc);
            int entry = nearestResolvableWaypointIndex(path, sessloc, player.rc);
            // A milestone splice creates a placeholder section even while every real waypoint is
            // on another segment, so bridge based on resolvable coordinates rather than section count.
            if (entry < 0) {
                bridgeToRoute(gui);
                sessloc = gui.mmap != null ? gui.mmap.sessloc : null;
                player = NUtils.player();
                entry = nearestResolvableWaypointIndex(path, sessloc, player != null ? player.rc : null);
            }
            if (entry < 0) return Results.ERROR(L10n.get("routewalker.unresolved_route"));
            if (!hasRouteLease(gui, path)) return Results.ERROR(L10n.get("routewalker.busy"));
            Coord2d entryPos = path.waypoints.get(entry).toWorldCoord(sessloc);
            updateActiveWaypoint(gui, path, entry);
            control.progress(entry + 1, path.waypoints.size());
            control.status("routewalker.status_joining");
            awaitResume(gui);
            checkStamina(gui);
            entryPos = milestoneApproachTarget(path.waypoints.get(entry), entryPos, NUtils.player());
            if (!walkTo(gui, entryPos))
                return Results.ERROR(L10n.get("routewalker.unreachable", entry + 1));

            if (entry >= path.waypoints.size() - 1) {
                gui.msg(L10n.get("routewalker.already_finished"));
                terminalStatus = "routewalker.finished";
                return Results.SUCCESS();
            }

            control.status("routewalker.status_walking");
            int completedWaypoint = entry;
            for (int i = firstSectionIndexAtOrAfterWaypoint(path, entry); i < path.getSectionCount(); i++) {
                if (!hasRouteLease(gui, path)) return Results.ERROR(L10n.get("routewalker.busy"));
                ForagerSection section = path.getSection(i);
                if (section == null || section.waypointIndex < 0
                        || section.waypointIndex + 1 >= path.waypoints.size())
                    return Results.ERROR(L10n.get("routewalker.invalid_route"));
                // generateSections deliberately skips non-resolvable gaps. Never interpret the
                // first section after such a gap as a valid continuation or silently finish early.
                if (section.waypointIndex != completedWaypoint)
                    return Results.ERROR(L10n.get("routewalker.unresolved_waypoint"));

                ForagerWaypoint fromWp = path.waypoints.get(section.waypointIndex);
                ForagerWaypoint toWp = path.waypoints.get(section.waypointIndex + 1);
                if (fromWp == null || toWp == null)
                    return Results.ERROR(L10n.get("routewalker.invalid_route"));

                updateActiveWaypoint(gui, path, section.waypointIndex + 1);
                control.progress(section.waypointIndex + 2, path.waypoints.size());
                awaitResume(gui);
                checkStamina(gui);

                if (isMilestoneSplice(fromWp, toWp)) {
                    control.status("routewalker.status_milestone");
                    // Route Walker validates the landing itself. Passing null avoids UseMilestone's
                    // Forager-specific hearth recovery on a bad route.
                    Results travelled = new UseMilestone(fromWp.milestoneHash, null, null).run(gui);
                    if (!travelled.IsSuccess()) return travelled;
                    awaitResume(gui);

                    MiniMap.Location[] readyLoc = new MiniMap.Location[1];
                    Coord2d destination = waitForDestinationWorldCoord(toWp, () -> {
                        MiniMap.Location current = gui.mmap != null ? gui.mmap.sessloc : null;
                        readyLoc[0] = current;
                        return current;
                    }, DEST_MAP_READY_ATTEMPTS,
                            () -> NUtils.addTask(new WaitDuration(DEST_MAP_READY_POLL_MS)));
                    Gob landedPlayer = NUtils.player();
                    Coord2d landedAt = landedPlayer != null ? landedPlayer.rc : null;
                    UseMilestone.Arrival arrival = UseMilestone.classifyArrival(
                            destination, landedAt, UseMilestone.wrongLocationTolerance());
                    if (arrival == UseMilestone.Arrival.UNRESOLVED)
                        return Results.ERROR(L10n.get("routewalker.milestone_unresolved"));
                    if (arrival == UseMilestone.Arrival.WRONG_PLACE)
                        return Results.ERROR(L10n.get("routewalker.milestone_wrong_place"));

                    completedWaypoint = section.waypointIndex + 1;
                    path.generateSections(readyLoc[0]);
                    i = nextSectionIndexAfterTeleport(path, section.waypointIndex) - 1;
                    control.status("routewalker.status_walking");
                    continue;
                }

                Coord2d target = resolveSectionWalkTarget(section, toWp,
                        gui.mmap != null ? gui.mmap.sessloc : null);
                if (target == null) return Results.ERROR(L10n.get("routewalker.unresolved_waypoint"));
                target = milestoneApproachTarget(toWp, target, NUtils.player());
                if (!walkTo(gui, target)) {
                    markFailedWaypoint(gui, path, section.waypointIndex + 1);
                    return Results.ERROR(L10n.get("routewalker.unreachable", section.waypointIndex + 2));
                }
                if (section.isLastInGap) completedWaypoint = section.waypointIndex + 1;
            }

            if (!routeCompleted(path, completedWaypoint))
                return Results.ERROR(L10n.get("routewalker.unresolved_waypoint"));
            gui.msg(L10n.get("routewalker.route_finished"));
            terminalStatus = "routewalker.finished";
            return Results.SUCCESS();
        } catch (InterruptedException stopped) {
            stopNow(gui);
            terminalStatus = "routewalker.stopped";
            return Results.FAIL();
        } finally {
            if (ownsRoute) releaseRoute(gui, path);
            control.finish(terminalStatus);
        }
    }

    private void bridgeToRoute(NGameUI gui) throws InterruptedException {
        awaitResume(gui);
        ForagerWaypoint first = path.waypoints.get(0);
        if (first == null || first.gridId == -1 || first.localTile == null || !(gui.map instanceof NMapView)) return;
        ChunkNavManager manager = ((NMapView) gui.map).getChunkNavManager();
        if (manager == null || !manager.isInitialized()) return;
        control.status("routewalker.status_bridging");
        gui.msg(L10n.get("routewalker.bridging"));
        ChunkPath bridge = manager.planToGridCoord(first.gridId, first.localTile);
        if (bridge != null && manager.navigateWithPath(bridge, null, gui).IsSuccess()) {
            awaitResume(gui);
            path.generateSections(gui.mmap != null ? gui.mmap.sessloc : null);
        }
    }

    private boolean walkTo(NGameUI gui, Coord2d destination) throws InterruptedException {
        if (destination == null) return false;
        while (true) {
            awaitResume(gui);
            Gob player = NUtils.player();
            if (player == null) return false;
            double remaining = player.rc.dist(destination);
            boolean lastHop = remaining <= MAX_HOP_DISTANCE;
            Coord2d target = lastHop ? destination
                    : player.rc.add(destination.sub(player.rc).norm(MAX_HOP_DISTANCE));
            PathFinder finder = new PathFinder(target).withAbort(control::abortRequested);
            finder.learnedBlocks = stallSpots;
            Results walked = finder.run(gui);
            if (finder.abortedByCaller()) {
                awaitResume(gui);
                continue;
            }
            if (!walked.IsSuccess()) return false;
            if (lastHop) return true;
        }
    }

    private void awaitResume(NGameUI gui) throws InterruptedException {
        if (!control.isPaused()) {
            control.awaitResume();
            return;
        }
        PathFinder.stopHere(gui);
        control.status("routewalker.status_paused");
        gui.msg(L10n.get("routewalker.paused"));
        control.awaitResume();
        control.status("routewalker.status_walking");
        gui.msg(L10n.get("routewalker.resumed"));
    }

    private void checkStamina(NGameUI gui) throws InterruptedException {
        if (!Boolean.TRUE.equals(NConfig.get(NConfig.Key.autoDrink))) return;
        double stamina = NUtils.getStamina();
        if (stamina < 0 || stamina >= DRINK_BELOW) return;
        if (gui.drinkMeter != null && gui.drinkMeter.getTotalDrinkable() <= 0) {
            if (!reportedNoWater) {
                reportedNoWater = true;
                gui.msg(L10n.get("routewalker.no_water"));
            }
            return;
        }
        if (System.currentTimeMillis() - lastFailedDrinkMs < DRINK_RETRY_MS) return;
        control.status("routewalker.status_drinking");
        if (!new Drink(DRINK_TARGET, false).run(gui).IsSuccess())
            lastFailedDrinkMs = System.currentTimeMillis();
        control.status("routewalker.status_walking");
    }

    public static String validationErrorKey(ForagerPath route, NGameUI gui) {
        if (route == null || route.waypoints == null || route.waypoints.size() < 2)
            return "routewalker.need_two";
        for (ForagerWaypoint waypoint : route.waypoints)
            if (waypoint == null) return "routewalker.invalid_route";
        if (gui == null || gui.mmap == null) return "routewalker.no_location";
        if (NUtils.player() == null) return "routewalker.no_player";
        return null;
    }

    public static boolean valid(ForagerPath route, NGameUI gui) {
        return validationErrorKey(route, gui) == null;
    }

    /** Atomically reserves the shared route overlay for one runner. */
    public static boolean claimRoute(NGameUI gui, ForagerPath route) {
        if (gui == null || route == null) return false;
        synchronized (gui) {
            if (gui.activeBotPath != null) return false;
            gui.activeBotPath = route;
            gui.activeBotWaypointIndex = -1;
            gui.activeBotFailedWaypoints = ConcurrentHashMap.newKeySet();
            return true;
        }
    }

    /** Releases only this run's lease; another bot's later overlay is left untouched. */
    public static void releaseRoute(NGameUI gui, ForagerPath route) {
        if (gui == null) return;
        synchronized (gui) {
            if (gui.activeBotPath != route) return;
            gui.activeBotPath = null;
            gui.activeBotWaypointIndex = -1;
            gui.activeBotFailedWaypoints = null;
        }
    }

    public static void stopNow(NGameUI gui) {
        if (gui != null) PathFinder.stopHere(gui);
    }

    private static void markFailedWaypoint(NGameUI gui, ForagerPath route, int waypoint) {
        synchronized (gui) {
            if (gui.activeBotPath == route && gui.activeBotFailedWaypoints != null)
                gui.activeBotFailedWaypoints.add(waypoint);
        }
    }

    private static void updateActiveWaypoint(NGameUI gui, ForagerPath route, int waypoint) {
        synchronized (gui) {
            if (gui.activeBotPath == route) gui.activeBotWaypointIndex = waypoint;
        }
    }

    private static boolean hasRouteLease(NGameUI gui, ForagerPath route) {
        synchronized (gui) {
            return gui.activeBotPath == route;
        }
    }

    static boolean routeCompleted(ForagerPath route, int completedWaypoint) {
        return route != null && route.waypoints != null
                && !route.waypoints.isEmpty() && completedWaypoint == route.waypoints.size() - 1;
    }

    public static Coord2d resolveSectionWalkTarget(
            ForagerSection section, ForagerWaypoint destination, MiniMap.Location location) {
        if (section == null) return null;
        if (section.isLastInGap && destination != null && location != null) {
            Coord2d fresh = destination.toWorldCoord(location);
            if (fresh != null) return fresh;
        }
        return section.endPoint;
    }

    static Coord2d milestoneApproachTarget(ForagerWaypoint destination, Coord2d fallback, Gob player) {
        if (destination == null || destination.milestoneHash == null || player == null) return fallback;
        Gob milestone = Finder.findGob(destination.milestoneHash);
        if (milestone == null) return fallback;
        Coord2d away = player.rc.sub(milestone.rc);
        if (away.abs() <= 0.01) return fallback;
        return milestone.rc.add(away.norm(MILESTONE_APPROACH_DIST));
    }

    static boolean isMilestoneSplice(ForagerWaypoint from, ForagerWaypoint to) {
        return from != null && to != null && from.milestoneHash != null
                && from.milestoneHash.equals(to.milestoneHash);
    }

    public static int nearestResolvableWaypointIndex(
            ForagerPath route, MiniMap.Location location, Coord2d at) {
        if (route == null || route.waypoints == null || location == null || at == null) return -1;
        int best = -1;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < route.waypoints.size(); i++) {
            ForagerWaypoint waypoint = route.waypoints.get(i);
            Coord2d candidate = waypoint != null ? waypoint.toWorldCoord(location) : null;
            if (candidate == null) continue;
            double current = at.dist(candidate);
            if (current < distance) {
                distance = current;
                best = i;
            }
        }
        return best;
    }

    public static int firstSectionIndexAtOrAfterWaypoint(ForagerPath route, int waypoint) {
        if (route == null) return 0;
        for (int i = 0; i < route.getSectionCount(); i++) {
            ForagerSection section = route.getSection(i);
            if (section != null && section.waypointIndex >= waypoint) return i;
        }
        return route.getSectionCount();
    }

    public static int nextSectionIndexAfterTeleport(ForagerPath route, int completedWaypoint) {
        if (route == null) return 0;
        for (int i = 0; i < route.getSectionCount(); i++) {
            ForagerSection section = route.getSection(i);
            if (section != null && section.waypointIndex > completedWaypoint) return i;
        }
        return route.getSectionCount();
    }

    @FunctionalInterface
    public interface InterruptibleWait { void await() throws InterruptedException; }

    public static Coord2d waitForDestinationWorldCoord(
            ForagerWaypoint destination, Supplier<MiniMap.Location> location,
            int maxAttempts, InterruptibleWait wait) throws InterruptedException {
        int attempts = Math.max(1, maxAttempts);
        Coord2d world = destinationWorldCoord(destination, location == null ? null : location.get());
        for (int i = 1; world == null && i < attempts; i++) {
            if (wait != null) wait.await();
            world = destinationWorldCoord(destination, location == null ? null : location.get());
        }
        return world;
    }

    private static Coord2d destinationWorldCoord(ForagerWaypoint destination, MiniMap.Location location) {
        return destination == null || location == null ? null : destination.toWorldCoord(location);
    }
}
