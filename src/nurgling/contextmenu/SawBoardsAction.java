package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.tools.PrepQuota;

public class SawBoardsAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return PrepQuota.isLog(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.saw_boards");
    }

    @Override
    public Action create(Gob gob) {
        return new nurgling.actions.bots.PrepareBoards();
    }
}
