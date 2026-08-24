package nurgling.actions;

import haven.*;
import nurgling.NFlowerMenu;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tasks.NFlowerMenuIsClosed;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitCollectState;
import nurgling.tools.NAlias;

import static haven.OCache.posres;


public class CollectFromGob implements Action{

    Gob target;
    String action;
    String pose;
    boolean withPiles = false;
    Coord targetSize = null;
    int marker = - 1;
    public CollectFromGob(Gob target, String action, String pose, Coord targetSize, NAlias targetItems, Pair<Coord2d, Coord2d> pileArea) {
        this.target = target;
        this.action = action;
        this.pose = pose;
        this.targetSize = targetSize;
        this.withPiles = true;
        this.targetItems = targetItems;
        this.pileArea = pileArea;
    }

    public CollectFromGob(Gob target, String action, String pose, boolean withPiles, Coord targetSize, int marker, NAlias targetItems, Pair<Coord2d, Coord2d> pileArea) {
        this.target = target;
        this.action = action;
        this.pose = pose;
        this.withPiles = withPiles;
        this.targetSize = targetSize;
        this.marker = marker;
        this.targetItems = targetItems;
        this.pileArea = pileArea;
    }

    public CollectFromGob(Gob target, String action, String pose, Coord targetSize,  NAlias targetItems, boolean withoutTransfer)
    {
        this.target = target;
        this.action = action;
        this.pose = pose;
        this.withPiles = false;
        this.targetSize = targetSize;
        this.targetItems = targetItems;
        this.withoutTransfer = withoutTransfer;
    }

    public CollectFromGob(Gob target, String action, String pose, Coord targetSize, NAlias targetItems, boolean withoutTransfer, int stopAt)
    {
        this(target, action, pose, targetSize, targetItems, withoutTransfer);
        this.stopAt = stopAt;
    }

     NAlias targetItems;
    Pair<Coord2d,Coord2d> pileArea = null;

    boolean withoutTransfer = false;
    int stopAt = 0;

    public static boolean reachedStopAt(int have, int stopAt) {
        return stopAt > 0 && have >= stopAt;
    }

    public static boolean shouldCancelHarvest(boolean reachedStop, boolean stillPicking) {
        return reachedStop && stillPicking;
    }

    public static boolean collectStartWaitDone(boolean hasPickPose, boolean hasClocks, boolean reachedStop, int ticks, int maxTicks) {
        return hasPickPose || hasClocks || reachedStop || ticks >= maxTicks;
    }

    public static boolean harvestCancelWaitDone(boolean isIdle, boolean hasClocks, int ticks, int maxTicks) {
        return isIdle || !hasClocks || ticks >= maxTicks;
    }

    private boolean inventoryReachedStop() {
        return targetItems != null && reachedStopAt(WaitCollectState.countPieces(targetItems), stopAt);
    }

    private boolean hasPickPose(Gob player) {
        return player != null && player.pose() != null && pose != null && player.pose().contains(pose);
    }

    private void waitCollectStart(NGameUI gui) throws InterruptedException {
        NUtils.getUI().core.addTask(new NTask() {
            int ticks = 0;
            {
                infinite = false;
                maxCounter = 200;
                criticalOnTimeout = false;
            }
            @Override
            public boolean check() {
                return collectStartWaitDone(hasPickPose(NUtils.player()), gui.prog != null, inventoryReachedStop(), ticks++, 200);
            }
        });
    }

    private void cancelHarvestIfNeeded(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        boolean busy = hasPickPose(player) || gui.prog != null;
        if (player == null || !shouldCancelHarvest(true, busy))
            return;
        NUtils.lclick(player.rc);
        NUtils.addTask(new NTask() {
            int ticks = 0;
            {
                infinite = false;
                maxCounter = 200;
                criticalOnTimeout = false;
            }
            @Override
            public boolean check() {
                Gob p = NUtils.player();
                String cpose = p != null ? p.pose() : null;
                boolean idle = cpose != null && cpose.contains("gfx/borka/idle");
                return harvestCancelWaitDone(idle, gui.prog != null, ticks++, 200);
            }
        });
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (inventoryReachedStop())
            return Results.SUCCESS();
        WaitCollectState wcs = null;
        do {
            if (inventoryReachedStop()) {
                cancelHarvestIfNeeded(gui);
                return Results.SUCCESS();
            }
            if(!withoutTransfer) {
                NInventory inv = (gui != null) ? gui.getInventory() : null;
                if (inv != null && targetSize != null && inv.getNumberFreeCoord(targetSize) == 0) {
                    if (withPiles) {
                        new TransferToPiles(pileArea, targetItems).run(gui);
                    }
                }
            }
            if(marker!=-1)
            {
                if((target.ngob.getModelAttribute()&marker)!=marker)
                {
                    return Results.SUCCESS();
                }
            }
            new PathFinder(target).run(gui);
            gui.map.wdgmsg("click", Coord.z, target.rc.floor(posres), 3, 0, 1, (int) target.id, target.rc.floor(posres),
                    0, -1);
            NFlowerMenu fm = NUtils.findFlowerMenu();
            if (fm != null) {
                if (fm.hasOpt(action)) {
                    if (fm.chooseOpt(action)) {
                        NUtils.getUI().core.addTask(new NFlowerMenuIsClosed());
                        waitCollectStart(gui);
                        if (inventoryReachedStop()) {
                            cancelHarvestIfNeeded(gui);
                            return Results.SUCCESS();
                        }
                        wcs = stopAt > 0
                                ? new WaitCollectState(target, targetSize, targetItems, stopAt)
                                : new WaitCollectState(target, targetSize);
                        NUtils.getUI().core.addTask(wcs);
                        if (inventoryReachedStop()) {
                            cancelHarvestIfNeeded(gui);
                            return Results.SUCCESS();
                        }
                    } else {
                        NUtils.getUI().core.addTask(new NFlowerMenuIsClosed());
                        return Results.FAIL();
                    }
                } else {
                    fm.wdgmsg("cl", -1);
                    NUtils.getUI().core.addTask(new NFlowerMenuIsClosed());
                    return Results.FAIL();
                }
            }
            else
            {
                return Results.FAIL();
            }

        }
        while (wcs!=null && wcs.getState()!= WaitCollectState.State.NOITEMSFORCOLLECT && !inventoryReachedStop());
        if (inventoryReachedStop())
            cancelHarvestIfNeeded(gui);
        return Results.SUCCESS();
    }
}
