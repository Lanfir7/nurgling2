package nurgling.actions.bots;

import haven.*;
import haven.res.lib.tree.TreeScale;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NContext;
import nurgling.conf.NChopperProp;
import nurgling.tasks.*;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.bots.Checkable;
import nurgling.pf.Graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Chopper implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        nurgling.widgets.bots.Chopper w = null;
        NChopperProp prop = null;
        try {
            NUtils.getUI().core.addTask(new WaitCheckable( NUtils.getGameUI().add((w = new nurgling.widgets.bots.Chopper()), UI.scale(200,200))));
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
        if((prop.stumps && prop.shovel==null) || (prop.tool == null))
        {
            return Results.ERROR("Not set required tools");
        }

        NContext context = new NContext(gui);

        String treeArea = context.createArea("Please select area for deforestation", Resource.loadsimg("baubles/chopperArea"));

        NAlias pattern = prop.stumps ? new NAlias(new ArrayList<String>(List.of("gfx/terobjs/tree")),new ArrayList<String>(Arrays.asList("log","oldtrunk"))) :
                new NAlias(new ArrayList<String>(List.of("gfx/terobjs/tree")),new ArrayList<String>(Arrays.asList("log", "oldtrunk", "stump")));

        if(!prop.bushes)
        {
            pattern.exceptions.add("bushes");
        }
        else
        {
            pattern.keys.add("gfx/terobjs/bushes");
        }
        pattern.buildCaches(); // Rebuild caches after modifying keys/exceptions
        context.navigateToAreaIfNeeded(treeArea);
        ArrayList<Gob> trees;
        while (!(trees = context.getGobsLocal(treeArea,pattern)).isEmpty()) {
            trees.sort(NUtils.y_min_comp);

            if(prop.ngrowth)
            {
                ArrayList<Gob> for_remove = new ArrayList<>();
                for (Gob tree: trees)
                {
                    if(tree.getattr(TreeScale.class)!=null)
                    {
                        for_remove.add(tree);
                    }
                }
                trees.removeAll(for_remove);
                if(trees.isEmpty())
                    break;
            }

            Gob tree = trees.get(0);
            long treeId = tree.id;
            context.setLastPos(tree.rc);

            PathFinder.Mode approachMode;
            switch (prop.approachDirection) {
                case 0: approachMode = PathFinder.Mode.Y_MIN; break;
                case 2: approachMode = PathFinder.Mode.X_MAX; break;
                case 3: approachMode = PathFinder.Mode.X_MIN; break;
                default: approachMode = PathFinder.Mode.Y_MAX; break;
            }

            moveToSideCell(gui, tree, approachMode, prop.approachDirection);

            while (tree!=null && context.getGobLocal(treeArea, treeId) != null) {
                boolean chopped = false;
                if (NParser.isIt(tree, new NAlias("stump"))) {
                    if(!new Equip(new NAlias(prop.shovel)).run(gui).IsSuccess())
                        return Results.ERROR("Equipment not found: " + prop.shovel);
                    moveToSideCell(gui, tree, approachMode, prop.approachDirection);
                    for (MenuGrid.Pagina pag : NUtils.getGameUI().menu.paginae) {
                        if (pag.button() != null && pag.button().name().equals("Destroy")) {
                            pag.button().use(new MenuGrid.Interaction(1, 0));
                            break;
                        }
                    }
                    NUtils.getUI().core.addTask(new GetCurs("mine"));
                    gui.map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(OCache.posres),
                            1, 0, 0, (int) tree.id, tree.rc.floor(OCache.posres), 0, -1);
                    NUtils.getUI().core.addTask(new WaitPose(NUtils.player(), "gfx/borka/shoveldig"));
                    NUtils.rclickGob(tree);
                    NUtils.getUI().core.addTask(new GetCurs("arw"));
                } else {
                    chopped = true;
                    if(tree.getattr(TreeScale.class)!=null)
                    {
                        chopped = false;
                    }
                    if(tree.ngob.name.startsWith("gfx/terobjs/bushes"))
                        chopped = false;
                    if(!new Equip(new NAlias(prop.tool)).run(gui).IsSuccess())
                        return Results.ERROR("Equipment not found: " + prop.tool);
                    moveToSideCell(gui, tree, approachMode, prop.approachDirection);
                    new SelectFlowerAction("Chop", tree).run(gui);
                    NUtils.getUI().core.addTask(new WaitPoseOrNoGob(NUtils.player(), tree, "gfx/borka/treechop"));
                }
                WaitChopperState wcs = new WaitChopperState(tree, prop);
                NUtils.getUI().core.addTask(wcs);
                switch (wcs.getState()) {
                    case TREENOTFOUND:
                        break;
                    case TIMEFORDRINK:
                    case TIMEFOREAT: {
                        context.setLastPos(tree.rc);
                        if (!new RestoreResources().run(gui).IsSuccess()) {
                            return Results.ERROR("No Drink or Eat");
                        }
                        tree = context.getGobLocal(treeArea, treeId);

                        break;
                    }
                    case DANGER:
                        return Results.ERROR("SOMETHING WRONG, STOP WORKING");
                    case WOUND_DANGER:
                        return Results.ERROR("Scrapes & Cuts wound damage too high! Stopping for safety.");

                }
                if(chopped && context.getGobLocal(treeArea, treeId) == null) {
                    NUtils.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return Finder.findGob(context.getLastPosCoordLocal())!=null;
                        }
                    });
                }
            }

        }
        new RunToSafe().run(gui);
        return Results.SUCCESS();
    }

    private void moveToSideCell(NGameUI gui, Gob target, PathFinder.Mode approachMode, int approachDirection) throws InterruptedException {
        PathFinder pf = new PathFinder(target);
        pf.setMode(approachMode);
        pf.isHardMode = true;
        pf.run(gui);

        Coord2d pl = NUtils.player().rc;
        boolean wrongSide;
        switch (approachDirection) {
            case 0: wrongSide = pl.y >= target.rc.y; break;
            case 2: wrongSide = pl.x <= target.rc.x; break;
            case 3: wrongSide = pl.x >= target.rc.x; break;
            default: wrongSide = pl.y <= target.rc.y; break;
        }
        if (wrongSide) {
            PathFinder pfSide = new PathFinder(target);
            pfSide.setMode(approachMode);
            pfSide.isHardMode = true;
            pfSide.skipDN = true;
            LinkedList<Graph.Vertex> sidePath = pfSide.construct();
            if (sidePath != null && !sidePath.isEmpty()) {
                Coord2d rawPos = nurgling.pf.Utils.pfGridToWorld(sidePath.getLast().pos);
                Coord tileIdx = rawPos.div(MCache.tilesz).floor();
                Coord2d tileCenter = new Coord2d(
                        tileIdx.x * MCache.tilesz.x + MCache.tilehsz.x,
                        tileIdx.y * MCache.tilesz.y + MCache.tilehsz.y
                );
                new GoTo(tileCenter).run(gui);
            }
        }
    }
}
