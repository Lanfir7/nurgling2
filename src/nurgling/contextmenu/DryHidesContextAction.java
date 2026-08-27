package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.DFrameHidesAction;

public class DryHidesContextAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return DryingFrameGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.dry_hides");
    }

    @Override
    public Action create(Gob gob) {
        return new DFrameHidesAction();
    }
}
