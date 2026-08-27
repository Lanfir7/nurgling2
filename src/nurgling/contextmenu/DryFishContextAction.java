package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.DFrameFishAction;

public class DryFishContextAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return DryingFrameGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.dry_fish");
    }

    @Override
    public Action create(Gob gob) {
        return new DFrameFishAction();
    }
}
