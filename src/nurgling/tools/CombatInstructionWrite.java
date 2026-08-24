package nurgling.tools;

import haven.Widget;

/**
 * Vanilla FightWnd: parchment RMB on a learned combat move
 * sends {@code itemact(actionId, modflags)} to the fight widget.
 */
public final class CombatInstructionWrite {
    private CombatInstructionWrite() {}

    public static boolean iteminteract(Widget fightWnd, int actionId, int modflags) {
        fightWnd.wdgmsg("itemact", actionId, modflags);
        return true;
    }
}
