package nurgling.actions.bots;

import haven.Coord;
import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.SimpleRoutesWidget;

public class OpenSimpleRoutesWidgetBot implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.ui == null) {
            return Results.FAIL();
        }

        if (gui.simpleRoutesWidget == null) {
            gui.simpleRoutesWidget = new SimpleRoutesWidget();
            gui.add(gui.simpleRoutesWidget, new Coord(100, 200));
        }
        gui.simpleRoutesWidget.show();
        gui.msg("Simple Routes opened");
        return Results.SUCCESS();
    }
}
