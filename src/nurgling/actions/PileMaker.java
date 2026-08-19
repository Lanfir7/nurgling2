package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NGob;
import nurgling.NHitBox;
import nurgling.NUtils;
import nurgling.tasks.WaitPile;
import nurgling.tasks.WaitPlob;
import nurgling.NGItem;
import nurgling.db.StockpileStoragePolicy;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import haven.WItem;
import java.util.ArrayList;

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

        if (gui.hand.isEmpty()) {
            ArrayList<WItem> witems = getMatchingItems(gui);
            if(witems.isEmpty() || NUtils.takeItemToHand(witems.get(0))==null)

                return Results.FAIL();
        }
        Coord2d itemactPos = exactPos != null ? plobClickPos(gui, exactPos) : out.a;
        NUtils.activateItem(itemactPos);
        NUtils.getUI().core.addTask(new WaitPlob());
        Coord2d pos;
        NHitBox hitbox = NUtils.getGameUI().map.placing.get().ngob.hitBox;
        if (exactPos != null) {
            pos = exactPos;
        } else if ((pos = Finder.getFreePlace(out, hitbox)) == null) {
            return Results.ERROR("No free space");
        }

        new PathFinder( NGob.getDummy(pos, 0, hitbox),true).run(gui);
        NUtils.addTask(new WaitStockpile(false));
        NUtils.getGameUI().map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
        WaitPile wp = new WaitPile(pos);
        NUtils.getUI().core.addTask(wp);
        pile = wp.getPile();
        NUtils.addTask(new WaitStockpile(true));
        return Results.SUCCESS();
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
