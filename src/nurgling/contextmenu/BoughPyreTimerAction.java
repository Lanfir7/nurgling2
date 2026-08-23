package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.actions.bots.BoughBee;
import nurgling.actions.bots.BoughBeeMaterials;

public class BoughPyreTimerAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return BoughBeeMaterials.isBoughPyreGob(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.boughpyre_timer");
    }

    @Override
    public Action create(Gob gob) {
        return gui -> {
            BoughBee.placePyreTimer(gui, gob);
            return Results.SUCCESS();
        };
    }
}
