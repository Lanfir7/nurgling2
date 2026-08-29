package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.widgets.HTableTimesWindow;

/** Lookup-only herbalist table times. Does not start a bot. */
public class HTableTimesAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && HTableGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.htable_times");
    }

    @Override
    public Action create(Gob gob) {
        return null;
    }

    @Override
    public boolean isUiAction() {
        return true;
    }

    @Override
    public void performUi(Gob gob) {
        HTableTimesWindow.open();
    }
}
