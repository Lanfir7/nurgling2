package nurgling.actions;

import haven.Gob;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitPose;
import nurgling.tools.Finder;
import nurgling.tools.ForageChainPick;
import nurgling.tools.NAlias;

import java.util.ArrayList;

/** After a SHIFT+Pick of a herb gob, keep picking nearby gobs of the same resource. */
public class ForageChainPickAction implements Action {
    private final String gobName;
    private final String actionName;
    private final long firstGobId;

    public ForageChainPickAction(String gobName, String actionName, long firstGobId) {
        this.gobName = gobName;
        this.actionName = actionName;
        this.firstGobId = firstGobId;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        waitGobGone(firstGobId);
        waitIdle();

        NAlias alias = new NAlias(gobName);
        ArrayList<Long> skip = new ArrayList<Long>();
        skip.add(firstGobId);

        while (true) {
            if (inventoryBlocked(gui)) return Results.SUCCESS();
            Gob player = NUtils.player();
            if (player == null || player.rc == null) return Results.SUCCESS();

            Gob next = Finder.findGob(player.rc, alias, null, ForageChainPick.RADIUS, skip);
            if (next == null) return Results.SUCCESS();
            skip.add(next.id);
            if (next.ngob == null || !gobName.equals(next.ngob.name)) continue;

            PathFinder pf = new PathFinder(next);
            pf.waterMode = true;
            if (!pf.run(gui).IsSuccess()) return Results.SUCCESS();
            if (!new SelectFlowerAction(actionName, next).run(gui).IsSuccess()) return Results.SUCCESS();
            waitGobGone(next.id);
            waitIdle();
        }
    }

    static boolean inventoryBlocked(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.vhand != null) return true;
        NInventory inv = gui.getInventory();
        return inv == null || inv.getFreeSpace() <= 0;
    }

    private static void waitGobGone(long gobId) throws InterruptedException {
        NUtils.getUI().core.addTask(new NTask() {
            {
                infinite = false;
                maxCounter = 200;
                criticalOnTimeout = false;
            }
            @Override
            public boolean check() {
                return Finder.findGob(gobId) == null;
            }
        });
    }

    private static void waitIdle() throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) return;
        NUtils.getUI().core.addTask(new WaitPose(player, "gfx/borka/idle"));
    }
}
