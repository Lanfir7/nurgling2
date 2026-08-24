package nurgling.tasks;

import haven.Coord;
import haven.Gob;
import nurgling.NUtils;
import nurgling.conf.NPrepBoardsProp;
import nurgling.tools.Finder;
import nurgling.tools.PrepQuota;

public class WaitPrepBoardsState extends NTask
{
    public WaitPrepBoardsState(Gob log, NPrepBoardsProp prop)
    {
        this.player = NUtils.player();
        this.log = log;
        this.prop = prop;
    }


    Gob player;
    Gob log;
    NPrepBoardsProp prop;
    public enum State
    {
        WORKING,
        LOGNOTFOUND,
        TIMEFORDRINK,
        DANGER,
        NOFREESPACE
    }

    State state = State.WORKING;
    @Override
    public boolean check() {
        int space = NUtils.getGameUI().getInventory().calcNumberFreeCoord(new Coord(4, 1));
        boolean noSpace = space <= 1 && space >= 0
                && (NUtils.getGameUI().getInventory().calcFreeSpace() <= 4 || space == 0);
        PrepQuota.Halt halt = PrepQuota.pickBoards(
                Finder.findGob(log.id) == null,
                NUtils.getEnergy() < 0.22,
                noSpace,
                NUtils.getStamina() <= 0.45);
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
            default:
                return State.WORKING;
        }
    }

    public State getState() {
        return state;
    }
}
