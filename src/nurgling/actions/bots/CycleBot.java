package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;

import java.util.Map;

public class CycleBot implements Action {

    public CycleBot() {
    }

    public CycleBot(Map<String, Object> settings) {
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        return Results.CYCLE();
    }
}
