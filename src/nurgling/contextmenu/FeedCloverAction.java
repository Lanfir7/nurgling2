package nurgling.contextmenu;

import haven.Gob;
import nurgling.actions.Action;
import nurgling.actions.bots.FeedClover;

public class FeedCloverAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return FeedClover.isWildHorse(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.feed_clover");
    }

    @Override
    public Action create(Gob gob) {
        return new FeedClover(gob);
    }
}
