package nurgling.actions.bots;

import haven.Gob;
import haven.MCache;
import haven.OCache;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.widgets.bots.MasterMinerGroundStacks;

/**
 * Walks to and takes matching loose items in the loaded view, up to a cap.
 */
public class PickupGroundItems implements Action {
    private final String resPath;
    private final double radius;
    private final int maxItems;

    public PickupGroundItems(String resPath) {
        this(resPath, MasterMinerGroundStacks.PICKUP_RADIUS, Integer.MAX_VALUE);
    }

    public PickupGroundItems(String resPath, int maxItems) {
        this(resPath, MasterMinerGroundStacks.PICKUP_RADIUS, maxItems);
    }

    public PickupGroundItems(String resPath, double radius) {
        this(resPath, radius, Integer.MAX_VALUE);
    }

    public PickupGroundItems(String resPath, double radius, int maxItems) {
        this.resPath = resPath;
        this.radius = radius;
        this.maxItems = maxItems <= 0 ? Integer.MAX_VALUE : maxItems;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (resPath == null || gui == null) {
            return Results.FAIL();
        }
        int taken = 0;
        while (taken < maxItems) {
            Gob player = NUtils.player();
            if (player == null) {
                return Results.FAIL();
            }
            if (gui.getInventory() != null && gui.getInventory().getFreeSpace() <= 0) {
                return Results.SUCCESS();
            }
            Gob item = nearest(player);
            if (item == null) {
                return Results.SUCCESS();
            }
            if (item.rc.dist(player.rc) > MCache.tilesz.x) {
                Results walk = new PathFinder(item).run(gui);
                if (!walk.IsSuccess()) {
                    return walk;
                }
            }
            NUtils.takeFromEarth(item);
            taken++;
        }
        return Results.SUCCESS();
    }

    private Gob nearest(Gob player) {
        Gob best = null;
        double bestDist = radius;
        OCache oc = player.glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob == null || gob == player || gob instanceof OCache.Virtual) {
                    continue;
                }
                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }
                if (!resPath.equals(gob.ngob.name)) {
                    continue;
                }
                double dist = gob.rc.dist(player.rc);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = gob;
                }
            }
        }
        return best;
    }
}
