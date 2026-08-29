package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.FuelKilns;

/** Gob-menu macro: select a kiln area, then fuel each kiln from the catalog. */
public class FuelKilnsAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && KilnGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.fuel_kilns");
    }

    @Override
    public Action create(Gob gob) {
        return new FuelKilns();
    }
}
