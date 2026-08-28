package nurgling.actions;

import haven.*;
import haven.res.ui.tt.wear.Wear;
import nurgling.*;
import nurgling.tasks.*;
import nurgling.widgets.TableInventoryExtension;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoSaveTableware implements Action
{
    public final AtomicBoolean stop = new AtomicBoolean(false);
    NInventory tableInv = null;
    NInventory scInv = null;

    enum TakeOff { TO_INVENTORY, DROP }

    public AutoSaveTableware()
    {
        stop.set(false);
    }

    /** Remaining wear is `m - d`; take off at 1 left so the next eat cannot break it. */
    static boolean shouldRemove(int d, int m) {
        return m > 0 && m - d <= 1;
    }

    static boolean shouldRemove(Wear w) {
        return w != null && shouldRemove(w.d, w.m);
    }

    /** Shift-click `transfer` can land in the table's full food grid; invxf targets the bag. */
    static TakeOff takeOffMode(int freeSlots) {
        return freeSlots > 0 ? TakeOff.TO_INVENTORY : TakeOff.DROP;
    }

    static boolean canWatch(boolean feast, boolean hasTable) {
        return feast && hasTable;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        while (!stop.get())
        {
            tableInv = null;
            scInv = null;
            NUtils.addTask(new NTask()
            {
                @Override
                public boolean check()
                {
                    return stop.get() || (NUtils.getGameUI() != null && findTableInventory());
                }
            });

            if (stop.get())
            {
                return Results.SUCCESS();
            }

            if (tableInv != null)
            {
                ArrayList<WItem> items = tableInv.getItems();
                if (scInv != null)
                    items.addAll(scInv.getItems());
                for (WItem witem : items)
                {
                    if (!(witem.item instanceof NGItem))
                        continue;
                    witem.item.info();
                    if (!shouldRemove(((NGItem) witem.item).getInfo(Wear.class)))
                        continue;
                    takeOff(gui, witem);
                    if (stop.get())
                        throw new InterruptedException();
                }
            }
        }

        return Results.SUCCESS();
    }

    private void takeOff(NGameUI gui, WItem witem) throws InterruptedException
    {
        int id = witem.item.wdgid();
        TakeOff mode = takeOffMode(freeSlots(gui, witem));
        sendTakeOff(gui, witem, mode);
        waitGone(id);
        if (stillHere(id) && mode == TakeOff.TO_INVENTORY)
        {
            sendTakeOff(gui, witem, TakeOff.DROP);
            waitGone(id);
        }
    }

    private static int freeSlots(NGameUI gui, WItem witem) throws InterruptedException
    {
        if (gui == null || gui.getInventory() == null)
            return 0;
        return gui.getInventory().getNumberFreeCoord(witem);
    }

    private static void sendTakeOff(NGameUI gui, WItem witem, TakeOff mode)
    {
        if (mode == TakeOff.TO_INVENTORY && gui != null && gui.getInventory() != null)
            witem.item.wdgmsg("invxf", gui.getInventory().wdgid(), 1);
        else
            witem.item.wdgmsg("drop", Coord.z);
    }

    private void waitGone(int id) throws InterruptedException
    {
        try {
            NUtils.addTask(new ISRemoved(id, false));
        } catch (InterruptedException e) {
            if (stop.get() || Thread.currentThread().isInterrupted())
                throw e;
        }
    }

    private static boolean stillHere(int id)
    {
        NUI ui = NUtils.getUI();
        return ui != null && ui.getwidget(id) != null;
    }

    private boolean findTableInventory()
    {
        NInventory tableCand = null;
        NInventory scCand = null;
        boolean isFeast = false;

        for (Widget w = NUtils.getGameUI().lchild; w != null; w = w.prev)
        {
            if (w instanceof Window)
            {
                Window wnd = (Window) w;
                if (((Window) w).cap != null && TableInventoryExtension.isTableWindowCap(((Window) w).cap))
                {
                    for (Widget child : wnd.children())
                    {
                        if (child instanceof NInventory)
                        {
                            NInventory cand = ((NInventory) child);
                            if (cand.isz.y * cand.isz.x == 9)
                                tableCand = (NInventory) child;
                            if (cand.isz.y * cand.isz.x == 2)
                                scCand = (NInventory) child;
                        } else if (child instanceof Button)
                        {
                            Button b = (Button) child;
                            if (b.text != null && TableInventoryExtension.isFeastText(b.text.text))
                            {
                                isFeast = true;
                            }
                        }
                    }
                }
            }
        }
        if (canWatch(isFeast, tableCand != null))
        {
            tableInv = tableCand;
            scInv = scCand;
            return true;
        }

        return false;
    }

}
