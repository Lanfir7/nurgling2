package nurgling.actions;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.Eater;
import nurgling.areas.NGlobalCoord;

public class RestoreResources implements Action
{

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        NGlobalCoord bookmark = NUtils.bookmarkHere();
        boolean navigated = false;

        double stamina = NUtils.getStamina();
        if (stamina >= 0 && stamina < 0.5) {
            if (shouldRefillAtWaterZone(true, FillWaterskins.checkIfNeed())) {
                navigated = true;
                Results fill = new FillWaterskinsGlobal().run(gui);
                if (!fill.IsSuccess()) {
                    return Results.ERROR("Failed to restore stamina - no water available");
                }
            }
            if (!new Drink(0.9, false).run(gui).IsSuccess()) {
                navigated = true;
                Results fill = new FillWaterskinsGlobal().run(gui);
                if (!fill.IsSuccess() || !new Drink(0.9, false).run(gui).IsSuccess()) {
                    return Results.ERROR("Failed to restore stamina - no water available");
                }
            }
        }

        double energy = NUtils.getEnergy();
        if (energy >= 0 && energy < 0.35) {
            navigated = true;
            Results eatResult = new Eater(true).run(gui);
            if (!eatResult.IsSuccess()) {
                return Results.ERROR("Failed to restore energy - no food available");
            }
        }

        if (navigated) {
            NUtils.navigateTo(bookmark);
        }

        return Results.SUCCESS();
    }

    static boolean shouldRefillAtWaterZone(boolean staminaLow, boolean waterskinsEmpty) {
        return staminaLow && waterskinsEmpty;
    }
}
