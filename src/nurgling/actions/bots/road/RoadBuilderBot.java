package nurgling.actions.bots.road;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.routes.ForagerPath;
import nurgling.tasks.WaitCheckable;
import nurgling.widgets.bots.RoadBuilderWnd;

public class RoadBuilderBot implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        RoadBuilderWnd w = null;
        ForagerPath path;
        boolean stone;
        try {
            NUtils.getUI().core.addTask(new WaitCheckable(NUtils.addCentered(w = new RoadBuilderWnd())));
            if (w.cancelled || w.route == null)
                return Results.FAIL();
            path = w.route;
            stone = w.stone;
        } finally {
            if (w != null)
                w.destroy();
        }
        return new RoadBuilder(path, stone).run(gui);
    }
}
