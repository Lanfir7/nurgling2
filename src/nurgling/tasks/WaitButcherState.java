package nurgling.tasks;

import haven.Coord;
import haven.Following;
import haven.Gob;
import nurgling.NUtils;

public class WaitButcherState extends NTask
{
    public WaitButcherState(Coord itemSize)
    {
        this.player = NUtils.player();
        this.itemSize = itemSize;
    }

    final Coord itemSize;

    Gob player;
    boolean seenButcher;
    int ticks;

    public enum State
    {
        WORKING,
        NOFREESPACE,
        READY
    }

    State state = State.WORKING;

    static boolean isIdle(String pose) {
        return pose != null && pose.contains("gfx/borka/idle");
    }

    static boolean isButcherPose(String pose) {
        return pose != null && pose.contains("gfx/borka/butcher");
    }

    public static boolean isMounted(Gob gob) {
        return gob != null && gob.getattr(Following.class) != null;
    }

    /** WaitPose equivalent: butcher started, or timed out on idle / mount. */
    public static boolean workStarted(String pose, boolean mounted, int ticks) {
        if (isButcherPose(pose)) {
            return true;
        }
        return ticks >= 200 && (mounted || isIdle(pose));
    }

    public static State resolve(String pose, boolean noFreeSpace, boolean mounted, boolean seenButcher, int ticks) {
        if (isIdle(pose)) {
            return State.READY;
        }
        if (noFreeSpace) {
            return State.NOFREESPACE;
        }
        if (isButcherPose(pose)) {
            return State.WORKING;
        }
        if (mounted && (seenButcher || ticks >= 200)) {
            return State.READY;
        }
        return State.WORKING;
    }

    @Override
    public boolean check() {
        String pose = player != null ? player.pose() : null;
        if (isButcherPose(pose)) {
            seenButcher = true;
        }
        boolean noFreeSpace = NUtils.getGameUI() != null
                && NUtils.getGameUI().getInventory() != null
                && NUtils.getGameUI().getInventory().calcNumberFreeCoord(itemSize) == 0;
        state = resolve(pose, noFreeSpace, isMounted(player), seenButcher, ticks++);
        return state != State.WORKING;
    }

    public State getState() {
        return state;
    }
}
