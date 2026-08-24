package nurgling.actions.bots;

import haven.Coord;
import haven.Gob;
import haven.Resource;
import haven.UI;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NPrepBlocksProp;
import nurgling.tasks.WaitCheckable;
import nurgling.tasks.WaitPose;
import nurgling.tasks.WaitPrepBlocksState;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.PrepQuota;

import java.util.ArrayList;

public class PrepareBlocks implements Action {
    private static final NAlias BLOCKS = new NAlias("block");

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        nurgling.widgets.bots.PrepareBlocks w = null;
        NPrepBlocksProp prop = null;
        try {
            NUtils.getUI().core.addTask(new WaitCheckable( NUtils.getGameUI().add((w = new nurgling.widgets.bots.PrepareBlocks()), UI.scale(200,200))));
            prop = w.prop;
        }
        catch (InterruptedException e)
        {
            throw e;
        }
        finally {
            if(w!=null)
                w.destroy();
        }
        if(prop == null)
        {
            return Results.ERROR("No config");
        }
        NContext context = new NContext(gui);

        String logAreaId = context.createArea("Please select area with logs", Resource.loadsimg("baubles/prepLogs"));
        NArea logArea = context.goToAreaById(logAreaId);

        String pileAreaId = context.createArea("Please select area for piles", Resource.loadsimg("baubles/prepBlockP"));
        NArea pileArea = context.goToAreaById(pileAreaId);

        int piled = 0;
        int target = prop.count;

        ArrayList<Gob> logs;
        while (!(logs = Finder.findGobs(logArea, new NAlias("log"))).isEmpty())
        {
            logs.sort(NUtils.d_comp);
            Gob log = logs.get(0);
            while (Finder.findGob(log.id) != null) {
                if (PrepQuota.reached(target, gui.getInventory().getItems(BLOCKS).size(), piled)) {
                    piled = dumpPiles(gui, pileArea, piled);
                    if (piled < 0)
                        return Results.FAIL();
                    return Results.SUCCESS();
                }
                if (NUtils.getGameUI().getInventory().calcNumberFreeCoord(new Coord(1, 2)) == 0)
                {
                    piled = dumpPiles(gui, pileArea, piled);
                    if (piled < 0)
                        return Results.FAIL();
                }
                new PathFinder(log).run(gui);
                new Equip(new NAlias(prop.tool)).run(gui);
                new SelectFlowerAction("Chop into blocks", log).run(gui);
                NUtils.getUI().core.addTask(new WaitPose(NUtils.player(), "gfx/borka/choppan"));
                WaitPrepBlocksState wcs = new WaitPrepBlocksState(log, prop);
                NUtils.getUI().core.addTask(wcs);
                switch (wcs.getState()) {
                    case LOGNOTFOUND:
                        break;
                    case TIMEFORDRINK: {
                        new Drink(0.9, true).run(gui);
                        if(!new RestoreResources().run(gui).IsSuccess())
                            return Results.ERROR("Failed to restore resources");
                        break;
                    }
                    case NOFREESPACE: {
                        piled = dumpPiles(gui, pileArea, piled);
                        if (piled < 0)
                            return Results.FAIL();
                        break;
                    }
                    case DANGER:
                        return Results.ERROR("SOMETHING WRONG, STOP WORKING");
                    case WOUND_DANGER:
                        return Results.ERROR("Scrapes & Cuts wound damage too high! Stopping for safety.");

                }
                if (PrepQuota.reached(target, gui.getInventory().getItems(BLOCKS).size(), piled)) {
                    piled = dumpPiles(gui, pileArea, piled);
                    if (piled < 0)
                        return Results.FAIL();
                    return Results.SUCCESS();
                }
            }
        }
        piled = dumpPiles(gui, pileArea, piled);
        if (piled < 0)
            return Results.FAIL();
        return Results.SUCCESS();
    }

    private static int dumpPiles(NGameUI gui, NArea pileArea, int piled) throws InterruptedException {
        int before = gui.getInventory().getItems(BLOCKS).size();
        if (!new TransferToPiles(pileArea.getRCArea(), BLOCKS).run(gui).IsSuccess())
            return -1;
        return piled + Math.max(0, before - gui.getInventory().getItems(BLOCKS).size());
    }
}
