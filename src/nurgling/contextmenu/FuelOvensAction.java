package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.FuelOvens;

/** Gob-menu macro: select an oven area, add four branches to each oven, then light it. */
public class FuelOvensAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && OvenGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.fuel_ovens");
    }

    @Override
    public Action create(Gob gob) {
        return new FuelOvens();
    }
}
