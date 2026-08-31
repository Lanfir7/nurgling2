package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.FriedFish;

/** Gob-menu macro: start spit-roast on a bonfire (`gfx/terobjs/pow`). */
public class SpitRoastContextAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && PowGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.spit_roast");
    }

    @Override
    public Action create(Gob gob) {
        return new FriedFish();
    }
}
