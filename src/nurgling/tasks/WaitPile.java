package nurgling.tasks;

import haven.Coord2d;
import haven.Following;
import haven.Gob;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

public class WaitPile extends NTask {
    Coord2d pos;

    public WaitPile(Coord2d pos) {
        this.pos = pos;
    }

    public static WaitPile withSoftTimeout(Coord2d pos, int ticks) {
        WaitPile wait = new WaitPile(pos);
        wait.infinite = false;
        wait.maxCounter = Math.max(1, ticks);
        wait.criticalOnTimeout = false;
        return wait;
    }


    @Override
    public boolean check() {
        return acceptCandidates(Finder.findGobs(pos));
    }

    boolean acceptCandidates(Iterable<Gob> candidates) {
        for (Gob candidate : candidates) {
            if (acceptCandidate(candidate)) {
                return true;
            }
        }
        pile = null;
        return false;
    }

    boolean acceptCandidate(Gob gob) {
        if (gob != null && gob.ngob != null && gob.ngob.name != null &&
                NParser.checkName(gob.ngob.name, new NAlias("stockpile"))) {
            pile = gob;
            return true;
        }
        pile = null;
        return false;
    }

    Gob pile = null;

    public Gob getPile() {
        return pile;
    }
}


