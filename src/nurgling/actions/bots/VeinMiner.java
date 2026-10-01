package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.overlays.NMiningSafeOverlay;
import nurgling.overlays.NMiningNumber;
import nurgling.overlays.NMiningSupport;
import nurgling.tasks.GetCurs;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitChipperState;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class VeinMiner implements Action {

    private static final NAlias ALL_SUPPORTS = new NAlias(
            "minebeam", "column", "towercap", "ladder", "minesupport", "naturalminesupport"
    );

    private final Coord presetSeed;

    public VeinMiner() {
        this(null);
    }

    public VeinMiner(Coord seed) {
        this.presetSeed = seed;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || !(gui.map instanceof NMapView)) {
            return Results.ERROR("No map");
        }
        NMapView map = (NMapView) gui.map;
        VeinSeedCapture cap = map.veinSeedCapture();
        Coord seed = presetSeed;
        try {
            gui.ui.rcvr.rcvmsg(NUtils.getUI().getMenuGridId(), "act", "mine");
            NUtils.addTask(new GetCurs("mine"));
            if (seed == null) {
                cap.arm();
                gui.msg("Vein Miner: click a rock tile");
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        return seedWaitComplete(cap, NUtils.getCursorName());
                    }
                });
                seed = cap.peek();
                if (seed == null) {
                    return Results.SUCCESS();
                }
            }

            final Coord veinSeed = seed;
            String type = tileName(gui, veinSeed);
            if (type == null) {
                return Results.ERROR("Unknown tile");
            }
            if (presetSeed == null) {
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        return seedFinished(type, tileName(gui, veinSeed));
                    }
                });
            } else if (!isVeinRock(type)) {
                return Results.ERROR("Not a rock");
            }

            VeinWorklist list = (presetSeed == null)
                    ? new VeinWorklist(type, veinSeed)
                    : VeinWorklist.withInitialTarget(type, veinSeed);
            Set<Long> pathRetries = new HashSet<>();
            while (true) {
                if (inFight(gui)) {
                    return Results.FAIL();
                }
                list.scanVisible(c -> tileName(gui, c), c -> isSafe(gui, c));
                Gob player = NUtils.player();
                if (player == null) {
                    return Results.ERROR("Lost player");
                }
                Coord playerTile = player.rc.div(tilesz).floor();
                Coord next = list.takeNearest(playerTile);
                if (next == null) {
                    gui.msg("Vein Miner: vein finished.");
                    return Results.SUCCESS();
                }
                if (!isTargetTile(type, tileName(gui, next)) || !isSafe(gui, next)) {
                    continue;
                }
                Results mined = mineTile(gui, next, type);
                if (mined.isCycle) {
                    if (pathRetries.add(tileKey(next))) {
                        list.requeue(next);
                    }
                    continue;
                }
                if (!mined.IsSuccess()) {
                    return mined;
                }
                pathRetries.remove(tileKey(next));
                list.markMined(next);
                Results bum = handleBumlings(gui);
                if (!bum.IsSuccess()) {
                    return bum;
                }
            }
        } finally {
            cap.disarm();
        }
    }

    public static boolean supportCovers(Coord tile, Coord begin, boolean[][] data) {
        if (tile == null || begin == null || data == null) {
            return false;
        }
        int dx = tile.x - begin.x;
        int dy = tile.y - begin.y;
        return dx >= 0 && dy >= 0 && dx < data.length && data[dx] != null
                && dy < data[dx].length && data[dx][dy];
    }

    static boolean isSafe(NGameUI gui, Coord tile) {
        if (tile == null || gui == null || gui.ui == null || gui.ui.sess == null) {
            return false;
        }
        synchronized (gui.ui.sess.glob.oc) {
            // A live cave-in warning takes precedence over a solver marker that has
            // not yet been refreshed.
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (!gob.virtual && markerMatchesTile(tile, gob.rc)) {
                    for (Gob.Overlay ol : gob.ols) {
                        if (ol.spr instanceof NMiningNumber && ((NMiningNumber) ol.spr).val > 0) {
                            return false;
                        }
                    }
                }
            }
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (!gob.virtual && gob.id != -1) {
                    Gob.Overlay supportOl = gob.findol(NMiningSupport.class);
                    NMiningSupport.SupportMask mask = supportOl != null
                            && supportOl.spr instanceof NMiningSupport
                            ? ((NMiningSupport) supportOl.spr).getMask() : null;
                    if (supportCoversOrCircle(tile, mask == null ? null : mask.begin,
                            mask == null ? null : mask.data, gob.rc,
                            gob.ngob == null ? null : gob.ngob.name, gob.id)) {
                        return true;
                    }
                }
                if (markerMatchesTile(tile, gob.rc)) {
                    for (Gob.Overlay ol : gob.ols) {
                        if (ol.spr instanceof NMiningSafeOverlay
                                && ((NMiningSafeOverlay) ol.spr).isConfirmedSafe(gui)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static boolean markerMatchesTile(Coord tile, Coord2d gobRc) {
        return tile != null && gobRc != null && tile.equals(gobRc.div(tilesz).floor());
    }

    static boolean supportCoversOrCircle(Coord tile, Coord begin, boolean[][] data,
                                         Coord2d gobRc, String name, long gobId) {
        if (gobId == -1) {
            return false;
        }
        // A completed mask is authoritative, including its uncovered pixels.
        if (data != null) {
            return supportCovers(tile, begin, data);
        }
        return inSupportRadius(tile, gobRc, supportRadiusFor(name));
    }

    public static boolean inSupportRadius(Coord tile, Coord2d gobRc, int radius) {
        if (tile == null || gobRc == null || radius <= 0) {
            return false;
        }
        return gobRc.dist(tileCenter(tile)) < radius;
    }

    static int supportRadiusFor(String name) {
        if (name == null || "gfx/terobjs/trees/towercap".equals(name)) {
            return -1;
        }
        NMiningSupport.Spec spec = NMiningSupport.specFor(name);
        return spec != null && !spec.isRect() && spec.circleRadius != null
                ? spec.circleRadius : -1;
    }

    static boolean seedFinished(String original, String now) {
        if (original == null || now == null) {
            return false;
        }
        return !original.equals(now);
    }

    static boolean seedWaitComplete(VeinSeedCapture cap, String cursorName) {
        if (cap != null && cap.peek() != null) {
            return true;
        }
        if (cursorName == null) {
            return false;
        }
        return !NParser.checkName(cursorName, "mine");
    }

    static long tileKey(Coord c) {
        return ((long) c.x << 32) | (c.y & 0xffffffffL);
    }

    static boolean isTargetTile(String type, String currentType) {
        return type != null && type.equals(currentType);
    }

    public static boolean isVeinRock(String tileName) {
        return tileName != null && tileName.startsWith("gfx/tiles/rocks/");
    }

    public static boolean isMineCursor() {
        try {
            return isMineCursor(NUtils.getCursorName());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMineCursor(String cursorName) {
        return cursorName != null && NParser.checkName(cursorName, "mine");
    }

    public static String tileName(NGameUI gui, Coord tile) {
        if (gui == null || gui.ui == null || gui.ui.sess == null || tile == null) {
            return null;
        }
        try {
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile));
            return res == null ? null : res.name;
        } catch (Loading e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Results mineTile(NGameUI gui, Coord tilePos, String type) throws InterruptedException {
        if (inFight(gui)) {
            return Results.FAIL();
        }
        Gob player = NUtils.player();
        if (player == null) {
            return Results.ERROR("Lost player");
        }
        Gob looserock = Finder.findGob(new NAlias("looserock"));
        if (looserock != null && looserock.rc.dist(player.rc) < 93.5) {
            return Results.ERROR("Loose rock detected — unsafe to continue");
        }
        if (!checkSupportHealth(tilePos)) {
            return Results.ERROR("Nearby support damaged — unsafe to continue");
        }

        Coord2d worldPos = tileCenter(tilePos);
        NUtils.getDefaultCur();
        PathFinder pf = new PathFinder(NGob.getDummy(worldPos, 0,
                new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5))), true);
        pf.isHardMode = true;
        Results pfResult = pf.run(gui);
        if (!pfResult.IsSuccess()) {
            return pfResult;
        }
        if (!isTargetTile(type, tileName(gui, tilePos)) || !isSafe(gui, tilePos)) {
            return Results.CYCLE();
        }
        if (!new RestoreResources().run(gui).IsSuccess()) {
            return Results.ERROR("Cannot restore resources");
        }

        while (isTargetTile(type, tileName(gui, tilePos))) {
            Resource resBefore = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
            if (inFight(gui)) {
                return Results.FAIL();
            }
            player = NUtils.player();
            if (player == null) {
                return Results.ERROR("Lost player");
            }
            Gob looserockLoop = Finder.findGob(new NAlias("looserock"));
            if (looserockLoop != null && looserockLoop.rc.dist(player.rc) < 93.5) {
                return Results.ERROR("Loose rock detected — unsafe to continue");
            }
            if (!checkSupportHealth(tilePos)) {
                return Results.ERROR("Nearby support damaged — unsafe to continue");
            }
            Results bum = handleBumlings(gui);
            if (!bum.IsSuccess()) {
                return bum;
            }
            if (!isTargetTile(type, tileName(gui, tilePos)) || !isSafe(gui, tilePos)) {
                return Results.CYCLE();
            }
            NUtils.mine(worldPos);
            gui.map.wdgmsg("sel", tilePos, tilePos, 0);
            if (NUtils.getStamina() > 0.4) {
                Resource finalResBefore = resBefore;
                Coord finalTilePos = tilePos;
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        Resource current = gui.ui.sess.glob.map.tilesetr(
                                gui.ui.sess.glob.map.gettile(finalTilePos));
                        return current != finalResBefore;
                    }
                });
            }
            gui.map.wdgmsg("click", Coord.z, player.rc.floor(posres), 3, 0);
            NUtils.getUI().core.addTask(new GetCurs("arw"));
            if (isTargetTile(type, tileName(gui, tilePos))
                    && !new RestoreResources().run(gui).IsSuccess()) {
                return Results.ERROR("Cannot restore resources");
            }
        }
        return Results.SUCCESS();
    }

    private boolean checkSupportHealth(Coord tilePos) {
        Coord2d worldPos = tileCenter(tilePos);
        ArrayList<Gob> supports = Finder.findGobs(ALL_SUPPORTS);
        for (Gob support : supports) {
            if (support.rc.dist(worldPos) > 150) {
                continue;
            }
            GobHealth health = support.getattr(GobHealth.class);
            if (health != null && health.hp <= 0.25) {
                return false;
            }
        }
        return true;
    }

    private boolean inFight(NGameUI gui) {
        return gui.fv != null && gui.fv.lsrel != null && !gui.fv.lsrel.isEmpty();
    }

    private static Coord2d tileCenter(Coord tile) {
        return new Coord2d(tile.x * tilesz.x + tilesz.x / 2, tile.y * tilesz.y + tilesz.y / 2);
    }

    private Results handleBumlings(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) {
            return Results.ERROR("Lost player");
        }
        Gob bumling = Finder.findGob(new NAlias("bumlings"));
        if (bumling == null || bumling.rc.dist(player.rc) > 20) {
            return Results.SUCCESS();
        }
        Results pfResult = new PathFinder(bumling).run(gui);
        if (!pfResult.IsSuccess()) {
            return pfResult;
        }
        int attempts = 0;
        while (bumling != null && Finder.findGob(bumling.id) != null && attempts < 10) {
            attempts++;
            if (NUtils.getGameUI().vhand != null) {
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
                    if (!new RestoreResources().run(gui).IsSuccess()) {
                        return Results.ERROR("Cannot restore resources");
                    }
                    bumling = Finder.findGob(bumling.id);
                    break;
                case DANGER:
                    gui.msg("Warning: Low energy while chipping stones");
                    return Results.ERROR("Low energy while chipping stones");
                default:
                    bumling = Finder.findGob(bumling.id);
                    break;
            }
        }
        return Results.SUCCESS();
    }
}
