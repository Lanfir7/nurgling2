package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.NZoneMeasureTool;

public class ZoneMeasureTool implements Action {
    private static NZoneMeasureTool currentTool = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Toggle behavior: close if already open
        if (currentTool != null && currentTool.parent != null) {
            currentTool.wdgmsg("close");
            currentTool = null;
            return Results.SUCCESS();
        }

        // Create and display new tool window
        currentTool = new NZoneMeasureTool(gui);
        NUtils.addCentered(gui, currentTool);

        return Results.SUCCESS();
    }
}
