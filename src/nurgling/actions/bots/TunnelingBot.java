package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.tasks.*;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.TunnelingDialog;
import nurgling.widgets.TunnelingDialog.Direction;
import nurgling.widgets.TunnelingDialog.TunnelSide;
import nurgling.widgets.TunnelingDialog.SupportType;
import nurgling.widgets.TunnelingDialog.WingOption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class TunnelingBot implements Action {
    private OreWatch oreWatch;

    // All support types that can be used as a starting point
    private static final NAlias ALL_SUPPORTS = new NAlias(
            "minebeam", "column", "towercap", "ladder", "minesupport", "naturalminesupport"
    );

    // Tiles that need mining
    private static final NAlias MINEABLE_TILES = new NAlias("rock", "tiles/cave");

    // Tile size in world units
    private static final double TILE_SIZE = 11.0;
    private static final NAlias ROAD_SPOT_IGNORED = new NAlias("bumlings", "terobjs/items");
    private static final int ROAD_ATTEMPTS = 3;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Show configuration dialog
        int[] directionRef = new int[]{0};
        int[] tunnelSideRef = new int[]{0};
        int[] supportTypeRef = new int[]{0};
        int[] wingOptionRef = new int[]{0};
        int[] wingSideRef = new int[]{0};
        int[] maxLateralRef = new int[]{5};
        boolean[] confirmRef = new boolean[]{false};
        boolean[] cancelRef = new boolean[]{false};
        boolean[] doubleTunnelRef = new boolean[]{false};
        boolean[] stopAtOreRef = new boolean[]{false};
        Set<String> stopOresRef = new HashSet<>();
        boolean[] roadRef = new boolean[]{false};
        int[] roadSpacingRef = new int[]{TunnelRoad.MAX_SPACING_TILES};

        TunnelingDialog dialog = new TunnelingDialog();
        dialog.setReferences(directionRef, tunnelSideRef, supportTypeRef, wingOptionRef, wingSideRef, maxLateralRef, confirmRef, cancelRef, doubleTunnelRef);
        boolean[] centreLineRef = new boolean[]{false};
        dialog.setFrameReferences(centreLineRef);
        dialog.setOreReferences(stopAtOreRef, stopOresRef);
        dialog.setRoadReferences(roadRef, roadSpacingRef);
        NUtils.addCentered(gui, dialog);

        // Wait for user input
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return confirmRef[0] || cancelRef[0];
            }
        });

        dialog.destroy();

        if (cancelRef[0]) {
            return Results.SUCCESS();
        }
        oreWatch = stopAtOreRef[0] ? new OreWatch(stopOresRef) : null;

        // Get configuration
        Direction direction = TunnelingDialog.getDirection(directionRef[0]);
        SupportType supportType = TunnelingDialog.getSupportType(supportTypeRef[0]);
        TunnelRoad road = roadRef[0] && supportType.supportsRoad()
                ? new TunnelRoad(direction, roadSpacingRef[0]) : null;

        // Dispatch to MinesweeperMiner if no support selected
        if (supportType == SupportType.NONE) {
            return new MinesweeperMiner(direction, maxLateralRef[0], oreWatch).run(gui);
        }

        // Tunnel frames are dug through rather than beside, so they get their own loop
        if (supportType.isFrame()) {
            return new FrameTunneler(direction, supportType,
                    TunnelingDialog.getTunnelSide(directionRef[0], tunnelSideRef[0]), centreLineRef[0], oreWatch, road).run(gui);
        }

        TunnelSide tunnelSide = TunnelingDialog.getTunnelSide(directionRef[0], tunnelSideRef[0]);
        WingOption wingOption = TunnelingDialog.getWingOption(directionRef[0], wingOptionRef[0]);
        TunnelSide wingSide = TunnelingDialog.getWingSide(directionRef[0], wingSideRef[0]);
        boolean doubleTunnel = doubleTunnelRef[0];

        gui.msg("Tunneling: " + direction.name + ", Side: " + tunnelSide.name +
                ", Support: " + supportType.menuName + ", Wings: " + wingOption.name + ", Wing Side: " + wingSide.name +
                (doubleTunnel ? ", x2 Tunnel" : ""));

        // Find nearest support of ANY type as starting point
        Gob startSupport = findNearestSupport(gui);
        if (startSupport == null) {
            return Results.ERROR("No mine support found nearby. Stand near a support to start.");
        }

        gui.msg("Starting from support at: " + startSupport.rc);
        if (road != null) {
            Results started = road.start(gui);
            if (!started.IsSuccess()) return started;
        }

        // Main tunneling loop
        Coord2d currentSupportPos = startSupport.rc;
        int iteration = 0;

        while (true) {
            iteration++;
            gui.msg("Tunneling iteration " + iteration);

            // Calculate next support position (on the support axis, not tunnel axis)
            int placementDistance = supportType.radius;
            Coord2d dirVector = new Coord2d(direction.dx, direction.dy);
            Coord2d nextSupportPos = currentSupportPos.add(dirVector.mul(placementDistance));

            // Snap to tile grid for proper alignment
            Coord nextSupportTile = nextSupportPos.div(tilesz).floor();
            nextSupportPos = tileCenter(nextSupportTile);

            // Calculate tunnel position (offset from support axis)
            // The tunnel runs parallel to the support line, but offset by tunnelSide
            Coord2d tunnelOffset = new Coord2d(tunnelSide.dx * TILE_SIZE, tunnelSide.dy * TILE_SIZE);

            // Mine the main tunnel path (offset from support line)
            Results mineResult = mineTunnelPath(gui, currentSupportPos, nextSupportPos, direction, tunnelOffset);
            if (!mineResult.IsSuccess()) {
                return mineResult;
            }

            if (doubleTunnel) {
                Coord2d tunnelOffset2 = new Coord2d(tunnelSide.dx * TILE_SIZE * 2, tunnelSide.dy * TILE_SIZE * 2);
                Results mineResult2 = mineTunnelPath(gui, currentSupportPos, nextSupportPos, direction, tunnelOffset2);
                if (!mineResult2.IsSuccess()) {
                    return mineResult2;
                }
            }

            // Mine the tile where support will be placed
            // This is on the support axis, not the tunnel axis
            Coord nextSupportTileCoord = nextSupportPos.div(tilesz).floor();
            Results supportTileResult = mineTileIfNeeded(gui, nextSupportTileCoord);
            if (!supportTileResult.IsSuccess()) {
                return supportTileResult;
            }

            handleBumlings(gui);

            // Place the new support
            Results oreStop = checkOre(gui, nextSupportTileCoord);
            if (oreStop != null) return oreStop;
            Results buildResult = placeSupport(gui, nextSupportPos, supportType);
            if (!buildResult.IsSuccess()) {
                return buildResult;
            }

            // Handle any bumlings (dropped stones)
            handleBumlings(gui);

            // Dig wings based on wing option
            if (wingOption != WingOption.NONE) {
                // First, mine connector from tunnel to wing start area
                Results connectorResult = mineWingConnector(gui, nextSupportPos, direction, tunnelSide, wingSide);
                if (!connectorResult.IsSuccess()) {
                    if (oreWatch != null && oreWatch.found() != null) return connectorResult;
                    gui.msg("Wing connector mining had issues, skipping wings");
                } else {
                    Results wingResult = mineWings(gui, nextSupportPos, direction, wingOption, wingSide, supportType.radius);
                    if (!wingResult.IsSuccess()) {
                        if (oreWatch != null && oreWatch.found() != null) return wingResult;
                        gui.msg("Wing mining had issues, continuing anyway");
                        // Continue anyway, wings are optional
                    }
                }
            }

            if (road != null) {
                Results roadResult = layRoad(gui, road, nextSupportPos, direction, supportType, tunnelSide);
                if (!roadResult.IsSuccess()) return roadResult;
            }

            // Update current support position for next iteration
            currentSupportPos = nextSupportPos;

            // Check for stop conditions
            if (!canContinueTunneling(gui, currentSupportPos, direction, supportType.radius)) {
                gui.msg("Cannot continue tunneling - obstacle or map edge detected");
                break;
            }
        }

        if (road != null) {
            Coord last = roadSpot(currentSupportPos.div(tilesz).floor(), direction, tunnelSide);
            if (last == null) return Results.ERROR("Road: no free milestone spot at the tunnel end");
            if (road.worthFinishing(last)) return buildRoadPost(gui, road, last, tunnelSide);
        }
        return Results.SUCCESS();
    }

    private Coord2d nextRoadSupport(Coord2d current, Direction direction, SupportType type) {
        Coord2d next = current.add(new Coord2d(direction.dx, direction.dy).mul(type.radius));
        return tileCenter(next.div(tilesz).floor());
    }

    private Coord roadSpot(Coord supportTile, Direction direction, TunnelSide side) {
        Coord corridor = supportTile.add(side.dx, side.dy);
        for (int step : new int[]{0, -1, 1}) {
            Coord tile = corridor.add(direction.dx * step, direction.dy * step);
            boolean blocked = false;
            for (Gob gob : Finder.findGobs(tile))
                if (gob.ngob == null || gob.ngob.name == null
                        || !NParser.checkName(gob.ngob.name, ROAD_SPOT_IGNORED)) blocked = true;
            if (!blocked) return tile;
        }
        return null;
    }

    private Results layRoad(NGameUI gui, TunnelRoad road, Coord2d supportPos, Direction direction,
                            SupportType type, TunnelSide side) throws InterruptedException {
        Coord spot = roadSpot(supportPos.div(tilesz).floor(), direction, side);
        Coord next = roadSpot(nextRoadSupport(supportPos, direction, type).div(tilesz).floor(), direction, side);
        if (spot == null || next == null)
            return Results.ERROR("Road: no free milestone spot in the next tunnel stretch");
        if (!road.due(spot, next)) return Results.SUCCESS();
        return buildRoadPost(gui, road, spot, side);
    }

    private Results buildRoadPost(NGameUI gui, TunnelRoad road, Coord spot, TunnelSide side)
            throws InterruptedException {
        for (int i = 0; !road.reaches(spot).IsSuccess() && i < TunnelRoad.MAX_CATCHUP_POSTS; i++) {
            Coord step = road.stepToward(spot);
            double before = road.distanceTo(spot);
            if (step.equals(spot)) return Results.ERROR("Road: catch-up milestone cannot advance");
            Results added = buildOneRoadPost(gui, road, step, side);
            if (!added.IsSuccess()) return added;
            if (road.distanceTo(spot) >= before - TILE_SIZE * 0.25)
                return Results.ERROR("Road: catch-up milestone made no progress");
        }
        if (!road.reaches(spot).IsSuccess())
            return Results.ERROR("Road: too many catch-up milestones required");
        return buildOneRoadPost(gui, road, spot, side);
    }

    private Results buildOneRoadPost(NGameUI gui, TunnelRoad road, Coord spot, TunnelSide side)
            throws InterruptedException {
        Results hand = ExtendRoad.emptyHand(gui);
        if (!hand.IsSuccess()) return hand;
        Coord outward = Coord.of(side.dx, side.dy);
        Results mined = mineRoadTile(gui, spot.add(outward));
        if (!mined.IsSuccess()) return mined;
        mined = mineRoadTile(gui, spot);
        if (!mined.IsSuccess()) return mined;
        for (int attempt = 0; attempt < ROAD_ATTEMPTS; attempt++) {
            Results oreStop = checkOre(gui, spot);
            if (oreStop != null) return oreStop;
            oreStop = checkOre(gui, spot.add(outward));
            if (oreStop != null) return oreStop;
            hand = ExtendRoad.emptyHand(gui);
            if (!hand.IsSuccess()) return hand;
            Results chipped = handleBumlings(gui, true);
            if (!chipped.IsSuccess()) return chipped;
            Results built = road.extendTo(gui, spot, outward);
            if (built.IsSuccess() || road.rockInTheWay().isEmpty()) return built;
            Results cleared = clearRoadRock(gui, road.rockInTheWay());
            if (!cleared.IsSuccess()) return cleared;
        }
        return Results.ERROR("Road: milestone placement still blocked after retries");
    }

    private Results mineRoadTile(NGameUI gui, Coord tile) throws InterruptedException {
        Results oreStop = checkOre(gui, tile);
        if (oreStop != null) return oreStop;
        Results hand = ExtendRoad.emptyHand(gui);
        if (!hand.IsSuccess()) return hand;
        if (!knownRoadTile(gui, tile)) return Results.ERROR("Road: milestone terrain is not loaded");
        return mineTileIfNeeded(gui, tile, true);
    }

    private Results clearRoadRock(NGameUI gui, List<Coord> tiles) throws InterruptedException {
        List<Coord> pending = new ArrayList<>(tiles);
        if (pending.size() > TunnelRoad.MAX_LINE_ROCK)
            return Results.ERROR("Road: too many rock tiles between milestones");
        while (!pending.isEmpty()) {
            Coord next = null;
            for (Coord tile : pending) if (bordersOpenGround(gui, tile)) { next = tile; break; }
            if (next == null) return Results.ERROR("Road: no safe open edge for mining the route");
            Results mined = mineRoadTile(gui, next);
            if (!mined.IsSuccess()) return mined;
            pending.remove(next);
        }
        return Results.SUCCESS();
    }

    private boolean knownRoadTile(NGameUI gui, Coord tile) {
        try { return gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile)) != null; }
        catch (Loading unavailable) { return false; }
    }

    private boolean bordersOpenGround(NGameUI gui, Coord tile) {
        for (Coord delta : Coord.uecw) {
            Coord neighbor = tile.add(delta);
            try {
                Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(neighbor));
                if (res != null && !isMineableTileName(res.name)) return true;
            } catch (Loading unavailable) { /* Unknown is not open. */ }
        }
        return false;
    }

    private Coord2d tileCenter(Coord tile) {
        return new Coord2d(tile.x * tilesz.x + tilesz.x / 2, tile.y * tilesz.y + tilesz.y / 2);
    }

    private Gob findNearestSupport(NGameUI gui) throws InterruptedException {
        ArrayList<Gob> supports = Finder.findGobs(ALL_SUPPORTS);
        if (supports.isEmpty()) {
            return null;
        }

        Gob player = NUtils.player();
        if (player == null) {
            return null;
        }

        Gob nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Gob support : supports) {
            double dist = support.rc.dist(player.rc);
            if (dist < minDist) {
                minDist = dist;
                nearest = support;
            }
        }

        return nearest;
    }

    /**
     * Mine the tunnel path from current support to next support position.
     * The tunnel is offset from the support axis by tunnelOffset.
     * IMPORTANT: Must mine tiles in order from current position outward,
     * because you can only mine tiles adjacent to already-open areas.
     */
    private Results mineTunnelPath(NGameUI gui, Coord2d fromSupport, Coord2d toSupport,
                                    Direction direction, Coord2d tunnelOffset)
            throws InterruptedException {

        // Calculate tunnel start and end positions (offset from support positions)
        Coord2d tunnelStart = fromSupport.add(tunnelOffset);
        Coord2d tunnelEnd = toSupport.add(tunnelOffset);

        Coord fromTile = tunnelStart.div(tilesz).floor();
        Coord toTile = tunnelEnd.div(tilesz).floor();

        ArrayList<Coord> tilesToMine = new ArrayList<>();

        // Build list of tiles along the tunnel path IN ORDER from start to end
        // This is critical - we must mine from the open area outward
        if (direction.isVertical()) {
            // North/South: tunnel runs along Y axis at offset X
            int x = fromTile.x;
            if (direction == Direction.NORTH) {
                // Mining north: start from current position, go toward lower Y values
                for (int y = fromTile.y; y >= toTile.y; y--) {
                    tilesToMine.add(new Coord(x, y));
                }
            } else {
                // Mining south: start from current position, go toward higher Y values
                for (int y = fromTile.y; y <= toTile.y; y++) {
                    tilesToMine.add(new Coord(x, y));
                }
            }
        } else {
            // East/West: tunnel runs along X axis at offset Y
            int y = fromTile.y;
            if (direction == Direction.WEST) {
                // Mining west: start from current position, go toward lower X values
                for (int x = fromTile.x; x >= toTile.x; x--) {
                    tilesToMine.add(new Coord(x, y));
                }
            } else {
                // Mining east: start from current position, go toward higher X values
                for (int x = fromTile.x; x <= toTile.x; x++) {
                    tilesToMine.add(new Coord(x, y));
                }
            }
        }

        // Mine each tile in order (from open area outward)
        for (Coord tile : tilesToMine) {
            Results result = mineTileIfNeeded(gui, tile);
            if (!result.IsSuccess()) {
                return result;
            }
        }

        return Results.SUCCESS();
    }

    Results mineTileIfNeeded(NGameUI gui, Coord tilePos) throws InterruptedException {
        return mineTileIfNeeded(gui, tilePos, false);
    }

    Results mineTileIfNeeded(NGameUI gui, Coord tilePos, boolean requireSupport)
            throws InterruptedException {
        Results oreStop = checkOre(gui, tilePos);
        if (oreStop != null) return oreStop;
        // Check if tile needs mining
        if (!needsMining(gui, tilePos)) {
            return Results.SUCCESS(); // Already open, skip
        }

        // Check for dangerous conditions
        if (!isSafeToMine(gui, tilePos, requireSupport)) {
            return Results.ERROR("Unsafe to mine - check support coverage, health or loose rocks");
        }

        // Convert tile to world coordinates (center of tile)
        Coord2d worldPos = tileCenter(tilePos);

        // Navigate close to the tile
        PathFinder pf = new PathFinder(NGob.getDummy(worldPos, 0,
                new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5))), true);
        pf.isHardMode = true;
        Results walked = pf.run(gui);
        if (!walked.IsSuccess()) return walked;

        // Restore resources if needed
        Results restored = new RestoreResources().run(gui);
        if (!restored.IsSuccess()) return restored;

        // Mine the tile
        Resource resBefore = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
        boolean miningSelectionActive = false;

        while (needsMining(gui, tilePos)) {
            oreStop = checkOre(gui, tilePos, miningSelectionActive);
            if (oreStop != null) return oreStop;
            if (!isSafeToMine(gui, tilePos, requireSupport)) {
                return Results.ERROR("Unsafe to mine - check support coverage, health or loose rocks");
            }
            // Clear any stones that may have fallen during previous mining
            if (requireSupport) {
                Results chipped = handleBumlings(gui, true);
                if (!chipped.IsSuccess()) return chipped;
            } else {
                handleBumlings(gui);
            }
            if (!isSafeToMine(gui, tilePos, requireSupport)) {
                return Results.ERROR("Unsafe to mine - check support coverage, health or loose rocks");
            }

            NUtils.mine(worldPos);
            gui.map.wdgmsg("sel", tilePos, tilePos, 0);
            miningSelectionActive = true;

            // Wait for tile to change
            if (NUtils.getStamina() > 0.4) {
                Coord finalTilePos = tilePos;
                Resource finalResBefore = resBefore;
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        Resource current = gui.ui.sess.glob.map.tilesetr(
                                gui.ui.sess.glob.map.gettile(finalTilePos));
                        return current != finalResBefore;
                    }
                });
            }

            // Update resource reference
            resBefore = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
            oreStop = checkOre(gui, tilePos, miningSelectionActive);
            if (oreStop != null) return oreStop;

            // Check stamina/energy
            if (!new RestoreResources().run(gui).IsSuccess()) {
                return Results.ERROR("Cannot restore resources");
            }
        }

        NUtils.getDefaultCur();
        oreStop = checkOre(gui, tilePos, miningSelectionActive);
        if (oreStop != null) return oreStop;
        return Results.SUCCESS();
    }

    private Results checkOre(NGameUI gui, Coord tile) {
        return checkOre(gui, tile, false);
    }

    private Results checkOre(NGameUI gui, Coord tile, boolean cancelMiningSelection) {
        if (oreWatch == null) return null;
        String found = oreWatch.look(gui, tile);
        if (found != null && cancelMiningSelection) {
            Gob player = NUtils.player();
            if (player != null)
                gui.map.wdgmsg("click", Coord.z, player.rc.floor(posres), 3, 0);
        }
        return found == null ? null : Results.ERROR(found);
    }

    private boolean needsMining(NGameUI gui, Coord tilePos) {
        Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
        if (res == null) {
            return false;
        }
        return isMineableTileName(res.name);
    }

    static boolean isMineableTileName(String name) {
        return NParser.checkName(name, MINEABLE_TILES);
    }

    private boolean isSafeToMine(NGameUI gui, Coord tilePos, boolean requireSupport)
            throws InterruptedException {
        Coord2d worldPos = tileCenter(tilePos);

        // Check for loose rocks
        Gob looserock = Finder.findGob(new NAlias("looserock"));
        if (looserock != null && looserock.rc.dist(worldPos) < 93.5) {
            return false;
        }

        // Check support health
        ArrayList<Gob> supports = Finder.findGobs(ALL_SUPPORTS);
        boolean covered = false;
        for (Gob support : supports) {
            double dist = support.rc.dist(worldPos);
            int supportRadius = getSupportRadius(support);

            if (dist <= supportRadius) {
                covered = true;
                GobHealth health = support.getattr(GobHealth.class);
                if (health != null && health.hp <= 0.25) {
                    return false;
                }
            }
        }

        return !requireSupport || covered;
    }

    private int getSupportRadius(Gob support) {
        if (support.ngob == null || support.ngob.name == null) {
            return 100;
        }

        String name = support.ngob.name;
        if (name.contains("minebeam")) return 150;
        if (name.contains("column")) return 125;
        if (name.contains("naturalminesupport")) return 92;
        return 100; // Default for minesupport, ladder, towercap
    }

    static Results placeSupport(NGameUI gui, Coord2d pos, SupportType supportType)
            throws InterruptedException {
        // Check if there's already a support at this location
        Gob existingSupport = Finder.findGob(pos);
        if (existingSupport != null && NParser.isIt(existingSupport, ALL_SUPPORTS)) {
            gui.msg("Support already exists at this location, skipping placement");
            return Results.SUCCESS();
        }

        // Navigate to position
        PathFinder pf = new PathFinder(NGob.getDummy(pos, 0,
                new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5))), true);
        pf.isHardMode = true;
        pf.run(gui);

        // Activate build menu for support type
        boolean menuActivated = false;
        for (MenuGrid.Pagina pag : gui.menu.paginae) {
            if (pag.button() != null && pag.button().name().equals(supportType.menuName)) {
                pag.button().use(new MenuGrid.Interaction(1, 0));
                menuActivated = true;
                break;
            }
        }

        if (!menuActivated) {
            return Results.ERROR("Cannot find " + supportType.menuName + " in build menu");
        }

        // Wait for placement object
        NUtils.addTask(new WaitPlob());

        // Place the support
        gui.map.wdgmsg("place", pos.floor(posres), 0, 1, 0);

        // Wait for construction window
        NUtils.addTask(new WaitConstructionObject(pos));
        NUtils.addTask(new WaitWindow(supportType.menuName));

        // Build the support
        Window window = gui.getWindow(supportType.menuName);
        if (window == null) {
            return Results.ERROR("Construction window did not open");
        }

        NUtils.startBuild(window);

        // Wait for build to complete
        NUtils.addTask(new NTask() {
            int count = 0;

            @Override
            public boolean check() {
                return gui.prog != null || count++ > 100;
            }
        });

        WaitBuildState wbs = new WaitBuildState();
        NUtils.addTask(wbs);

        if (wbs.getState() == WaitBuildState.State.TIMEFORDRINK) {
            if (!new Drink(0.9, false).run(gui).IsSuccess()) {
                return Results.ERROR("Cannot drink");
            }
        } else if (wbs.getState() == WaitBuildState.State.DANGER) {
            return Results.ERROR("Low energy");
        }

        // Wait for window to close (construction complete)
        String windowName = supportType.menuName;
        NUtils.addTask(new NTask() {
            int count = 0;
            @Override
            public boolean check() {
                return gui.getWindow(windowName) == null || count++ > 100;
            }
        });

        // Verify the support was actually built
        Coord2d targetPos = pos;
        final Gob[] builtSupport = {null};
        NUtils.addTask(new NTask() {
            int count = 0;
            @Override
            public boolean check() {
                ArrayList<Gob> nearbySupports = Finder.findGobs(ALL_SUPPORTS);
                for (Gob support : nearbySupports) {
                    if (support.rc.dist(targetPos) < 15) {
                        builtSupport[0] = support;
                        return true;
                    }
                }
                return count++ > 50; // Timeout after checking
            }
        });

        if (builtSupport[0] == null) {
            return Results.ERROR("Failed to build support - check if you have required resources");
        }

        gui.msg("Support placed successfully");
        return Results.SUCCESS();
    }

    private void handleBumlings(NGameUI gui) throws InterruptedException {
        handleBumlings(gui, false);
    }

    private Results handleBumlings(NGameUI gui, boolean preserveHand) throws InterruptedException {
        Gob bumling = Finder.findGob(new NAlias("bumlings"));

        if (bumling != null && bumling.rc.dist(NUtils.player().rc) <= 20) {
            new PathFinder(bumling).run(gui);

            int maxAttempts = 10;
            int attempts = 0;

            while (bumling != null && Finder.findGob(bumling.id) != null && attempts < maxAttempts) {
                attempts++;

                if (preserveHand) {
                    Results hand = ExtendRoad.emptyHand(gui);
                    if (!hand.IsSuccess()) return hand;
                } else if(NUtils.getGameUI().vhand != null) {
                    NUtils.drop(NUtils.getGameUI().vhand);
                }

                new SelectFlowerAction("Chip stone", bumling).run(gui);

                WaitChipperState wcs = new WaitChipperState(bumling, true);
                NUtils.getUI().core.addTask(wcs);

                switch (wcs.getState()) {
                    case BUMLINGNOTFOUND:
                        bumling = null;
                        break;
                    case BUMLINGFORDRINK:
                        new RestoreResources().run(gui);
                        bumling = Finder.findGob(bumling.id);
                        break;
                    case DANGER:
                        gui.msg("Warning: Low energy while chipping stones");
                        return Results.SUCCESS();
                }

                // Re-check if bumling still exists
                if (bumling != null) {
                    bumling = Finder.findGob(bumling.id);
                }
            }
        }
        return Results.SUCCESS();
    }

    /**
     * Mine a connector path from the tunnel to the wing start position.
     * If wingSide is in the same direction as travel, we extend the tunnel one more tile,
     * then mine from there to the wing start.
     */
    private Results mineWingConnector(NGameUI gui, Coord2d supportPos, Direction direction,
                                       TunnelSide tunnelSide, TunnelSide wingSide)
            throws InterruptedException {

        Coord supportTile = supportPos.div(tilesz).floor();

        // Calculate the tunnel end tile (at support position, offset by tunnelSide)
        Coord tunnelEndTile = new Coord(supportTile.x + tunnelSide.dx, supportTile.y + tunnelSide.dy);

        // First, navigate back to the tunnel area (which is definitely open)
        Coord2d tunnelEndPos = tileCenter(tunnelEndTile);
        PathFinder pf = new PathFinder(NGob.getDummy(tunnelEndPos, 0,
                new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5))), true);
        pf.isHardMode = true;
        pf.run(gui);

        // Extend tunnel one more tile in the main direction
        // This opens up access to the wing start area
        Coord extendedTunnelTile = new Coord(tunnelEndTile.x + direction.dx, tunnelEndTile.y + direction.dy);
        Results extendResult = mineTileIfNeeded(gui, extendedTunnelTile);
        if (!extendResult.IsSuccess()) {
            return extendResult;
        }

        // Now mine from extended tunnel to the wing start tile
        // Wing start is at support + wingSide offset
        Coord wingStartTile = new Coord(supportTile.x + wingSide.dx, supportTile.y + wingSide.dy);

        // Mine the tile connecting extended tunnel to wing start (perpendicular to main direction)
        // This is the tile at: extendedTunnel position, but moved toward wing start
        if (!extendedTunnelTile.equals(wingStartTile)) {
            // Mine from extended tunnel toward wing start (one tile perpendicular)
            Coord connectorTile = new Coord(extendedTunnelTile.x - tunnelSide.dx,
                                             extendedTunnelTile.y - tunnelSide.dy);
            Results connectorResult = mineTileIfNeeded(gui, connectorTile);
            if (!connectorResult.IsSuccess()) {
                return connectorResult;
            }
        }

        return Results.SUCCESS();
    }

    /**
     * Mine wings based on the wing option selected by user.
     * Wings are offset from the support position by wingSide, similar to how the main tunnel is offset.
     */
    private Results mineWings(NGameUI gui, Coord2d supportPos, Direction mainDirection,
                              WingOption wingOption, TunnelSide wingSide, int supportRadius)
            throws InterruptedException {
        if (oreWatch != null && oreWatch.found() != null)
            return Results.ERROR(oreWatch.found());

        if (wingOption == WingOption.NONE) {
            return Results.SUCCESS();
        }

        // Calculate wing offset (same concept as tunnel offset)
        Coord2d wingOffset = new Coord2d(wingSide.dx * TILE_SIZE, wingSide.dy * TILE_SIZE);
        Coord2d wingStartPos = supportPos.add(wingOffset);
        Coord wingStartTile = wingStartPos.div(tilesz).floor();

        // Full radius in tiles (no safety margin for wings)
        int wingTiles = supportRadius / 11;

        // Determine which directions to mine based on wing option
        boolean mineEast = false, mineWest = false, mineNorth = false, mineSouth = false;

        switch (wingOption) {
            case EAST:
                mineEast = true;
                break;
            case WEST:
                mineWest = true;
                break;
            case BOTH_EW:
                mineEast = true;
                mineWest = true;
                break;
            case NORTH:
                mineNorth = true;
                break;
            case SOUTH:
                mineSouth = true;
                break;
            case BOTH_NS:
                mineNorth = true;
                mineSouth = true;
                break;
            default:
                break;
        }

        // Mine wings in each enabled direction, starting from the offset position
        if (mineEast) {
            gui.msg("Mining wing: East");
            for (int i = 0; i <= wingTiles; i++) {
                Coord wingTile = new Coord(wingStartTile.x + i, wingStartTile.y);
                Results result = mineTileIfNeeded(gui, wingTile);
                if (!result.IsSuccess()) {
                    if (oreWatch != null && oreWatch.found() != null) return result;
                    break; // Stop this wing but continue with others
                }
            }
        }

        if (mineWest) {
            gui.msg("Mining wing: West");
            for (int i = 0; i <= wingTiles; i++) {
                Coord wingTile = new Coord(wingStartTile.x - i, wingStartTile.y);
                Results result = mineTileIfNeeded(gui, wingTile);
                if (!result.IsSuccess()) {
                    if (oreWatch != null && oreWatch.found() != null) return result;
                    break;
                }
            }
        }

        if (mineNorth) {
            gui.msg("Mining wing: North");
            for (int i = 0; i <= wingTiles; i++) {
                Coord wingTile = new Coord(wingStartTile.x, wingStartTile.y - i);
                Results result = mineTileIfNeeded(gui, wingTile);
                if (!result.IsSuccess()) {
                    if (oreWatch != null && oreWatch.found() != null) return result;
                    break;
                }
            }
        }

        if (mineSouth) {
            gui.msg("Mining wing: South");
            for (int i = 0; i <= wingTiles; i++) {
                Coord wingTile = new Coord(wingStartTile.x, wingStartTile.y + i);
                Results result = mineTileIfNeeded(gui, wingTile);
                if (!result.IsSuccess()) {
                    if (oreWatch != null && oreWatch.found() != null) return result;
                    break;
                }
            }
        }

        return Results.SUCCESS();
    }

    private boolean canContinueTunneling(NGameUI gui, Coord2d currentPos, Direction direction, int radius)
            throws InterruptedException {
        // Check if the next target position is within reasonable bounds
        int placementDistance = radius;
        Coord2d nextPos = currentPos.add(new Coord2d(direction.dx, direction.dy).mul(placementDistance));
        Coord nextTile = nextPos.div(tilesz).floor();

        // Try to get tile info - if we can't, we're probably at map edge
        try {
            gui.ui.sess.glob.map.gettile(nextTile);
        } catch (Exception e) {
            return false;
        }

        // Check for obstacles at next position
        Gob obstacle = Finder.findGob(nextTile);
        if (obstacle != null && !NParser.isIt(obstacle, ALL_SUPPORTS) &&
                !NParser.checkName(obstacle.ngob.name, new NAlias("bumlings"))) {
            gui.msg("Obstacle detected: " + obstacle.ngob.name);
            return false;
        }

        return true;
    }
}
