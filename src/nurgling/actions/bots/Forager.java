package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NDiscordNotification;
import nurgling.conf.NForagerProp;
import nurgling.guarding.*;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.routes.*;
import nurgling.actions.bots.forager.DetourBranchBudget;
import nurgling.actions.bots.forager.ForagerDrinkPolicy;
import nurgling.actions.bots.forager.RouteLookahead;
import nurgling.i18n.L10n;
import nurgling.tools.AreaStock;
import nurgling.tools.Finder;
import nurgling.tools.MilestoneRegistry;
import nurgling.tools.NAlias;
import nurgling.tasks.GateDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class Forager implements Action {

    private HashSet<Long> processedGobs = new HashSet<>();
    private String presetName = null;

    // The Guard whose trigger fired, claimed atomically by the catch block below - a plain
    // boolean+reference pair here couldn't tell a genuine external cancel racing the watcher's own
    // interrupt apart from the watcher's own stop (both would just see the boolean already true).
    // compareAndSet on claim, getAndSet(null) on consume: at most one InterruptedException gets
    // attributed to a given guard firing, and any interrupt that arrives once this is empty again
    // is unambiguously external.
    private final java.util.concurrent.atomic.AtomicReference<Guard> pendingGuard = new java.util.concurrent.atomic.AtomicReference<>();

    // Resolved once near the top of run() - see resolveGuardingProfile().
    private GuardingProfile guardingProfile = null;
    private Guard dangerGuard = null;
    private java.util.function.Supplier<List<PathFinder.AvoidZone>> avoidZones = null;
    private boolean routeLegBlocked = false;
    private final ArrayList<Coord2d> stallSpots = new ArrayList<>();
    private Thread threatWatcher = null;

    public static final String HEARTH_UNLOAD_HEARTH = "hearth, unload, hearth";

    // Set once run() has resolved it, so performGobAction() can persist a confirmed flower-menu action.
    private NForagerProp forageProp = null;

    // Per-run Maintain baseline (see ForagerAction.maintainQuantity), keyed by sourceItemName so an in-run Edit Pattern save can't orphan it.
    private Map<String, Integer> maintainAreaStock = new HashMap<>();

    // Route-geometry limits configured per-route in Forager Settings - see ForagerRouteConstraints.
    private nurgling.actions.bots.forager.ForagerRouteConstraints routeConstraints;

    // Replaced for every invocation because the Recent Actions panel can re-run this instance.
    private ForagerDrinkPolicy.RunState drinkState = new ForagerDrinkPolicy.RunState();

    public Forager() {
        // Default constructor - will show UI
    }

    public Forager(Map<String, Object> settings) {
        // Constructor for scenario usage - uses preset from settings
        if (settings != null && settings.containsKey("presetName")) {
            this.presetName = (String) settings.get("presetName");
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        drinkState = new ForagerDrinkPolicy.RunState();
        NForagerProp prop = null;
        NForagerProp.PresetData preset = null;

        if (presetName != null) {
            // Scenario mode: load preset directly without UI
            prop = NForagerProp.get(NUtils.getUI().sessInfo);
            if (prop == null) {
                return Results.ERROR("Cannot load forager properties");
            }

            preset = prop.presets.get(presetName);
            if (preset == null) {
                return Results.ERROR("Preset not found: " + presetName);
            }

            // Load path if not already loaded
            if (preset.foragerPath == null && !preset.pathFile.isEmpty()) {
                try {
                    preset.foragerPath = ForagerPath.load(preset.pathFile);
                } catch (Exception e) {
                    return Results.ERROR("Failed to load path: " + e.getMessage());
                }
            }
        } else {
            // Interactive mode: show UI
            nurgling.widgets.bots.Forager w = null;
            try {
                NUtils.getUI().core.addTask(new nurgling.tasks.WaitCheckable(
                    NUtils.getGameUI().add((w = new nurgling.widgets.bots.Forager()), UI.scale(200, 200))
                ));
                if (w.cancelled)
                    return Results.FAIL();
                prop = w.prop;
            } catch (InterruptedException e) {
                throw e;
            } finally {
                if (w != null)
                    w.destroy();
            }

            if (prop == null) {
                return Results.ERROR("No configuration");
            }

            preset = prop.presets.get(prop.currentPreset);
        }

        if (preset == null || preset.foragerPath == null) {
            return Results.ERROR("No path configured");
        }

        forageProp = prop;

        // Overwrite the legacy preset.actions with a defensive copy of its Actions Profile, so concurrent Settings edits can't race this run's iteration.
        String actionsProfileName = preset.actionsProfileName != null ? preset.actionsProfileName : prop.currentActionsProfile;
        if (prop.actionsProfiles != null && actionsProfileName != null) {
            ArrayList<ForagerAction> profileActions = prop.actionsProfiles.get(actionsProfileName);
            if (profileActions != null) {
                preset.actions = new ArrayList<>(profileActions);
            }
        }

        ForagerPath path = preset.foragerPath;

        if (path.waypoints == null || path.waypoints.size() < 2) {
            return Results.ERROR("Path has fewer than 2 waypoints");
        }

        routeConstraints = new nurgling.actions.bots.forager.ForagerRouteConstraints(path);

        gui.activeBotPath = path;
        // Index of the waypoint Forager is currently heading toward - lets NWaypointOverlay color it as active.
        gui.activeBotWaypointIndex = 0;
        // Concurrent set: the render thread iterates this via NWaypointOverlay while this bot
        // thread adds to it, with no other synchronization between them.
        gui.activeBotFailedWaypoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
        threatWatcher = null;
        try {

        guardingProfile = resolveGuardingProfile(prop, preset);
        dangerGuard = resolveDangerGuard(guardingProfile);
        final boolean avoidIgnoreBats = guardingProfile.ignoreBats;
        avoidZones = dangerGuard == null ? null : () -> routeConstraints.dangerZones(gui, avoidIgnoreBats);
        stallSpots.clear();

        // Pre-flight guards: checked once before any movement; in-flight guards run continuously below.
        GuardContext preflightCtx = new GuardContext(gui, guardingProfile.ignoreBats);
        for (Guard guard : buildGuards(guardingProfile.preflightGuards)) {
            try {
                if (guard.trigger.check(preflightCtx)) {
                    gui.msg("Forager: pre-flight - " + guard.trigger.describe() + " (" + guard.outcome.id() + ")");
                    guard.outcome.perform(gui);
                    return Results.SUCCESS();
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                // Don't let one bad read kill the run before it even starts.
            }
        }

        // Runs continuously in the background so a mid-walk threat still triggers the safety action immediately.
        threatWatcher = startGuardWatcher(gui, guardingProfile, Thread.currentThread());

        // One-time Put-area audit for Maintain's pickup budget; skipped if the preset ignores Maintain limits.
        maintainAreaStock = preset.ignoreMaintainLimits
                ? java.util.Collections.emptyMap()
                : resolveMaintainAreaStock(gui, preset);

        // Every pickup action already maintained - nothing this run could collect, so there's no
        // point walking the route at all.
        if (nothingLeftToPickUp(gui, preset)) {
            gui.msg("Forager: every item is already at its Maintain quantity - nothing to do, stopping.");
            performSafetyAction(gui, preset.afterFinishAction);
            return Results.SUCCESS();
        }

        // Sections are segment-relative, so they can only be (re)computed while standing on the path's own segment.
        path.generateSections();
        if (path.getSectionCount() == 0) {
            // Not on the route's segment - bridge over via ChunkNav to the route's first waypoint.
            ForagerWaypoint firstWp = path.waypoints.get(0);
            if (firstWp.gridId != -1 && firstWp.localTile != null && gui.map instanceof NMapView) {
                ChunkNavManager chunkNav = ((NMapView) gui.map).getChunkNavManager();
                if (chunkNav != null && chunkNav.isInitialized()) {
                    gui.msg("Forager: not on the route's segment - trying ChunkNav to its first waypoint");
                    ChunkPath cp = chunkNav.planToGridCoord(firstWp.gridId, firstWp.localTile);
                    if (cp != null && chunkNav.navigateWithPath(cp, null, gui).IsSuccess()) {
                        path.generateSections();
                    }
                }
            }
        }
        if (path.getSectionCount() == 0) {
            return Results.ERROR("Forager: could not resolve path waypoints from the current location " +
                    "(wrong map/segment - begin the bot from near the path, or re-record it so its " +
                    "waypoints have a ChunkNav grid to bridge from)");
        }

        // Pick the closest recorded point that resolves on the route's current segment, then
        // continue only through the route gaps that leave that waypoint. Long gaps are split
        // into several sections, so section index and waypoint index are not interchangeable.
        MiniMap.Location sessloc = gui.mmap != null ? gui.mmap.sessloc : null;
        if(sessloc == null) {
            return Results.ERROR("Cannot get sessloc");
        }
        Gob startPlayer = NUtils.player();
        Coord2d playerStartPos = startPlayer != null ? startPlayer.rc : null;
        int startWaypointIndex = nearestResolvableWaypointIndex(path, sessloc, playerStartPos);
        gui.activeBotWaypointIndex = startWaypointIndex;
        ForagerWaypoint startWaypoint = path.waypoints.get(startWaypointIndex);
        Coord2d startPos = startWaypoint.toWorldCoord(sessloc);
        if(startPos == null) {
            return Results.ERROR("Cannot get start position - waypoint not in current segment");
        }

        checkStamina(gui);
        Results startResult = walk(gui, preset, new PathFinder(startPos), true);
        if (!shouldContinueAfterInitialPathFinder(startResult)) {
            return startResult;
        }

        // Only run this waypoint's steps if we actually reached it.
        if (runWaypointSteps(gui, startWaypoint)) {
            return Results.SUCCESS();
        }

        // Check inventory before starting
        if (isInventoryFull(gui) && !preset.onFullInventoryAction.equals("nothing")) {
            performSafetyAction(gui, preset.onFullInventoryAction);
            return Results.SUCCESS();
        }

        int zoneBlockedWaypoint = -1;
        // Main loop through sections
        for (int i = firstSectionIndexAtOrAfterWaypoint(path, startWaypointIndex); i < path.getSectionCount(); i++)
        {
            ForagerSection section = path.getSection(i);
            if (section == null) continue;
            if (section.waypointIndex + 1 == zoneBlockedWaypoint) continue;
            routeLegBlocked = false;

            checkStamina(gui);

            // A waypoint gap longer than ForagerPath.SECTION_LENGTH gets split across multiple
            // sections (see generateSections()), so the section-loop counter i is NOT the same
            // as the waypoint index - always go through section.waypointIndex instead.
            ForagerWaypoint fromWp = path.waypoints.get(section.waypointIndex);
            ForagerWaypoint toWp = path.waypoints.get(section.waypointIndex + 1);
            gui.activeBotWaypointIndex = section.waypointIndex + 1;
            if (fromWp.milestoneHash != null && fromWp.milestoneHash.equals(toWp.milestoneHash)) {
                // toWp validates we actually landed near the expected destination.
                Results milestoneResult = new UseMilestone(fromWp.milestoneHash, toWp, guardingProfile).run(gui);
                if (!milestoneResult.IsSuccess()) {
                    return milestoneResult;
                }
                stallSpots.clear();
                MiniMap.Location[] readyLoc = new MiniMap.Location[1];
                Coord2d destWorld = waitForDestinationWorldCoord(
                        toWp,
                        () -> {
                            MiniMap.Location loc = gui.mmap != null ? gui.mmap.sessloc : null;
                            readyLoc[0] = loc;
                            return loc;
                        },
                        destMapReadyAttempts(),
                        () -> NUtils.addTask(new nurgling.tasks.WaitDuration(DEST_MAP_READY_POLL_MS)));
                Gob playerAfterTravel = NUtils.player();
                Coord2d playerRc = playerAfterTravel != null ? playerAfterTravel.rc : null;
                MilestoneContinuation cont = continuationAfterMilestoneArrival(destWorld, playerRc);
                if (cont.stop != null) {
                    if (cont.arrival == UseMilestone.Arrival.WRONG_PLACE) {
                        gui.msg("Forager: milestone travel didn't land near the route's recorded destination "
                                + "- route may be broken. Teleporting home.");
                        if (CoracleBot.isPlayerInCoracle(gui)) {
                            new CoracleBot().run(gui);
                        }
                        return UseMilestone.resultAfterConfirmedWrongPlace(new TravelToHearthFire().run(gui));
                    }
                    gui.error("Forager: destination map location never became ready after milestone travel"
                            + " - cannot continue without world coordinates. Stopping instead of walking"
                            + " from stale origin coordinates.");
                    return cont.stop;
                }
                if (cont.runDestinationSteps && runWaypointSteps(gui, toWp)) {
                    return Results.SUCCESS();
                }
                if (cont.regenerateSections) {
                    path.generateSections(readyLoc[0]);
                    i = nextSectionIndexAfterTeleport(path, section.waypointIndex) - 1;
                }
                continue;
            }

            MiniMap.Location currentSessloc = gui.mmap != null ? gui.mmap.sessloc : null;
            Coord2d sectionEnd = resolveSectionWalkTarget(section, toWp, currentSessloc);

            // Detour to a known actionable gob first if it's closer than this section's own target.
            Gob playerBeforeWalk = NUtils.player();
            if (playerBeforeWalk != null) {
                Pair<Gob, ForagerAction> nearest = findNearestActionableGob(gui, playerBeforeWalk.rc, preset.actions,
                        playerBeforeWalk.rc, preset.ignoreMaintainLimits, effectiveWaterMode(gui, preset),
                        lookaheadFrom(gui, path, i, playerBeforeWalk.rc), null);
                if (nearest != null && playerBeforeWalk.rc.dist(nearest.a.rc) < playerBeforeWalk.rc.dist(sectionEnd)) {
                    collectNearbyActionableGobs(gui, preset, path, i);
                    if (isInventoryFull(gui) && !preset.onFullInventoryAction.equals("nothing")) {
                        performSafetyAction(gui, preset.onFullInventoryAction);
                        return Results.SUCCESS();
                    }
                }
            }

            // Milestone anchors: walk to a plain tile-target point short of the milestone rather than gob-targeted PathFinder (which failed on these).
            if (toWp.milestoneHash != null) {
                Gob milestoneGob = Finder.findGob(toWp.milestoneHash);
                if (milestoneGob != null) {
                    Coord2d approachPoint = resolveMilestoneWalkTarget(
                            milestoneGob, playerBeforeWalk != null ? playerBeforeWalk.rc : null, sectionEnd);
                    boolean arrivedNearMilestone = walk(gui, preset, new PathFinder(approachPoint), true).IsSuccess();
                    if (arrivedNearMilestone && runWaypointSteps(gui, toWp)) {
                        return Results.SUCCESS();
                    }
                    continue;
                }
                // Gob missing: fall through and walk to the section end instead of skipping the hop.
            }

            // Check if there are any target objects near the section endpoint (within 1 tile = 11 units)
            Gob targetGob = findGobNear(sectionEnd, 11.0);

            // Cover the ground in rescanning hops first, so a gob revealed mid-section still gets detoured to.
            Gob walkStart = NUtils.player();
            RouteLookahead walkLookahead = lookaheadFrom(gui, path, i, walkStart != null ? walkStart.rc : null);
            boolean reachedApproach = walkInHops(gui, preset, targetGob != null ? targetGob.rc : sectionEnd, null, walkLookahead);

            if (isInventoryFull(gui) && !preset.onFullInventoryAction.equals("nothing")) {
                performSafetyAction(gui, preset.onFullInventoryAction);
                return Results.SUCCESS();
            }

            // Whether this section's walk actually landed at/near waypoint i+1, gating the steps call below.
            boolean arrivedAtWaypoint = reachedApproach;
            if (!reachedApproach) {
                if (!isInventoryFull(gui)) {
                    gui.msg("Forager debug: section " + i + " failed pathing en route to "
                            + (targetGob != null ? "gob" : "sectionEnd=" + sectionEnd)
                            + " - waterMode=" + effectiveWaterMode(gui, preset) + " mounted=" + CoracleBot.isPlayerInCoracle(gui));
                    gui.activeBotFailedWaypoints.add(section.waypointIndex + 1);
                }
            } else if (targetGob != null)
            {
                // walkInHops only guarantees getting within MAX_HOP_DISTANCE, not precise arrival.
                Results pfGobResult = walk(gui, preset, new PathFinder(targetGob), true);
                arrivedAtWaypoint = pfGobResult.IsSuccess();
                if (!arrivedAtWaypoint) {
                    gui.msg("Forager debug: section " + i + " failed pathing to gob - waterMode="
                            + effectiveWaterMode(gui, preset) + " mounted=" + CoracleBot.isPlayerInCoracle(gui));
                    gui.activeBotFailedWaypoints.add(section.waypointIndex + 1);
                }
            } else
            {
                // Go to the endpoint if no objects found nearby
                Results pfEndResult = walk(gui, preset, new PathFinder(sectionEnd), true);
                arrivedAtWaypoint = pfEndResult.IsSuccess();
                if (!arrivedAtWaypoint) {
                    gui.msg("Forager debug: section " + i + " failed pathing to sectionEnd=" + sectionEnd
                            + " - waterMode=" + effectiveWaterMode(gui, preset) + " mounted=" + CoracleBot.isPlayerInCoracle(gui));
                    gui.activeBotFailedWaypoints.add(section.waypointIndex + 1);
                }
            }

            if (routeLegBlocked) zoneBlockedWaypoint = section.waypointIndex + 1;
            // Waypoint steps only run once we've actually reached the real waypoint - not on an
            // intermediate sub-section of a gap that got split across multiple sections.
            if (arrivedAtWaypoint && section.isLastInGap && runWaypointSteps(gui, toWp)) {
                return Results.SUCCESS();
            }

            // Repeatedly grab the nearest unprocessed actionable gob until nothing more is found nearby.
            collectNearbyActionableGobs(gui, preset, path, i + 1);

            // One-shot scan-and-notify, handled separately from the per-gob walk-to model.
            processChatNotifyActions(gui, section, preset.actions);

            // Check inventory after each section
            if (isInventoryFull(gui)) {
                if (!preset.onFullInventoryAction.equals("nothing")) {
                    performSafetyAction(gui, preset.onFullInventoryAction);
                    return Results.SUCCESS();
                }
            }

            // Everything's become maintained partway through the route - no point walking the rest.
            if (nothingLeftToPickUp(gui, preset)) {
                gui.msg("Forager: every item is now at its Maintain quantity - nothing left to do, stopping.");
                performSafetyAction(gui, preset.afterFinishAction);
                return Results.SUCCESS();
            }
        }

        // After completing all sections, perform finish action
        performSafetyAction(gui, preset.afterFinishAction);

        return Results.SUCCESS();
        } catch (InterruptedException e) {
            // Distinguish the watcher's own deliberate stop from a genuine external cancel, which
            // must keep propagating. Consuming (not just reading) the claim means a second,
            // unrelated interrupt - e.g. a real external cancel landing right after the guard's
            // own - won't be misattributed to this same guard firing a second time.
            Guard triggeredGuard = pendingGuard.getAndSet(null);
            if (triggeredGuard != null) {
                // Retry the safety action itself on further interrupts - it's the character's actual way home, it must not give up partway.
                InterruptedException last = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        triggeredGuard.outcome.perform(gui);
                        gui.msg("Forager: stopped safely after safety action");
                        return Results.SUCCESS();
                    } catch (InterruptedException retry) {
                        last = retry;
                        Thread.interrupted();
                        gui.msg("Forager: safety action interrupted mid-way, retrying (" + attempt + "/3)");
                    }
                }
                gui.error("Forager: safety action repeatedly interrupted - check the character reached home safely");
                return Results.ERROR("Safety action interrupted after 3 attempts: " +
                        (last != null ? last.getMessage() : "unknown"));
            }
            throw e;
        } catch (RuntimeException e) {
            // Without this, an unexpected bug (like the section/waypoint-index mismatch that used
            // to crash here) kills the bot thread with zero in-game feedback - the character just
            // stops with no explanation, which is exactly what made that bug so hard to diagnose.
            // Rethrown after reporting so the console still gets the full stack trace as before.
            gui.error("Forager: unexpected error (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + ") - bot stopped. Check the java console log for the full stack trace.");
            throw e;
        } finally {
            stopGuardWatcher();
            gui.activeBotPath = null;
            gui.activeBotDetourTrail = null;
            gui.activeBotDetourTarget = null;
            gui.activeBotWaypointIndex = -1;
            gui.activeBotFailedWaypoints = null;
        }
    }

    // Max world-unit distance for a single PathFinder hop - PathFinder's search grid fails to path once the span exceeds this.
    private static final double MAX_HOP_DISTANCE = 250.0;

    // Scan radius (world units) for finding actionable gobs to detour towards.
    private static final double SCAN_RADIUS = 100000.0;

    // Gobs within this many world units (5 tiles) of the last one a detour paid to reach are swept for free.
    private static final double CLUSTER_RADIUS = 5 * MCache.tilesz.x;

    // How close to stop when approaching a milestone anchor, without pathing onto its own tile.
    private static final double MILESTONE_APPROACH_DIST = 20.0;

    // mmap.sessloc often still describes the origin segment immediately after UseMilestone;
    // wait/retry until dest toWorldCoord is usable before regenerating walking sections.
    static final long DEST_MAP_READY_TIMEOUT_MS = 8_000;
    static final long DEST_MAP_READY_POLL_MS = 200;

    static Coord2d resolveSectionWalkTarget(ForagerSection section, ForagerWaypoint toWp, MiniMap.Location sessloc) {
        if (section == null) {
            return null;
        }
        if (section.isLastInGap && sessloc != null && toWp != null) {
            Coord2d freshEnd = toWp.toWorldCoord(sessloc);
            if (freshEnd != null) {
                return freshEnd;
            }
        }
        return section.endPoint;
    }

    static Coord2d resolveMilestoneWalkTarget(Gob milestoneGob, Coord2d playerRc, Coord2d sectionEnd) {
        if (milestoneGob == null) {
            return sectionEnd;
        }
        if (playerRc == null) {
            return milestoneGob.rc;
        }
        Coord2d away = playerRc.sub(milestoneGob.rc);
        double dist = away.dist(Coord2d.z);
        if (dist > 0.01) {
            return milestoneGob.rc.add(away.mul(MILESTONE_APPROACH_DIST / dist));
        }
        return sectionEnd;
    }

    static int nextSectionIndexAfterTeleport(ForagerPath path, int completedWaypointIndex) {
        if (path == null) {
            return 0;
        }
        for (int s = 0; s < path.getSectionCount(); s++) {
            ForagerSection regenerated = path.getSection(s);
            if (regenerated != null && regenerated.waypointIndex > completedWaypointIndex) {
                return s;
            }
        }
        return path.getSectionCount();
    }

    /** Returns the closest waypoint whose world position can be resolved on the current segment. */
    static int nearestResolvableWaypointIndex(ForagerPath path, MiniMap.Location sessloc, Coord2d playerWorldPos) {
        if (path == null || path.waypoints == null || path.waypoints.isEmpty()
                || sessloc == null || playerWorldPos == null) {
            return 0;
        }

        int nearestIndex = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < path.waypoints.size(); i++) {
            ForagerWaypoint waypoint = path.waypoints.get(i);
            if (waypoint == null) {
                continue;
            }
            Coord2d waypointWorldPos = waypoint.toWorldCoord(sessloc);
            if (waypointWorldPos == null) {
                continue;
            }
            double distance = playerWorldPos.dist(waypointWorldPos);
            // Strictly closer preserves the earlier route position on ties.
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    /** Finds the first section that covers the chosen waypoint's outgoing gap. */
    static int firstSectionIndexAtOrAfterWaypoint(ForagerPath path, int waypointIndex) {
        if (path == null) {
            return 0;
        }
        for (int sectionIndex = 0; sectionIndex < path.getSectionCount(); sectionIndex++) {
            ForagerSection section = path.getSection(sectionIndex);
            if (section != null && section.waypointIndex >= waypointIndex) {
                return sectionIndex;
            }
        }
        return path.getSectionCount();
    }

    @FunctionalInterface
    interface InterruptibleWait {
        void await() throws InterruptedException;
    }

    static int destMapReadyAttempts() {
        return (int) (DEST_MAP_READY_TIMEOUT_MS / DEST_MAP_READY_POLL_MS) + 1;
    }

    static Coord2d destinationWorldCoord(ForagerWaypoint dest, MiniMap.Location sessloc) {
        if (dest == null || sessloc == null) {
            return null;
        }
        return dest.toWorldCoord(sessloc);
    }

    /**
     * Bounded poll for dest session location after a cross-segment teleport.
     * InterruptedException is not swallowed. A still-null coordinate is not a wrong-place landing.
     */
    static Coord2d waitForDestinationWorldCoord(
            ForagerWaypoint dest,
            java.util.function.Supplier<MiniMap.Location> sessloc,
            int maxAttempts,
            InterruptibleWait wait) throws InterruptedException {
        int attempts = Math.max(1, maxAttempts);
        Coord2d world = destinationWorldCoord(dest, sessloc == null ? null : sessloc.get());
        for (int i = 1; world == null && i < attempts; i++) {
            if (wait != null) {
                wait.await();
            }
            world = destinationWorldCoord(dest, sessloc == null ? null : sessloc.get());
        }
        return world;
    }

    /** Stop the route; never a hearth recovery. Unresolved dest coords are not WRONG_PLACE. */
    static Results resultAfterUnresolvedDestinationMap() {
        return Results.FAIL();
    }

    /**
     * After UseMilestone SUCCESS, dest coords may still be catching up. Wait first, then
     * re-classify against the current player position. Destination steps and section regen
     * run only on Arrival.OK.
     */
    static final class MilestoneContinuation {
        final UseMilestone.Arrival arrival;
        final boolean runDestinationSteps;
        final boolean regenerateSections;
        final Results stop;

        MilestoneContinuation(UseMilestone.Arrival arrival, boolean runDestinationSteps,
                              boolean regenerateSections, Results stop) {
            this.arrival = arrival;
            this.runDestinationSteps = runDestinationSteps;
            this.regenerateSections = regenerateSections;
            this.stop = stop;
        }
    }

    static MilestoneContinuation continuationAfterMilestoneArrival(Coord2d destWorld, Coord2d playerRc) {
        UseMilestone.Arrival arrival = UseMilestone.classifyArrival(
                destWorld, playerRc, UseMilestone.wrongLocationTolerance());
        if (arrival == UseMilestone.Arrival.OK) {
            return new MilestoneContinuation(arrival, true, true, null);
        }
        if (arrival == UseMilestone.Arrival.WRONG_PLACE) {
            return new MilestoneContinuation(arrival, false, false,
                    UseMilestone.resultAfterConfirmedWrongPlace(Results.SUCCESS()));
        }
        return new MilestoneContinuation(arrival, false, false, resultAfterUnresolvedDestinationMap());
    }

    /** Non-success start PathFinder must not enter the section loop from the wrong tile. */
    static boolean shouldContinueAfterInitialPathFinder(Results startResult) {
        return startResult != null && startResult.IsSuccess();
    }

    /** The preset's waterMode toggle OR-ed with live coracle-mount state, so a route with a coracle leg doesn't need waterMode set for its whole length. */
    private boolean effectiveWaterMode(NGameUI gui, NForagerProp.PresetData preset) {
        boolean baseWaterMode = guardingProfile != null ? guardingProfile.waterMode : preset.waterMode;
        return baseWaterMode || CoracleBot.isPlayerInCoracle(gui);
    }

    /** Sorts lower Priority first (checked/collected before anything else in range), unset (-1)
     *  last; ties (including every unset pair) broken by distance ascending. */
    private int priorityRank(int priority) {
        return priority < 0 ? Integer.MAX_VALUE : priority;
    }

    /** True if there's at least one pickup action (PICK/FLOWER_ACTION/RIGHT_CLICK - not
     *  CHAT_NOTIFY, a separate scan-and-notify mechanism unrelated to Maintain) and every one of
     *  them has a Maintain cap set and already met, i.e. there's genuinely nothing left this run
     *  could collect. A preset with no pickup actions at all (e.g. used purely for waypoint steps
     *  or chat-notify scanning) never counts as "nothing to look for" here - that's a different,
     *  valid use case. Always false if Maintain limits are ignored, or if any action has no cap at
     *  all (unset always means "keep collecting"). */
    private boolean nothingLeftToPickUp(NGameUI gui, NForagerProp.PresetData preset) throws InterruptedException {
        if (preset.ignoreMaintainLimits) return false;
        boolean sawPickupAction = false;
        for (ForagerAction action : preset.actions) {
            if (action.actionType == ForagerAction.ActionType.CHAT_NOTIFY) continue;
            sawPickupAction = true;
            if (action.maintainQuantity < 0) return false;
            int areaStock = (action.sourceItemName != null) ? maintainAreaStock.getOrDefault(action.sourceItemName, 0) : 0;
            int carried = (action.sourceItemResource != null) ? countByResource(gui, action.sourceItemResource) : 0;
            if (areaStock + carried < action.maintainQuantity) return false;
        }
        return sawPickupAction;
    }

    /** Nearest unprocessed, constraint-passing gob (exclusion zone/leash/cliff/Maintain) matching any of the preset's actions within radius, preferring lower Priority actions first. */
    private Pair<Gob, ForagerAction> findNearestActionableGob(NGameUI gui, Coord2d from, java.util.List<ForagerAction> actions,
                                                               Coord2d leashAnchor, boolean ignoreMaintainLimits, boolean waterMode,
                                                               RouteLookahead lookahead, Coord2d clusterEntry) throws InterruptedException {
        MiniMap.Location sessloc = (gui.mmap != null) ? gui.mmap.sessloc : null;
        MCache map = (gui.map != null && gui.map.glob != null) ? gui.map.glob.map : null;
        Coord2d searchCenter = clusterEntry != null ? clusterEntry : from;
        double searchRadius = clusterEntry != null ? CLUSTER_RADIUS : SCAN_RADIUS;
        RouteLookahead leaveForLater = clusterEntry != null ? null : lookahead;
        List<PathFinder.AvoidZone> activeZones = avoidZones != null ? avoidZones.get() : null;

        List<Pair<Gob, ForagerAction>> candidates = new ArrayList<>();
        Map<Long, Double> distByGobId = new HashMap<>();
        for (ForagerAction action : actions) {
            if (action.actionType == ForagerAction.ActionType.CHAT_NOTIFY) continue;
            if (!ignoreMaintainLimits && action.maintainQuantity >= 0) {
                int areaStock = (action.sourceItemName != null) ? maintainAreaStock.getOrDefault(action.sourceItemName, 0) : 0;
                int carried = (action.sourceItemResource != null) ? countByResource(gui, action.sourceItemResource) : 0;
                if (areaStock + carried >= action.maintainQuantity) {
                    continue;
                }
            }
            for (Gob gob : Finder.findGobs(searchCenter, action.toNAlias(), null, searchRadius)) {
                if (processedGobs.contains(gob.id)) continue;
                if (activeZones != null && PathFinder.AvoidZone.anyContains(activeZones, gob.rc)) continue;
                if (routeConstraints.isGobExcluded(sessloc, gob)) continue;
                if (!routeConstraints.withinLeash(leashAnchor, gob.rc)) continue;
                // While in water mode (mounted in a coracle), a land-bound gob is structurally
                // unreachable without dismounting first - skip it rather than waste a detour
                // attempt PathFinder can never actually complete.
                if (waterMode && map != null && !isOnOrNearWater(map, gob.rc)) continue;
                if (leaveForLater != null && leaveForLater.leaveForLaterStop(gob, map, sessloc, waterMode)) continue;
                candidates.add(new Pair<>(gob, action));
                distByGobId.put(gob.id, from.dist(gob.rc));
            }
        }
        candidates.sort((a, b) -> {
            int byPriority = Integer.compare(priorityRank(a.b.priority), priorityRank(b.b.priority));
            if (byPriority != 0) return byPriority;
            return Double.compare(distByGobId.get(a.a.id), distByGobId.get(b.a.id));
        });

        boolean ignoreBats = guardingProfile != null && guardingProfile.ignoreBats;
        for (Pair<Gob, ForagerAction> candidate : candidates) {
            if (map != null && routeConstraints.cliffCorridorBlocked(map, from, candidate.a.rc)) continue;
            if (map != null && routeConstraints.landCorridorBlocked(map, from, candidate.a.rc, waterMode)) continue;
            if (routeConstraints.corridorExcluded(sessloc, from, candidate.a.rc)) continue;
            if (activeZones == null && routeConstraints.dangerousAnimalNearCorridor(from, candidate.a.rc, ignoreBats)) continue;
            return candidate;
        }
        return null;
    }

    /** Whether target's own tile is water a coracle could actually reach - same tileset check NPFMap's water-mode grid and CoracleBot use; an unstreamed tile is treated as reachable rather than guessed at. */
    private boolean isOnOrNearWater(MCache map, Coord2d target) {
        Coord tc = target.div(MCache.tilesz).floor();
        try {
            return nurgling.pf.NPFMap.isValidWaterTileName(map.tilesetname(map.gettile(tc)));
        } catch (Loading l) {
            return true;
        }
    }

    /** Counts inventory items by underlying resource (not display name, which varies by growth stage) for Maintain. */
    private int countByResource(NGameUI gui, String resource) throws InterruptedException {
        return AreaStock.countByResource(gui.getInventory(), resource);
    }

    /** Resolves each Maintain-configured action's area-stock baseline once, summing across every visible area with the item as "Put". */
    private Map<String, Integer> resolveMaintainAreaStock(NGameUI gui, NForagerProp.PresetData preset) throws InterruptedException {
        Map<String, Integer> stock = new HashMap<>();
        if (gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return stock;
        }
        for (ForagerAction action : preset.actions) {
            if (action.maintainQuantity < 0 || action.sourceItemName == null || action.sourceItemResource == null) continue;

            int total = 0;
            int areasChecked = 0;
            // gui.map.nols filtered by !isDisabled(), not area.isVisible() (which requires the area's grid already loaded).
            for (Integer id : gui.map.nols.keySet()) {
                if (id <= 0) continue;
                NArea area = gui.map.glob.map.areas.get(id);
                if (area == null || area.isDisabled()) continue;
                if (area.containOut(action.sourceItemName)) {
                    areasChecked++;
                    total += AreaStock.countItemsInAreaContainers(gui, area, action.sourceItemResource);
                }
            }
            // Silent when no Put area is configured (the common "cap my carried inventory" case).
            if (areasChecked > 0) {
                gui.msg("Forager Maintain: \"" + action.sourceItemName + "\" - " + total +
                        " already stored (target " + action.maintainQuantity + ")");
            }
            stock.put(action.sourceItemName, total);
        }
        return stock;
    }

    /** Repeatedly walks to the nearest unprocessed actionable gob, recording breadcrumbs for {@link #returnToPathViaBreadcrumbs}, until none remain or inventory fills. */
    private static final class Detour {
        final ArrayList<Coord2d> breadcrumbs = new ArrayList<>();
        final DetourBranchBudget budget;
        final Coord2d anchor;
        final RouteLookahead lookahead;
        Coord2d clusterEntry;

        Detour(DetourBranchBudget budget, Coord2d anchor, RouteLookahead lookahead) {
            this.budget = budget;
            this.anchor = anchor;
            this.lookahead = lookahead;
        }
    }

    /** Repeatedly walks to unprocessed actionable gobs, returning via breadcrumbs after the detour. */
    private void collectNearbyActionableGobs(NGameUI gui, NForagerProp.PresetData preset, ForagerPath path,
                                             int firstStopSection) throws InterruptedException {
        Gob player = NUtils.player();
        Coord2d anchor = player != null ? player.rc : null;
        Detour detour = new Detour(new DetourBranchBudget(routeConstraints.maxBranches(),
                routeConstraints.maxBranchDistanceTiles()), anchor, lookaheadFrom(gui, path, firstStopSection, anchor));
        gui.activeBotDetourTrail = detour.breadcrumbs;
        boolean interrupted = false;
        try {
            collectUntilExhausted(gui, preset, detour);
        } catch (InterruptedException e) {
            interrupted = true;
            throw e;
        } finally {
            if (interrupted) {
                gui.activeBotDetourTrail = null;
                gui.activeBotDetourTarget = null;
            } else {
                returnToPathViaBreadcrumbs(gui, preset, detour);
                gui.activeBotDetourTrail = null;
                gui.activeBotDetourTarget = null;
            }
        }
    }

    /** Grabs everything actionable in range, rescanning after each pickup, until nothing's found or inventory fills; shared by the outbound pass and the return walk. */
    private void collectUntilExhausted(NGameUI gui, NForagerProp.PresetData preset, Detour detour) throws InterruptedException {
        while (true) {
            if (isInventoryFull(gui)) return;
            checkStamina(gui);
            if (sweepCluster(gui, preset, detour)) continue;
            if (!detour.budget.canBranch()) return;

            Gob player = NUtils.player();
            if (player == null) return;

            Pair<Gob, ForagerAction> nearest = findNearestActionableGob(gui, player.rc, preset.actions, detour.anchor,
                    preset.ignoreMaintainLimits, effectiveWaterMode(gui, preset), detour.lookahead, null);
            if (nearest == null) {
                gui.activeBotDetourTarget = null;
                return;
            }
            gui.activeBotDetourTarget = nearest.a.rc;

            if (player.rc.dist(nearest.a.rc) > MAX_HOP_DISTANCE) {
                // Too far for one PathFinder call - hop toward it; stop the whole pass if a hop fails rather than retrying forever.
                detour.clusterEntry = null;
                if (!walkInHops(gui, preset, nearest.a.rc, detour, null)) return;
                continue;
            }

            payAndPick(gui, preset, detour, player.rc, nearest);
        }
    }

    /** Sweeps remaining actionable gobs near the paid-for cluster entry without consuming another branch. */
    private boolean sweepCluster(NGameUI gui, NForagerProp.PresetData preset, Detour detour) throws InterruptedException {
        if (detour.clusterEntry == null) return false;
        Gob player = NUtils.player();
        if (player == null) return false;
        Pair<Gob, ForagerAction> next = findNearestActionableGob(gui, player.rc, preset.actions, detour.anchor,
                preset.ignoreMaintainLimits, effectiveWaterMode(gui, preset), null, detour.clusterEntry);
        if (next == null) return false;
        gui.activeBotDetourTarget = next.a.rc;
        detour.breadcrumbs.add(player.rc);
        performGobAction(gui, next.b, next.a, preset);
        return true;
    }

    private void payAndPick(NGameUI gui, NForagerProp.PresetData preset, Detour detour, Coord2d from,
                            Pair<Gob, ForagerAction> target) throws InterruptedException {
        detour.budget.spend(from.dist(target.a.rc));
        detour.breadcrumbs.add(from);
        detour.clusterEntry = target.a.rc;
        performGobAction(gui, target.b, target.a, preset);
    }

    /** Walks toward target in hops, collecting closer gobs and keeping a fixed route anchor. */
    private boolean walkInHops(NGameUI gui, NForagerProp.PresetData preset, Coord2d target, Detour detour,
                               RouteLookahead routeLookahead) throws InterruptedException {
        boolean detourEpisode = detour != null;
        Coord2d leashAnchor;
        RouteLookahead lookahead;
        if (detourEpisode) {
            leashAnchor = detour.anchor;
            lookahead = detour.lookahead;
        } else {
            // Anchor held fixed for this whole call, same as the detour-episode case - re-deriving
            // it from the current position every hop let repeated hops drift arbitrarily far from
            // the route, since each hop's leash check only ever bounded the next hop from wherever
            // the last one left off.
            Gob startPlayer = NUtils.player();
            if (startPlayer == null) return false;
            leashAnchor = startPlayer.rc;
            lookahead = routeLookahead;
        }
        while (true) {
            if (isInventoryFull(gui)) return false;
            checkStamina(gui);
            if (detourEpisode) {
                if (sweepCluster(gui, preset, detour)) continue;
                if (!detour.budget.canBranch()) return false;
            }

            Gob player = NUtils.player();
            if (player == null) return false;

            double remaining = player.rc.dist(target);
            if (remaining <= MAX_HOP_DISTANCE) return true;

            Pair<Gob, ForagerAction> nearest = findNearestActionableGob(gui, player.rc, preset.actions, leashAnchor,
                    preset.ignoreMaintainLimits, effectiveWaterMode(gui, preset), lookahead, null);
            if (nearest != null && player.rc.dist(nearest.a.rc) < remaining) {
                gui.activeBotDetourTarget = nearest.a.rc;
                if (detourEpisode) {
                    payAndPick(gui, preset, detour, player.rc, nearest);
                } else {
                    performGobAction(gui, nearest.b, nearest.a, preset);
                    gui.activeBotDetourTarget = null;
                }
                continue;
            }
            if (detourEpisode) {
                gui.activeBotDetourTarget = target;
            }

            Coord2d waypoint = outsideDangerZones(player.rc.add(target.sub(player.rc).norm(MAX_HOP_DISTANCE)));
            if (detourEpisode) {
                detour.budget.spend(player.rc.dist(waypoint));
                detour.breadcrumbs.add(player.rc);
            }
            if (!walk(gui, preset, new PathFinder(waypoint), !detourEpisode).IsSuccess()) {
                unstickAtCurrentPosition(gui, preset);
                return false;
            }
        }
    }

    /** Best-effort recovery from a failed hop that may have wedged the character against a gob's hitbox. */
    private void unstickAtCurrentPosition(NGameUI gui, NForagerProp.PresetData preset) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) return;
        PathFinder unstick = new PathFinder(player.rc);
        unstick.waterMode = effectiveWaterMode(gui, preset);
        unstick.run(gui);
    }

    /** Retraces the breadcrumb trail home most-recent-first, sweeping via collectUntilExhausted before each hop; keeps going even once inventory is full. */
    private void returnToPathViaBreadcrumbs(NGameUI gui, NForagerProp.PresetData preset, Detour detour) throws InterruptedException {
        ArrayList<Coord2d> breadcrumbs = detour.breadcrumbs;
        while (!breadcrumbs.isEmpty()) {
            collectUntilExhausted(gui, preset, detour);
            if (breadcrumbs.isEmpty()) return;

            Gob player = NUtils.player();
            if (player == null) return;

            checkStamina(gui);
            int last = breadcrumbs.size() - 1;
            int oldestInReach = last;
            for (int k = 0; k < last; k++) {
                if (player.rc.dist(breadcrumbs.get(k)) <= MAX_HOP_DISTANCE) {
                    oldestInReach = k;
                    break;
                }
            }
            if (oldestInReach < last && hopTo(gui, preset, breadcrumbs.get(oldestInReach))) {
                breadcrumbs.subList(oldestInReach, breadcrumbs.size()).clear();
                continue;
            }
            if (!shouldDiscardBreadcrumbAfterFallback(hopTo(gui, preset, breadcrumbs.get(last)))) return;
            breadcrumbs.remove(last);
        }
    }

    private boolean hopTo(NGameUI gui, NForagerProp.PresetData preset, Coord2d pos) throws InterruptedException {
        gui.activeBotDetourTarget = pos;
        return walk(gui, preset, new PathFinder(pos), false).IsSuccess();
    }

    static boolean shouldDiscardBreadcrumbAfterFallback(boolean hopSucceeded) {
        return hopSucceeded;
    }

    private Results walk(NGameUI gui, NForagerProp.PresetData preset, PathFinder pf, boolean routeLeg) throws InterruptedException {
        if (avoidZones != null && !stepOutOfDangerZone(gui, preset)) {
            return Results.ERROR("Forager: couldn't leave a dangerous animal's avoidance zone");
        }
        pf.waterMode = effectiveWaterMode(gui, preset);
        pf.avoidZones = avoidZones;
        pf.learnedBlocks = stallSpots;
        Results result = pf.run(gui);
        if (routeLeg && pf.blockedByAvoidZones) {
            routeLegBlocked = true;
            gui.msg("Forager: a dangerous animal blocks the way to the next waypoint - skipping it");
        }
        return result;
    }

    private boolean stepOutOfDangerZone(NGameUI gui, NForagerProp.PresetData preset) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || avoidZones == null) return true;
        for (PathFinder.AvoidZone zone : avoidZones.get()) if (zone.contains(player.rc)) {
            Coord2d exit = outsideDangerZones(zone.pushOut(player.rc, MCache.tilesz.x));
            PathFinder back = new PathFinder(exit);
            back.waterMode = effectiveWaterMode(gui, preset);
            back.avoidZones = avoidZones;
            back.learnedBlocks = stallSpots;
            if (!back.run(gui).IsSuccess()) return false;
            Gob escaped = NUtils.player();
            return escaped != null && !PathFinder.AvoidZone.anyContains(avoidZones.get(), escaped.rc);
        }
        return true;
    }

    private Coord2d outsideDangerZones(Coord2d point) {
        if (avoidZones == null) return point;
        return outsideDangerZones(point, avoidZones.get());
    }

    static Coord2d outsideDangerZones(Coord2d point, List<PathFinder.AvoidZone> zones) {
        if (zones == null) return point;
        for (int pass = 0; pass < 4; pass++) {
            PathFinder.AvoidZone hit = null;
            for (PathFinder.AvoidZone zone : zones) if (zone.contains(point)) { hit = zone; break; }
            if (hit == null) break;
            point = hit.pushOut(point, MCache.tilesz.x);
        }
        return point;
    }

    /** Gathering stops ahead until the next milestone, which may switch map segments. */
    private RouteLookahead lookaheadFrom(NGameUI gui, ForagerPath path, int firstSection, Coord2d anchor) {
        MiniMap.Location sessloc = gui.mmap != null ? gui.mmap.sessloc : null;
        ArrayList<Coord2d> stops = new ArrayList<>();
        for (int s = firstSection; s < path.getSectionCount(); s++) {
            ForagerSection section = path.getSection(s);
            if (section == null) continue;
            ForagerWaypoint toWp = path.waypoints.get(section.waypointIndex + 1);
            if (toWp.milestoneHash != null) break;
            Coord2d stop = resolveSectionWalkTarget(section, toWp, sessloc);
            if (stop != null && (stops.isEmpty() || stops.get(stops.size() - 1).dist(stop) > 0.5)) {
                stops.add(stop);
            }
        }
        return new RouteLookahead(stops, anchor, routeConstraints);
    }

    /** Walks to and performs one action on a single gob, marking it processed once done. */
    private void performGobAction(NGameUI gui, ForagerAction action, Gob gob,
                                   NForagerProp.PresetData preset) throws InterruptedException {
        switch (action.actionType) {
            case PICK: {
                // Marks processed either way - an unreachable gob (e.g. on land while mounted in
                // a coracle) would otherwise keep getting re-picked as "nearest" forever.
                processedGobs.add(gob.id);
                if (!walk(gui, preset, new PathFinder(gob), false).IsSuccess()) break;
                new SelectFlowerAction("Pick", gob).run(gui);
                NUtils.getUI().core.addTask(new nurgling.tasks.WaitGobRemoval(gob.id));
                break;
            }
            case FLOWER_ACTION: {
                processedGobs.add(gob.id);
                if (!walk(gui, preset, new PathFinder(gob), false).IsSuccess()) break;
                SelectFlowerAction flowerAction = new SelectFlowerAction(action.toActionNameCandidates(), gob);
                flowerAction.run(gui);
                confirmActionName(action, flowerAction.getMatchedOpt());
                NUtils.getUI().core.addTask(new nurgling.tasks.WaitPose(NUtils.player(), "gfx/borka/idle"));
                break;
            }
            case RIGHT_CLICK: {
                // For objects with no flower menu - just gives the interaction a brief moment to register before moving on.
                NUtils.setSpeed(2);
                try {
                    processedGobs.add(gob.id);
                    if (!walk(gui, preset, new PathFinder(gob), false).IsSuccess()) break;
                    NUtils.rclickGob(gob);
                    NUtils.getUI().core.addTask(new nurgling.tasks.WaitTicks(30));
                } finally {
                    NUtils.setSpeed(1);
                }
                break;
            }
            default:
                // CHAT_NOTIFY never reaches here - findNearestActionableGob excludes it.
                break;
        }
    }

    /** Once a flower menu confirms which candidate action string was correct, narrows and persists it so future runs don't re-guess. */
    private void confirmActionName(ForagerAction action, String matched) {
        if (matched == null || matched.equals(action.actionName) || forageProp == null) {
            return;
        }
        action.actionName = matched;
        NForagerProp.set(forageProp);
    }

    /** CHAT_NOTIFY is a one-shot scan-and-notify action, not per-gob walk-to-and-interact, so it keeps its own per-section scan. */
    private void processChatNotifyActions(NGameUI gui, ForagerSection section,
                                           java.util.List<ForagerAction> actions) throws InterruptedException {
        for (ForagerAction action : actions) {
            if (action.actionType != ForagerAction.ActionType.CHAT_NOTIFY) continue;

            ArrayList<Gob> gobs = Finder.findGobs(section.getCenterPoint(), action.toNAlias(), null, MAX_HOP_DISTANCE);
            gobs.removeIf(gob -> processedGobs.contains(gob.id));
            if (gobs.isEmpty()) continue;

            String message = String.format("Found %d %s objects!", gobs.size(), action.targetObjectPattern);

            if (action.notifyTarget == ForagerAction.NotifyTarget.DISCORD) {
                NDiscordNotification discordSettings = NDiscordNotification.get("general");
                if (discordSettings != null && discordSettings.webhookUrl != null && !discordSettings.webhookUrl.isEmpty()) {
                    gui.msgToDiscord(discordSettings, message);
                }
            } else if (action.notifyTarget == ForagerAction.NotifyTarget.CHAT) {
                if (action.chatChannelName != null && !action.chatChannelName.isEmpty()) {
                    ChatUI.Channel targetChannel = findChatChannelByName(gui, action.chatChannelName);
                    if (targetChannel != null && targetChannel instanceof ChatUI.EntryChannel) {
                        ((ChatUI.EntryChannel) targetChannel).send(message);
                    }
                }
            }

            for (Gob gob : gobs) {
                processedGobs.add(gob.id);
            }

            // Pause for 5 minutes (18000 frames at 60fps)
            NUtils.getUI().core.addTask(new nurgling.tasks.WaitTicks(18000));

            // Signal to stop the bot after pause
            throw new InterruptedException("CHAT_NOTIFY action triggered - stopping bot");
        }
    }

    /**
     * Performs one synchronous drink only between Forager's movement and collection actions.
     * AutoDrink's background loop stays paused by the bot's existing waitBot gate.
     */
    private void checkStamina(NGameUI gui) throws InterruptedException {
        ForagerDrinkPolicy.Settings settings = ForagerDrinkPolicy.settings(
                NConfig.get(NConfig.Key.autoDrinkThreshold),
                NConfig.get(NConfig.Key.autoDrinkTimeout));
        Number totalDrinkable = gui.drinkMeter == null ? null : gui.drinkMeter.getTotalDrinkable();
        boolean hasDrink = ForagerDrinkPolicy.mayHaveDrink(totalDrinkable);
        ForagerDrinkPolicy.Action action = drinkState.atCheckpoint(
                Boolean.TRUE.equals(NConfig.get(NConfig.Key.autoDrink)),
                NUtils.getStamina(), hasDrink, System.currentTimeMillis(), settings);
        if (action == ForagerDrinkPolicy.Action.NOTIFY_NO_DRINK) {
            gui.msg(L10n.get("forager.auto_drink.no_water"));
        } else if (action == ForagerDrinkPolicy.Action.DRINK) {
            Results result = new Drink(settings.target(), false).run(gui);
            drinkState.recordDrinkAttempt(result.IsSuccess(), NUtils.getStamina(),
                    System.currentTimeMillis(), settings);
        }
    }

    private boolean isInventoryFull(NGameUI gui) throws InterruptedException
    {

        if (gui.vhand != null) {
            return true;
        }

        if (gui.getInventory() != null) {
            return gui.getInventory().getFreeSpace() <= 4;
        }

        return false;
    }
    
    
    /** Runs a waypoint's attached steps; on failure dispatches onStepsFailAction. Returns true if the caller should return SUCCESS immediately. */
    private boolean runWaypointSteps(NGameUI gui, ForagerWaypoint wp) throws InterruptedException {
        if (wp.steps == null || wp.steps.isEmpty()) {
            return false;
        }
        Results stepsResult = ScenarioRunner.runSteps(gui, wp.steps);
        if (!stepsResult.IsSuccess()) {
            String failAction = ForagerWaypoint.normalizeOnStepsFailAction(wp.onStepsFailAction);
            gui.msg("Forager: waypoint steps failed (" + failAction + ")");
            if (ForagerWaypoint.stopsRouteOnStepsFail(failAction)) {
                if (ForagerWaypoint.performsGuardOutcomeOnStepsFail(failAction)) {
                    performSafetyAction(gui, failAction);
                }
                return true;
            }
        }
        return false;
    }

    /** Dispatches configured end-of-run safety action after the watcher has stopped. */
    private void performSafetyAction(NGameUI gui, String action) throws InterruptedException {
        String canonical = HEARTH_UNLOAD_HEARTH.equals(action) ? action
                : ForagerWaypoint.normalizeOnStepsFailAction(action);
        if ("nothing".equals(canonical)) return;
        stopGuardWatcher();
        if (HEARTH_UNLOAD_HEARTH.equals(canonical)) {
            hearthUnloadHearth(gui);
            return;
        }
        if (ForagerWaypoint.performsGuardOutcomeOnStepsFail(canonical)) {
            gui.msg("Forager: running safety action \"" + canonical + "\"");
            GuardOutcome.fromId(canonical).perform(gui);
            gui.msg("Forager: safety action \"" + canonical + "\" finished");
        }
    }

    /** A first successful hearth makes unload safe to attempt; the second hearth is mandatory even if unloading fails. */
    private void hearthUnloadHearth(NGameUI gui) throws InterruptedException {
        if (!GuardOutcome.travelHearth(gui).IsSuccess()) {
            gui.error("Forager: first hearth-firing failed; inventory was not unloaded");
            return;
        }
        Results unload = new FreeInventory2(new NContext(gui)).run(gui);
        if (!unload.IsSuccess()) {
            gui.msg("Forager: inventory unload failed; hearth-firing again");
        }
        if (shouldRunSecondHearth(true, unload.IsSuccess())) {
            GuardOutcome.travelHearth(gui);
        }
    }

    static boolean shouldRunSecondHearth(boolean firstHearthSucceeded, boolean unloadSucceeded) {
        return firstHearthSucceeded;
    }

    private void stopGuardWatcher() throws InterruptedException {
        Thread watcher = threatWatcher;
        threatWatcher = null;
        if (watcher != null) {
            watcher.interrupt();
            watcher.join(1000);
        }
    }
    
    /** Resolves the preset's own GuardingProfile, falling back to prop-level then GuardingProfile.withDefaults() rather than erroring. */
    private GuardingProfile resolveGuardingProfile(NForagerProp prop, NForagerProp.PresetData preset) {
        if (prop.guardingProfiles == null || prop.guardingProfiles.isEmpty()) {
            return GuardingProfile.withDefaults();
        }
        String name = preset.guardingProfileName != null ? preset.guardingProfileName : prop.currentGuardingProfile;
        GuardingProfile p = prop.guardingProfiles.get(name);
        if (p != null) {
            return p;
        }
        return prop.guardingProfiles.values().iterator().next();
    }

    private Guard resolveDangerGuard(GuardingProfile profile) {
        if (profile == null) return null;
        for (GuardEntry entry : profile.inflightGuards)
            if ("dangerous_animal".equals(entry.guardId)) return entry.toGuard();
        return null;
    }

    private List<Guard> buildGuards(List<GuardEntry> entries) {
        List<Guard> guards = new ArrayList<>();
        for (GuardEntry entry : entries) {
            Guard guard = entry.toGuard();
            if (guard != null) {
                guards.add(guard);
            }
        }
        return guards;
    }

    /** Background thread polling in-flight guards independent of the bot thread; records the firing guard and interrupts the bot thread, but doesn't perform its outcome itself. */
    private Thread startGuardWatcher(NGameUI gui, GuardingProfile profile, Thread botThread) {
        List<Guard> guards = buildGuards(profile.inflightGuards);
        GuardContext ctx = new GuardContext(gui, profile.ignoreBats);
        // Bind the calling thread's NUI here too, mirroring BotExecutor.runAsync, so NConfig reads use this session's config.
        NUI boundUI = NUtils.getUI();
        Thread watcher = new Thread(() -> {
            if (boundUI != null) {
                nurgling.sessions.ThreadLocalUI.set(boundUI);
            }
            try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    for (Guard guard : guards) {
                        if (guard.trigger.check(ctx)) {
                            // Only interrupt if this watcher actually won the claim - guards
                            // against a redundant interrupt if something else already has one pending.
                            if (pendingGuard.compareAndSet(null, guard)) {
                                gui.msg("Forager: " + guard.trigger.describe() + " (" + guard.outcome.id() + ")");
                                botThread.interrupt();
                            }
                            return;
                        }
                    }
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    // Don't let one bad read kill the watcher for the rest of the run.
                }
            }
            } finally {
                if (boundUI != null) {
                    nurgling.sessions.ThreadLocalUI.clear();
                }
            }
        }, "ForagerGuardWatcher");
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    private Gob findGobNear(Coord2d pos, double radius) {
        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                if (!(gob instanceof OCache.Virtual || gob.attr.isEmpty() || gob.getClass().getName().contains("GlobEffector"))) {
                    if (gob.id != NUtils.playerID() && gob.rc.dist(pos) <= radius && !(gob instanceof MapView.Plob)
                            && gob.id > 0 && !GateDetector.isGate(gob)) {
                        return gob;
                    }
                }
            }
        }
        return null;
    }
    
    private ChatUI.Channel findChatChannelByName(NGameUI gui, String channelName) {
        if (gui.chat == null) return null;
        
        for (Widget w = gui.chat.child; w != null; w = w.next) {
            if (w instanceof ChatUI.Channel) {
                ChatUI.Channel chan = (ChatUI.Channel) w;
                if (chan.name().equalsIgnoreCase(channelName)) {
                    return chan;
                }
            }
        }
        return null;
    }
}
