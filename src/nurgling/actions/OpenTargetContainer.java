package nurgling.actions;

import haven.*;
import haven.error.FileLogger;
import static haven.OCache.posres;
import nurgling.*;
import nurgling.tasks.*;
import nurgling.tools.Container;
import nurgling.tools.Finder;

public class OpenTargetContainer implements Action
{
    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        long startedAt = System.currentTimeMillis();
        Window already = NUtils.getGameUI().getWindow(name);
        boolean ownsExisting = already != null && isOwnedBy(gui, already, gob);
        if ("Stockpile".equals(name)) {
            FileLogger.log(stockpileTrace("start", already,
                    "ownsExisting=" + ownsExisting));
        }
        if(already != null && !ownsExisting)
        {
            /* A container window is keyed only by its caption, so an open window from a
             * previously visited container of the same kind suppresses the click below and
             * the bot silently keeps working with that container instead of this one. Two
             * cupboards standing a couple of tiles apart stay in range of each other, so
             * every second one in a row was never opened at all. */
            already.wdgmsg("close");
            gui.ui.core.addTask(new WindowIsClosed(already));
            if ("Stockpile".equals(name)) {
                FileLogger.log(stockpileTrace("stale-window-closed", null, ""));
            }
            already = null;
        }
        if(already == null)
        {
            /* Inventory's factory binds the new window to core.getLastActions().gob, which
             * only real UI clicks populate - a bot's wdgmsg goes straight to the server and
             * leaves every bot-opened container unbound. Set it here so the window that is
             * about to arrive knows which gob it belongs to. */
            gui.ui.core.setLastAction(gob);
            if ("Stockpile".equals(name)) {
                FileLogger.log(stockpileTrace("click-sent", null, ""));
            }
            gui.map.wdgmsg ( "click", Coord.z, gob.rc.floor ( posres ), 3, 0, 0, ( int ) gob.id,
                    gob.rc.floor ( posres ), 0, -1 );
        }
        switch (name)
        {
            case "Stockpile":
                NTask wait = boundedStockpileWait
                        ? new FindStockpileAfterApproach(name, 200)
                        : new FindNISBox(name);
                gui.ui.core.addTask(wait);
                FileLogger.log(stockpileTrace("wait-finished",
                        NUtils.getGameUI().getWindow(name),
                        "elapsedMs=" + (System.currentTimeMillis() - startedAt)
                                + " stockpile=" + (gui.getStockpile() != null)));
                break;
            case "Barter Stand":
                gui.ui.core.addTask(new FindBarterStand());
                break;
            case "Barrel":
                gui.ui.core.addTask(new FindBarrel());
                break;
            case "Cauldron":
                if((gob.ngob.getModelAttribute() & 2) != 0)//"lit"
                    new SelectFlowerAction("Open", gob, true).run(gui);
                gui.ui.core.addTask(new FindNInventory(name));
                break;
            case "Study Desk":
            case "Fine Study Desk":
            case "Grand Study Desk":
                // A non-owned desk pops an "Open"/"Take possession" flower menu instead of
                // opening directly; only a fresh click (not a reused window) can trigger one.
                if (already == null) {
                    NFlowerMenu deskMenu = NUtils.findFlowerMenu();
                    if (deskMenu != null) {
                        deskMenu.chooseOpt("Open");
                        gui.ui.core.addTask(new NFlowerMenuIsClosed());
                    }
                }
                gui.ui.core.addTask(new FindNInventory(name));
                break;
            default:
                gui.ui.core.addTask(new FindNInventory(name));
        }
        if ("Stockpile".equals(name)) {
            NISBox stockpile = gui.getStockpile();
            if (stockpile == null) {
                FileLogger.log(stockpileTrace("failed", NUtils.getGameUI().getWindow(name),
                        "elapsedMs=" + (System.currentTimeMillis() - startedAt)));
                return Results.FAIL();
            }
            stockpile.parentGob = gob;
            FileLogger.log(stockpileTrace("success", NUtils.getGameUI().getWindow(name),
                    "elapsedMs=" + (System.currentTimeMillis() - startedAt)
                            + " free=" + stockpile.getFreeSpace()));
            monitoring.StockpileStorageTracker.observeOpenPile(
                    gob, stockpile.stockpileItemName(), stockpile.stockpileCount());
        }
        if(cont!=null)
        {
            cont.update();
        }
        return Results.SUCCESS();
    }

    private String stockpileTrace(String phase, Window window, String extra) {
        Gob player = NUtils.player();
        return "[StockpileOpen] phase=" + phase
                + " thread=" + Thread.currentThread().getName()
                + " target=" + (gob != null ? gob.id : -1)
                + " targetRc=" + (gob != null ? gob.rc : null)
                + " playerRc=" + (player != null ? player.rc : null)
                + " window=" + (window != null)
                + (extra == null || extra.isEmpty() ? "" : " " + extra);
    }

    /**
     * Whether an already open window is the one belonging to gob, and so may be reused
     * instead of being closed and reopened.
     *
     * NInventory-backed containers and stockpiles carry the gob binding. Other ISBoxes and
     * barter stands are left alone and keep the previous behaviour.
     *
     * Barrels are the exception: their window holds only a RelCont, so there is nothing to match
     * against, and TakeFromBarrel leaves its window open. An area with several barrels then had
     * every barrel after the first suppress its own click and silently reuse the previous barrel's
     * window. Always reopen those.
     */
    private static boolean isOwnedBy(NGameUI gui, Window wnd, Gob gob)
    {
        NInventory inv = null;
        NISBox stockpile = null;
        for(Widget w = wnd.lchild; w != null; w = w.prev)
        {
            if(w instanceof NInventory)
            {
                inv = (NInventory) w;
                break;
            }
            if(w instanceof NISBox)
                stockpile = (NISBox) w;
        }
        if(inv == null) {
            if ("Stockpile".equals(wnd.cap))
                return stockpile != null && stockpile.parentGob != null && gob != null
                        && sameGobId(stockpile.parentGob.id, gob.id);
            return !"Barrel".equals(wnd.cap);
        }
        return inv.parentGob != null && gob != null && sameGobId(inv.parentGob.id, gob.id);
    }

    static boolean sameGobId(long ownerGobId, long requestedGobId) {
        return ownerGobId >= 0 && ownerGobId == requestedGobId;
    }

    static final class StockpileOpenWaitBudget {
        private final int stationaryLimit;
        private int stationaryTicks;
        private boolean timedOut;

        StockpileOpenWaitBudget(int stationaryLimit) {
            this.stationaryLimit = Math.max(1, stationaryLimit);
        }

        boolean tick(boolean windowReady, boolean moving) {
            if (windowReady) {
                return true;
            }
            if (moving) {
                stationaryTicks = 0;
                return false;
            }
            timedOut = ++stationaryTicks >= stationaryLimit;
            return timedOut;
        }

        boolean timedOut() {
            return timedOut;
        }
    }

    private static final class FindStockpileAfterApproach extends NTask {
        private final String name;
        private final StockpileOpenWaitBudget budget;

        FindStockpileAfterApproach(String name, int stationaryLimit) {
            this.name = name;
            this.budget = new StockpileOpenWaitBudget(stationaryLimit);
            this.infinite = true;
            this.criticalOnTimeout = false;
        }

        @Override
        public boolean check() {
            Window window = NUtils.getGameUI().getWindow(name);
            Gob player = NUtils.player();
            boolean moving = player != null && player.getv() > 0.1;
            return budget.tick(window != null, moving);
        }
    }

    public OpenTargetContainer(String name, Gob gob)
    {
        this.name = name;
        this.gob = gob;
    }

    public OpenTargetContainer(String name, Gob gob, boolean boundedStockpileWait)
    {
        this(name, gob);
        this.boundedStockpileWait = boundedStockpileWait;
    }

    public OpenTargetContainer(Container container)
    {
        this.name = container.cap;
        if(container.gobHash!=null && !container.gobHash.isEmpty())
        {
            this.gob = Finder.findGob(container.gobHash);
        }
        else
        {
            this.gob = Finder.findGob(container.gobid);
        }
        this.cont = container;
    }

    String name;
    Gob gob;
    Container cont = null;
    boolean boundedStockpileWait = false;
}
