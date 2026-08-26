package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.NFollowDistanceTool;

public class FollowDistanceTool implements Action {

    private static NFollowDistanceTool currentTool = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (currentTool != null && currentTool.parent != null) {
            currentTool.stopTool();
            currentTool.reqdestroy();
            currentTool = null;
            return Results.SUCCESS();
        }

        currentTool = new NFollowDistanceTool(gui);
        NUtils.addCentered(gui, currentTool);
        currentTool.start();

        return Results.SUCCESS();
    }
}
