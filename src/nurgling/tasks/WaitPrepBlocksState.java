package nurgling.tasks;

import haven.Coord;
import haven.Gob;
import nurgling.NUtils;
import nurgling.conf.NPrepBlocksProp;
import nurgling.tools.Finder;
import nurgling.tools.NWoundChecker;
import nurgling.tools.PrepQuota;

public class WaitPrepBlocksState extends NTask
{
    public WaitPrepBlocksState(Gob log, NPrepBlocksProp prop)
    {
        this.player = NUtils.player();
        this.log = log;
        this.prop = prop;
    }


    Gob player;
    Gob log;
    NPrepBlocksProp prop;
    public enum State
    {
        WORKING,
        LOGNOTFOUND,
        TIMEFORDRINK,
        DANGER,
        NOFREESPACE,
        WOUND_DANGER
    }

    State state = State.WORKING;
    @Override
    public boolean check() {
        int space = NUtils.getGameUI().getInventory().calcNumberFreeCoord(new Coord(1, 2));
        boolean noSpace = space <= 1 && space >= 0
                && (NUtils.getGameUI().getInventory().calcFreeSpace() <= 2 || space == 0);
        boolean wound = prop.checkWounds && NWoundChecker.hasScrapesAndCutsAboveThreshold(prop.woundDamageThreshold);
        PrepQuota.Halt halt = PrepQuota.pickBlocks(
                Finder.findGob(log.id) == null,
                NUtils.getEnergy() < 0.22,
                noSpace,
                NUtils.getStamina() <= 0.45,
                wound);
        state = toState(halt);
        return state != State.WORKING;
    }

    static State toState(PrepQuota.Halt halt) {
        switch (halt) {
            case LOGNOTFOUND:
                return State.LOGNOTFOUND;
            case TIMEFORDRINK:
                return State.TIMEFORDRINK;
            case DANGER:
                return State.DANGER;
            case NOFREESPACE:
                return State.NOFREESPACE;
            case WOUND_DANGER:
                return State.WOUND_DANGER;
            default:
                return State.WORKING;
        }
    }

    public State getState() {
        return state;
    }
}
