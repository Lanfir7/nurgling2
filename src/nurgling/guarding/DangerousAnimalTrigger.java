package nurgling.guarding;

import haven.Gob;
import haven.Homing;
import haven.Moving;
import nurgling.conf.NAreaRad;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

/** Fires if a dangerous animal (per each Ring Settings entry's own {@code dangerous} flag, not just visibility) is within range of the player. */
public class DangerousAnimalTrigger implements GuardTrigger {
    private String lastReason = "";

    @Override
    public boolean check(GuardContext ctx) throws InterruptedException {
        Gob player = ctx.player();
        if (player == null) {
            return false;
        }
        for (NAreaRad rad : ctx.animalRads()) {
            if (!rad.isActiveThreat(ctx.ignoreBats)) {
                continue;
            }
            for (Gob animal : Finder.findGobs(player.rc, new NAlias(rad.name), null, rad.triggerDist()))
                if (!NAreaRad.isDownOrDead(animal)) { lastReason = "dangerous animal (" + rad.name + ") nearby"; return true; }
        }
        synchronized (ctx.gui.ui.sess.glob.oc) {
            for (Gob gob : ctx.gui.ui.sess.glob.oc) {
                Moving moving = gob.getattr(Moving.class);
                if (!(moving instanceof Homing) || ((Homing)moving).tgt != player.id || gob.ngob == null || gob.ngob.name == null) continue;
                for (NAreaRad rad : ctx.animalRads())
                    if (rad.isActiveThreat(ctx.ignoreBats) && NParser.checkName(gob.ngob.name, new NAlias(rad.name))) {
                        lastReason = "dangerous animal (" + rad.name + ") is chasing you"; return true;
                    }
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return lastReason;
    }
}
