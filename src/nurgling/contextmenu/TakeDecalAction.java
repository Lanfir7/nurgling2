package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.i18n.L10n;
import nurgling.tools.DecalLock;

/** Removes a decal explicitly when ordinary decal clicks are locked. */
public class TakeDecalAction implements GobContextAction {
    @Override
    public boolean appliesTo(Gob gob) {
        return DecalLock.enabled() && DecalLock.findDecal(gob) != null;
    }

    @Override
    public String label() {
        return L10n.get("context.take_decal");
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
        Gob.Overlay decal = DecalLock.findDecal(gob);
        if (decal != null) {
            DecalLock.takeDecal(gob, decal);
        }
    }
}
