package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.BoughBee;
import nurgling.actions.bots.BoughBeeMaterials;

public class BoughBeeAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return BoughBeeMaterials.isWildHive(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.boughbee");
    }

    @Override
    public Action create(Gob gob) {
        return new BoughBee(gob);
    }
}
