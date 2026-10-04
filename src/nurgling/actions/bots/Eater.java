package nurgling.actions.bots;

import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.widgets.FoodContainer;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

public class Eater implements Action {

    /** Same scale as {@link FindAndEatItems}: {@code energy * 10000}. 8000 is 80%. */
    static final int ENERGY_TARGET = 8000;

    boolean oz = false;

    public Eater(boolean oz) {
        this.oz = oz;
    }

    public Eater() {
        this.oz = false;
    }

    /**
     * True while energy is still below the eat target, so a food trip can help.
     * {@code energy} is the 0..1 meter. A missing meter is negative and still counts as hungry.
     */
    static boolean needsFood(double energy) {
        return energy * 10000 < ENERGY_TARGET;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (!needsFood(NUtils.getEnergy())) {
            gui.msg("Eater: done");
            return Results.SUCCESS();
        }

        ArrayList<String> items = FoodContainer.getFoodNames();
        // Inventory first. FindAndEatItems visits the eat area only if energy is still short.
        new FindAndEatItems(new NContext(gui), items, ENERGY_TARGET).run(gui);
        if (!needsFood(NUtils.getEnergy())) {
            gui.msg("Eater: done");
            return Results.SUCCESS();
        }

        NArea nArea = NContext.findSpec(Specialisation.SpecName.eat.toString());
        if (nArea == null)
            nArea = NContext.findSpecGlobal(Specialisation.SpecName.eat.toString());
        gui.msg(nArea == null ? "Eater: no area with 'eat' spec" : "Eater: energy < 80%");
        return Results.FAIL();
    }
}
