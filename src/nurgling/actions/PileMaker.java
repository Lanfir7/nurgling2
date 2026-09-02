package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
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
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import haven.WItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private boolean alexandrCreationFlow = false;

    static final int TAKE_TO_HAND_TICKS = 80;
    static final int WAIT_PILE_TICKS = 80;

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

    public static PileMaker forTransferToPiles(Pair<Coord2d, Coord2d> out,
                                                String exactName, NAlias pileName, int th) {
        PileMaker maker = new PileMaker(out, exactName, pileName, th);
        maker.alexandrCreationFlow = true;
        return maker;
    }

    public static PileMaker forTransferToPiles(Pair<Coord2d, Coord2d> out,
                                                NAlias items, NAlias pileName, int th) {
        PileMaker maker = new PileMaker(out, items, pileName, th);
        maker.alexandrCreationFlow = true;
        return maker;
    }

    boolean usesAlexandrCreationFlow() {
        return alexandrCreationFlow;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (alexandrCreationFlow) {
            return runAlexandrCreationFlow(gui);
        }
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
        PileFillDirection direction = directionFor(out);
        List<Coord2d> candidates = exactPos != null
                ? Collections.singletonList(exactPos)
                : Finder.getFreePlaces(out, hitbox, 0, direction);
        if (candidates.isEmpty()) {
            return Results.ERROR("No free space");
        }
        pos = candidates.get(0);

        new PathFinder(NGob.getDummy(pos, 0, hitbox), true).run(gui);
        NUtils.addTask(new WaitStockpile(false, WAIT_PILE_TICKS, false));
        NUtils.getGameUI().map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
        WaitPile wp = WaitPile.withSoftTimeout(pos, WAIT_PILE_TICKS);
        NUtils.getUI().core.addTask(wp);
        pile = wp.getPile();
        if (pile == null) {
            return Results.ERROR("Stockpile was not created");
        }
        NUtils.addTask(new WaitStockpile(true, WAIT_PILE_TICKS, false));
        if (gui.getStockpile() != null) {
            gui.getStockpile().parentGob = pile;
            monitoring.StockpileStorageTracker.observeOpenPile(
                    pile, gui.getStockpile().stockpileItemName(), gui.getStockpile().stockpileCount());
        }
        return Results.SUCCESS();
    }

    private Results runAlexandrCreationFlow(NGameUI gui) throws InterruptedException {
        if (gui.hand.isEmpty()) {
            ArrayList<WItem> witems = getMatchingItems(gui);
            if (witems.isEmpty() || NUtils.takeItemToHand(witems.get(0)) == null) {
                return Results.FAIL();
            }
        }

        NUtils.activateItem(out.a);
        NUtils.getUI().core.addTask(new WaitPlob());
        NHitBox hitbox = NUtils.getGameUI().map.placing.get().ngob.hitBox;
        Coord2d pos = transferPilePosition(
                out,
                () -> Finder.getFreePlace(out, hitbox),
                direction -> {
                    List<Coord2d> candidates = Finder.getFreePlaces(out, hitbox, 0, direction);
                    return candidates.isEmpty() ? null : candidates.get(0);
                });
        if (pos == null) {
            return Results.ERROR("No free space");
        }

        PathFinder path = new PathFinder(NGob.getDummy(pos, 0, hitbox), true)
                .withAlexandrPileBehavior();
        path.run(gui);
        NUtils.addTask(new WaitStockpile(false));
        NUtils.getGameUI().map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
        WaitPile waitPile = new WaitPile(pos);
        NUtils.getUI().core.addTask(waitPile);
        pile = waitPile.getPile();
        if (pile == null) {
            return Results.ERROR("Stockpile was not created");
        }
        NUtils.addTask(new WaitStockpile(true));
        if (gui.getStockpile() != null) {
            gui.getStockpile().parentGob = pile;
            monitoring.StockpileStorageTracker.observeOpenPile(
                    pile, gui.getStockpile().stockpileItemName(), gui.getStockpile().stockpileCount());
        }
        return Results.SUCCESS();
    }

    static PileFillDirection directionFor(Pair<Coord2d, Coord2d> bounds) {
        return bounds instanceof NArea.DirectedAreaBounds
                ? ((NArea.DirectedAreaBounds) bounds).direction()
                : PileFillDirection.LEFT_TO_RIGHT;
    }

    static Coord2d transferPilePosition(Pair<Coord2d, Coord2d> bounds,
                                        Supplier<Coord2d> alexandrPosition,
                                        Function<PileFillDirection, Coord2d> directedPosition) {
        PileFillDirection direction = directionFor(bounds);
        return direction == PileFillDirection.LEFT_TO_RIGHT
                ? alexandrPosition.get()
                : directedPosition.apply(direction);
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
