package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.bots.QuickBarrageBotWnd;

public class QuickBarrageBot implements Action {
    private static QuickBarrageBotWnd currentWindow = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (currentWindow != null && currentWindow.parent != null) {
            if (currentWindow.isRunning()) {
                currentWindow.stopBot();
            } else {
                currentWindow.stopBot();
                currentWindow.reqdestroy();
                currentWindow = null;
            }
            return Results.SUCCESS();
        }

        currentWindow = new QuickBarrageBotWnd(gui);
        NUtils.addCentered(gui, currentWindow);
        currentWindow.startBot();

        return Results.SUCCESS();
    }
}
