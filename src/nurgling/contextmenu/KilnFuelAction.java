package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.widgets.KilnFuelWindow;

/** Lookup-only kiln fuel table. Does not start a bot or add fuel. */
public class KilnFuelAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && KilnGobs.matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.kiln_fuel");
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
        KilnFuelWindow.open();
    }
}
