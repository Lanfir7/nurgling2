package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.Eater;

public class RestoreResources implements Action{

    private final Pair<Coord2d, Coord2d> waterArea;

    public RestoreResources() { this.waterArea = null; }

    public RestoreResources(Pair<Coord2d, Coord2d> waterArea) {
        this.waterArea = waterArea;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
            double stamina = NUtils.getStamina();
            if (stamina >= 0 && stamina < 0.5) {
                if (!new Drink(0.9, false).run(gui).IsSuccess()) {
                    if (waterArea != null) {
                        new FillWaterskins(waterArea).run(gui);
                    } else {
                        new FillWaterskinsGlobal().run(gui);
                    }
                    if (!new Drink(0.9, false).run(gui).IsSuccess()) {
                        return Results.ERROR("Failed to restore stamina - no water available");
                    }
                }
            }
            double energy = NUtils.getEnergy();
            if(energy >= 0 && energy < 0.35)
            {
                Eater eater = new Eater(true);
                Results eatResult = eater.run(gui);
                if (!eatResult.IsSuccess()) {
                    return Results.ERROR("Failed to restore energy - no food available");
                }
            }
            return Results.SUCCESS();
    }
}
