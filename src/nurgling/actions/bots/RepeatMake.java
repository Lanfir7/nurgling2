package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.widgets.NMakewindow;

/**
 * Repeat vanilla {@code wdgmsg("make", 0)} exactly {@code count} times, waiting
 * for each craft to finish. Does not fetch ingredients (not the auto Craft bot).
 */
public class RepeatMake implements Action {
    private final NMakewindow mwnd;
    private final int count;

    public RepeatMake(NMakewindow mwnd, int count) {
        this.mwnd = mwnd;
        this.count = count;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            if (!CraftMake.windowOpen(mwnd))
                return Results.SUCCESS();
            if (NUtils.getUI() != null)
                NUtils.getUI().dropLastError();
            mwnd.wdgmsg("make", 0);
            if (!CraftMake.waitOne(gui, mwnd))
                return Results.SUCCESS();
        }
        return Results.SUCCESS();
    }
}
