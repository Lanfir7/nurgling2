package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tasks.NTask;
import nurgling.widgets.NMakewindow;

/**
 * Shared wait / window checks for a single vanilla {@code make 0} craft.
 */
public final class CraftMake {
    private static final int MAX_WAIT_FOR_PROG = 100;
    private static final int MAX_TICKS_AFTER_PROG = 50;

    private CraftMake() {}

    public static boolean windowOpen(NMakewindow mwnd) {
        return mwnd != null && mwnd.parent != null;
    }

    /**
     * After {@code wdgmsg("make", 0)}: wait for the progress bar to appear and
     * finish, or for an instant craft / error / closed window.
     *
     * @return {@code false} if the recipe window closed or the make failed
     */
    public static boolean waitOne(NGameUI gui, NMakewindow mwnd) throws InterruptedException {
        if (!windowOpen(mwnd) || gui == null)
            return false;

        final boolean[] progAppeared = {false};
        NUtils.addTask(new NTask() {
            {
                infinite = false;
                maxCounter = MAX_WAIT_FOR_PROG;
                criticalOnTimeout = false;
            }

            @Override
            public boolean check() {
                if (!windowOpen(mwnd))
                    return true;
                if (NUtils.getUI() != null && NUtils.getUI().getLastError() != null)
                    return true;
                if (gui.prog != null && gui.prog.prog > 0) {
                    progAppeared[0] = true;
                    return true;
                }
                return false;
            }
        });

        if (!windowOpen(mwnd))
            return false;
        if (NUtils.getUI() != null && NUtils.getUI().getLastError() != null)
            return false;
        if (!progAppeared[0])
            return true;

        NUtils.addTask(new NTask() {
            private int ticksAfterProgGone = 0;

            @Override
            public boolean check() {
                if (!windowOpen(mwnd))
                    return true;
                if (NUtils.getUI() != null && NUtils.getUI().getLastError() != null)
                    return true;
                if (gui.prog != null && gui.prog.visible) {
                    ticksAfterProgGone = 0;
                    return false;
                }
                ticksAfterProgGone++;
                return ticksAfterProgGone >= MAX_TICKS_AFTER_PROG;
            }
        });

        if (!windowOpen(mwnd))
            return false;
        return NUtils.getUI() == null || NUtils.getUI().getLastError() == null;
    }
}
