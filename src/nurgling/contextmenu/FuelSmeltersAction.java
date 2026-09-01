package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.FuelSmelters;

/** Gob-menu macro: select a smelter area, fuel each Ore/Smith's smelter with coal, then light. */
public class FuelSmeltersAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && SmelterGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.fuel_smelters");
    }

    @Override
    public Action create(Gob gob) {
        return new FuelSmelters();
    }
}
