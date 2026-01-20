package nurgling.actions.bots;

import haven.Coord;
import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.bots.QuickBarrageBotWnd;

public class QuickBarrageBot implements Action {
    private static QuickBarrageBotWnd currentWindow = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Если окно уже открыто, закрываем его
        if (currentWindow != null && currentWindow.parent != null) {
            currentWindow.reqdestroy();
            currentWindow = null;
            return Results.SUCCESS();
        }

        // Создаем новое окно
        currentWindow = new QuickBarrageBotWnd(gui);
        Coord center = new Coord(gui.sz.x / 2 - currentWindow.sz.x / 2, gui.sz.y / 2 - currentWindow.sz.y / 2);
        gui.add(currentWindow, center);

        return Results.SUCCESS();
    }
}
