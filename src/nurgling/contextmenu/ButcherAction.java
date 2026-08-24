package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.Butcher;
import nurgling.actions.bots.ButcherTarget;
import nurgling.i18n.L10n;

public class ButcherAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return ButcherTarget.isCarcass(gob);
    }

    @Override
    public String label() {
        return L10n.get("context.butcher");
    }

    @Override
    public Action create(Gob gob) {
        return new Butcher(gob);
    }
}
