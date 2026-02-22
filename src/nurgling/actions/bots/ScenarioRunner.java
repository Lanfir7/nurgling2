package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.*;
import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.scenarios.*;
import nurgling.actions.bots.registry.BotRegistry;

public class ScenarioRunner implements Action {
    private final Scenario scenario;

    public ScenarioRunner(Scenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        while (true) {
            boolean restart = false;
            for (BotStep step : scenario.getSteps()) {
                BotDescriptor desc = BotRegistry.byId(step.getId());
                Action bot = (desc != null) ? desc.instantiate(step.getSettings()) : null;
                if (bot == null) {
                    gui.msg("ScenarioRunner: Unknown bot key: " + step.getId());
                    return Results.FAIL();
                }
                Results result = bot.run(gui);
                if (result.isCycle) {
                    restart = true;
                    break;
                }
                if (!result.IsSuccess()) {
                    gui.msg("ScenarioRunner: Bot failed: " + step.getId());
                    return result;
                }
            }
            if (!restart) {
                return Results.SUCCESS();
            }
        }
    }
}
