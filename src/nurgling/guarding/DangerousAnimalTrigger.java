package nurgling.guarding;

import haven.Fightview;
import haven.Gob;
import haven.Homing;
import haven.Moving;
import nurgling.conf.NAreaRad;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.List;

/** Fires if a dangerous animal (per each Ring Settings entry's own {@code dangerous} flag, not just visibility) is within range of the player. */
public class DangerousAnimalTrigger implements GuardTrigger {
    private String lastReason = "";

    @Override
    public boolean check(GuardContext ctx) throws InterruptedException {
        Gob player = ctx.player();
        if (player == null || ctx.gui == null || ctx.gui.ui == null || ctx.gui.ui.sess == null) {
            return false;
        }
        synchronized (ctx.gui.ui.sess.glob.oc) {
            for (Gob gob : ctx.gui.ui.sess.glob.oc) {
                if (NAreaRad.isDownOrDead(gob))
                    continue;
                String name = NAreaRad.resourceName(gob);
                if (name == null)
                    continue;
                Moving moving = gob.getattr(Moving.class);
                boolean chasing = moving instanceof Homing && ((Homing) moving).tgt == player.id;
                double dist = gob.rc.dist(player.rc);
                for (NAreaRad rad : ctx.animalRads()) {
                    if (!rad.isActiveThreat(ctx.ignoreBats) || !NParser.checkName(name, new NAlias(rad.name)))
                        continue;
                    if (dist < rad.triggerDist()) {
                        lastReason = "dangerous animal (" + rad.name + ") nearby";
                        return true;
                    }
                    if (chasing) {
                        lastReason = "dangerous animal (" + rad.name + ") is chasing you";
                        return true;
                    }
                }
            }
        }
        return fightingThreat(ctx);
    }

    /** A scorpion (or any other listed threat) already in the fight list is hitting now, even from outside the ring. */
    private boolean fightingThreat(GuardContext ctx) {
        Fightview fv = ctx.gui.fv;
        if (fv == null)
            return false;
        List<Fightview.Relation> rels;
        try {
            rels = new ArrayList<>(fv.lsrel);
        } catch (java.util.ConcurrentModificationException e) {
            return false;
        }
        for (Fightview.Relation rel : rels) {
            Gob gob = ctx.gui.ui.sess.glob.oc.getgob(rel.gobid);
            if (gob == null || NAreaRad.isDownOrDead(gob))
                continue;
            String name = NAreaRad.resourceName(gob);
            if (name == null)
                continue;
            for (NAreaRad rad : ctx.animalRads()) {
                if (rad.isActiveThreat(ctx.ignoreBats) && NParser.checkName(name, new NAlias(rad.name))) {
                    lastReason = "dangerous animal (" + rad.name + ") is attacking you";
                    return true;
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
