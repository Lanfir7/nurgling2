package nurgling.actions.bots;

import haven.error.FileLogger;
import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.FreeInventory2;
import nurgling.actions.Results;
import nurgling.areas.NContext;

public class FreeInvBot implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Results result = runFreeInventory(new FreeInventory2(new NContext(gui)), gui);
        FileLogger.log("[FreeInvBot] finished thread=" + Thread.currentThread().getName()
                + " success=" + result.IsSuccess());
        return result;
    }

    static Results runFreeInventory(Action freeInventory, NGameUI gui)
            throws InterruptedException {
        return freeInventory.run(gui);
    }
}
