package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import haven.MapView;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NGob;
import nurgling.NHitBox;
import nurgling.NUtils;
import nurgling.tasks.WaitPile;
import nurgling.tasks.WaitPlob;
import nurgling.tasks.WaitItemInHand;
import nurgling.NGItem;
import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;
import nurgling.db.StockpileStoragePolicy;
import nurgling.pf.NHitBoxD;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import haven.WItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static haven.OCache.posres;

public class PileMaker implements Action{
    Pair<Coord2d, Coord2d> out;
    NAlias items;
    NAlias pileName;
    int th = 0;

    // When set, use exact name matching instead of NAlias substring matching
    String exactName = null;

    public Gob getPile() {
        return pile;
    }

    Gob pile = null;
    Coord2d exactPos = null;

    static final int TAKE_TO_HAND_TICKS = 80;
    static final int WAIT_PILE_TICKS = 80;
    static final double EXACT_ESCAPE_MARGIN = MCache.tilesz.x * 10;

    @FunctionalInterface
    interface CandidateProbe {
        boolean isSafe(Coord2d candidate) throws InterruptedException;
    }

    @FunctionalInterface
    interface DirectMove {
        boolean run(Coord2d target) throws InterruptedException;
    }

    static boolean shouldCloseStockpileBeforeTakeToHand(boolean stockpileWindowOpen) {
        return stockpileWindowOpen;
    }

    static WaitItemInHand takeToHandWait(WItem item) {
        return WaitItemInHand.withSoftTimeout(item, TAKE_TO_HAND_TICKS);
    }

    public PileMaker(Pair<Coord2d, Coord2d> out, NAlias items, NAlias pileName) {
        this.out = out;
        this.items = items;
        this.pileName = pileName;
    }

    public PileMaker(Coord2d exactPos, NAlias items, NAlias pileName) {
        this.out = new Pair<>(exactPos, exactPos);
        this.items = items;
        this.pileName = pileName;
        this.exactPos = exactPos;
    }

    public PileMaker(Pair<Coord2d, Coord2d> out, NAlias items, NAlias pileName, int th) {
        this.out = out;
        this.items = items;
        this.pileName = pileName;
        this.th = th;
    }

    public PileMaker(Pair<Coord2d, Coord2d> out, String exactName, NAlias pileName, int th) {
        this.out = out;
        this.exactName = exactName;
        this.items = new NAlias(exactName);
        this.pileName = pileName;
        this.th = th;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (shouldCloseStockpileBeforeTakeToHand(gui.getStockpile() != null)) {
            new CloseTargetContainer("Stockpile").run(gui);
        }
        if (gui.hand.isEmpty()) {
            ArrayList<WItem> witems = getMatchingItems(gui);
            if(witems.isEmpty() || NUtils.takeItemToHand(witems.get(0), takeToHandWait(witems.get(0)))==null)

                return Results.FAIL();
        }
        Coord2d itemactPos = plobClickPos(gui, exactPos != null ? exactPos : out.a);
        NUtils.activateItem(itemactPos);
        // Background sessions never finish the GL ghost; wait only for the server "place" msg.
        NUtils.getUI().core.addTask(new WaitPlob(false));
        Coord2d pos;
        NHitBox hitbox = resolveHitbox(plobHitbox(gui), pileName);
        if (hitbox == null) {
            return Results.ERROR("No hitbox");
        }
        double width = Math.abs(hitbox.end.x - hitbox.begin.x);
        double height = Math.abs(hitbox.end.y - hitbox.begin.y);
        double candidateStride = Math.max(1, Math.min(width, height));
        PileFillDirection direction = directionFor(out);
        List<Coord2d> candidates = exactPos != null
                ? Collections.singletonList(exactPos)
                : Finder.getFreePlaces(out, hitbox, 0, direction, candidateStride);
        pos = firstSafeCandidate(candidates, candidate -> approachAndPreserveEscape(gui, candidate, hitbox));
        if (pos == null) {
            return Results.ERROR("No free space");
        }

        NUtils.addTask(new WaitStockpile(false, WAIT_PILE_TICKS, false));
        NUtils.getGameUI().map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
        WaitPile wp = WaitPile.withSoftTimeout(pos, WAIT_PILE_TICKS);
        NUtils.getUI().core.addTask(wp);
        pile = wp.getPile();
        if (pile == null) {
            return Results.ERROR("Stockpile was not created");
        }
        NUtils.addTask(new WaitStockpile(true, WAIT_PILE_TICKS, false));
        return Results.SUCCESS();
    }

    static Coord2d firstSafeCandidate(List<Coord2d> candidates, CandidateProbe probe)
            throws InterruptedException {
        for (Coord2d candidate : candidates) {
            if (probe.isSafe(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static PileFillDirection directionFor(Pair<Coord2d, Coord2d> bounds) {
        return bounds instanceof NArea.DirectedAreaBounds
                ? ((NArea.DirectedAreaBounds) bounds).direction()
                : PileFillDirection.LEFT_TO_RIGHT;
    }

    static boolean exitStartObstacle(Coord2d freeStart,
                                     DirectMove move) throws InterruptedException {
        return freeStart != null && move.run(freeStart);
    }

    private static Coord2d freeStartTarget(PathFinder preview) {
        if (preview == null || preview.gobInStartPos == null ||
                preview.pfmap == null || preview.start_pos == null) {
            return null;
        }
        return nurgling.pf.Utils.pfGridToWorld(
                preview.pfmap.getCells()[preview.start_pos.x][preview.start_pos.y].pos);
    }

    static List<Coord2d> escapeTargets(Pair<Coord2d, Coord2d> area,
                                       double spacing, double clearance) {
        double minX = Math.min(area.a.x, area.b.x);
        double maxX = Math.max(area.a.x, area.b.x);
        double minY = Math.min(area.a.y, area.b.y);
        double maxY = Math.max(area.a.y, area.b.y);
        double step = Math.max(1, spacing);
        double margin = Math.max(1, clearance);
        ArrayList<Coord2d> targets = new ArrayList<>();

        for (double x : axisSamples(minX, maxX, step)) {
            addUnique(targets, Coord2d.of(x, minY - margin));
            addUnique(targets, Coord2d.of(x, maxY + margin));
        }
        for (double y : axisSamples(minY, maxY, step)) {
            addUnique(targets, Coord2d.of(minX - margin, y));
            addUnique(targets, Coord2d.of(maxX + margin, y));
        }
        return targets;
    }

    static Pair<Coord2d, Coord2d> escapeEnvelope(Coord2d player, Coord2d candidate,
                                                  double margin) {
        double safeMargin = Math.max(1, margin);
        return new Pair<>(
                Coord2d.of(Math.min(player.x, candidate.x) - safeMargin,
                        Math.min(player.y, candidate.y) - safeMargin),
                Coord2d.of(Math.max(player.x, candidate.x) + safeMargin,
                        Math.max(player.y, candidate.y) + safeMargin));
    }

    private static List<Double> axisSamples(double min, double max, double spacing) {
        ArrayList<Double> samples = new ArrayList<>();
        for (double value = min; value < max; value += spacing) {
            samples.add(value);
        }
        if (samples.isEmpty() || Math.abs(samples.get(samples.size() - 1) - max) > 0.001) {
            samples.add(max);
        }
        return samples;
    }

    private static void addUnique(List<Coord2d> targets, Coord2d target) {
        if (!targets.contains(target)) {
            targets.add(target);
        }
    }

    private boolean approachAndPreserveEscape(NGameUI gui, Coord2d candidate, NHitBox hitbox)
            throws InterruptedException {
        Gob dummy = NGob.getDummy(candidate, 0, hitbox);
        PathFinder preview = new PathFinder(dummy, true);
        if (preview.construct(true) == null && !preview.dn) {
            return false;
        }

        Gob player = NUtils.player();
        Gob startObstacle = preview.gobInStartPos;
        if (player != null && player.rc != null && startObstacle != null &&
                startObstacle.ngob != null && startObstacle.ngob.name != null &&
                new NAlias("stockpile").matches(startObstacle.ngob.name)) {
            Coord2d freeStart = freeStartTarget(preview);
            if (!exitStartObstacle(freeStart,
                    target -> new GoTo(target).run(gui).IsSuccess())) {
                return false;
            }

            preview = new PathFinder(dummy, true);
            if (preview.construct(true) == null && !preview.dn) {
                return false;
            }
        }

        if (!nonRetryingPathFinder(dummy).run(gui).IsSuccess()) {
            return false;
        }

        player = NUtils.player();
        if (player == null || player.rc == null) {
            return false;
        }
        Coord2d escape = findEscapeTarget(player.rc, dummy, hitbox);
        if (escape == null) {
            return false;
        }

        if (overlapsCandidate(player, dummy)) {
            if (!nonRetryingPathFinder(escape).run(gui).IsSuccess()) {
                return false;
            }
            player = NUtils.player();
            if (player == null || overlapsCandidate(player, dummy)) {
                return false;
            }
            return findEscapeTarget(player.rc, dummy, hitbox) != null;
        }
        return true;
    }

    private Coord2d findEscapeTarget(Coord2d from, Gob futurePile, NHitBox hitbox)
            throws InterruptedException {
        if (exactPos == null && !insidePlacementArea(from) &&
                !new NHitBoxD(futurePile).containsSemiOpen(from)) {
            return from;
        }
        double width = Math.abs(hitbox.end.x - hitbox.begin.x);
        double height = Math.abs(hitbox.end.y - hitbox.begin.y);
        double spacing = Math.max(MCache.tilesz.x, Math.min(width, height));
        double clearance = Math.max(MCache.tilesz.x * 2, Math.max(width, height) + MCache.tilesz.x);
        Pair<Coord2d, Coord2d> escapeArea = exactPos == null
                ? out
                : escapeEnvelope(from, futurePile.rc, Math.max(EXACT_ESCAPE_MARGIN, clearance * 2));
        for (Coord2d target : escapeTargets(escapeArea, spacing, clearance)) {
            if (PathFinder.isAvailableWithObstacle(from, target, futurePile)) {
                return target;
            }
        }
        return null;
    }

    private boolean insidePlacementArea(Coord2d point) {
        double minX = Math.min(out.a.x, out.b.x);
        double maxX = Math.max(out.a.x, out.b.x);
        double minY = Math.min(out.a.y, out.b.y);
        double maxY = Math.max(out.a.y, out.b.y);
        return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY;
    }

    private static boolean overlapsCandidate(Gob player, Gob futurePile) {
        NHitBoxD pileBox = new NHitBoxD(futurePile);
        if (player.ngob != null && player.ngob.hitBox != null) {
            return pileBox.intersects(new NHitBoxD(player), false);
        }
        return pileBox.containsSemiOpen(player.rc);
    }

    private static PathFinder nonRetryingPathFinder(Gob target) {
        return new PathFinder(target, true) {
            @Override
            protected boolean onLegFailed(NGameUI gui, Coord2d at) {
                return false;
            }
        };
    }

    private static PathFinder nonRetryingPathFinder(Coord2d target) {
        return new PathFinder(target) {
            @Override
            protected boolean onLegFailed(NGameUI gui, Coord2d at) {
                return false;
            }
        };
    }

    static NHitBox plobHitbox(NGameUI gui) {
        try {
            if (gui != null && gui.map != null && gui.map.placing != null && gui.map.placing.ready()) {
                MapView.Plob plob = gui.map.placing.get();
                if (plob != null && plob.ngob != null) {
                    return plob.ngob.hitBox;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Prefer the loaded placement ghost; fall back to the known stockpile hitbox
     * so a headless session can still pathfind and send {@code place}.
     */
    public static NHitBox resolveHitbox(NHitBox plobHitbox, NAlias pileName) {
        if (plobHitbox != null) {
            return plobHitbox;
        }
        if (pileName != null) {
            for (String key : pileName.getKeys()) {
                NHitBox custom = NHitBox.findCustom(key);
                if (custom != null) {
                    return custom;
                }
            }
        }
        return NHitBox.findCustom("stockpile");
    }

    /**
     * Gets items from inventory, using exact name match if exactName is set,
     * otherwise uses NAlias substring matching.
     */
    private ArrayList<WItem> getMatchingItems(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> allItems = NUtils.getGameUI().getInventory().getItems(items, th);
        if (exactName == null) {
            return allItems;
        }
        ArrayList<WItem> exactMatches = new ArrayList<>();
        for (WItem witem : allItems) {
            if (((NGItem) witem.item).name().equals(exactName)) {
                exactMatches.add(witem);
            }
        }
        return exactMatches;
    }

    /**
     * itemact on the player (between two piles) hits the neighbor.
     * Click the original empty tile, or a cell farther from other piles.
     */
    private static Coord2d plobClickPos(NGameUI gui, Coord2d target) {
        ArrayList<Coord2d> others = new ArrayList<>();
        if (gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null) {
            synchronized (gui.ui.sess.glob.oc) {
                for (Gob gob : gui.ui.sess.glob.oc) {
                    if (gob == null || gob.rc == null || gob.ngob == null
                            || !StockpileStoragePolicy.isStockpileRes(gob.ngob.name)) {
                        continue;
                    }
                    others.add(gob.rc);
                }
            }
        }
        if (!hitsForeignPile(target, target, others)) {
            return target;
        }
        double[][] dirs = {
                {15, 0}, {-15, 0}, {0, 15}, {0, -15},
                {15, 15}, {15, -15}, {-15, 15}, {-15, -15}
        };
        for (double[] d : dirs) {
            Coord2d cand = target.add(d[0], d[1]);
            if (!hitsForeignPile(cand, target, others)) {
                return cand;
            }
        }
        Gob player = NUtils.player();
        if (player != null && player.rc != null && !hitsForeignPile(player.rc, target, others)) {
            return player.rc;
        }
        return target;
    }

    private static boolean hitsForeignPile(Coord2d click, Coord2d target, ArrayList<Coord2d> others) {
        for (Coord2d pile : others) {
            if (StockpileStoragePolicy.clickHitsForeignPile(
                    click.x, click.y, target.x, target.y, pile.x, pile.y)) {
                return true;
            }
        }
        return false;
    }
}
