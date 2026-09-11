package nurgling.actions.bots;

import haven.WItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.actions.SelectFlowerAction;
import nurgling.actions.TakeItems2;
import nurgling.areas.NContext;
import nurgling.tasks.WaitTicks;
import nurgling.tools.NBuffChecker;

import java.util.ArrayList;
import java.util.Map;

/** Applies Tansy from its configured Take area, rubbing it on skin repeatedly until the "Scent of
 *  Tansy" buff (which keeps midges - and the swamp fever risk they carry - away) reaches a target
 *  stack count, rather than just a one-shot "apply if missing". The buff's count rises on each
 *  application and falls on each midge bite, so topping it up to a safe margin lasts longer than a
 *  single application. Shared by the standalone bot and the Scheduler step wrapper. */
public class ApplyTansyIfMissing implements Action {

    private static final String ITEM_NAME = "Tansy";
    private static final String FLOWER_ACTION = "Rub on skin";
    private static final int DEFAULT_TARGET_STACKS = 10;
    // Gives the buff's stack count a moment to register server-side before the next re-check.
    private static final int APPLY_SETTLE_TICKS = 20;
    // Overlay loading can return -1 briefly; retry a couple of times, then stop instead of
    // draining the Take area while the count never resolves.
    static final int MAX_CONSECUTIVE_UNKNOWN_COUNTS = 2;

    static boolean isBuffCountUnknown(int count) {
        return count < 0;
    }

    static boolean failedStackGrowth(int previous, int observed) {
        if (isBuffCountUnknown(observed))
            return false;
        return observed <= previous;
    }

    static boolean shouldStopOnUnknownCount(int consecutiveUnknown) {
        return consecutiveUnknown >= MAX_CONSECUTIVE_UNKNOWN_COUNTS;
    }

    private final int targetStacks;

    public ApplyTansyIfMissing() {
        this.targetStacks = DEFAULT_TARGET_STACKS;
    }

    public ApplyTansyIfMissing(Map<String, Object> settings) {
        int target = DEFAULT_TARGET_STACKS;
        if (settings != null && settings.containsKey("targetStacks")) {
            Object t = settings.get("targetStacks");
            if (t instanceof Number) {
                target = ((Number) t).intValue();
            }
        }
        this.targetStacks = target;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NContext context = new NContext(gui);
        // getInStorages (called inside TakeItems2) only ever looks at areas already registered
        // into NContext's own inAreas map - it does no discovery itself. addInItem is what
        // actually finds the existing area tagged with this item and registers it, matching the
        // pattern every other bot using NContext+TakeItems2 already follows (e.g. BakerAction's
        // addInItem(doughName, null)).
        context.addInItem(ITEM_NAME, null);

        int count = NBuffChecker.getScentOfTansyCount();
        boolean appliedAny = false;
        int consecutiveUnknown = 0;

        // Observable applies still raise count toward targetStacks. Persistent -1/loading is
        // capped separately so overlay stalls cannot drain the Take area.
        while (count < targetStacks) {
            Results takeResult = new TakeItems2(context, ITEM_NAME, 1).run(gui);
            if (!takeResult.IsSuccess()) {
                // No more Tansy available - keep whatever we already applied this run.
                break;
            }

            ArrayList<WItem> items = NUtils.getGameUI().getInventory().getItems(ITEM_NAME);
            if (items.isEmpty()) {
                break;
            }
            new SelectFlowerAction(FLOWER_ACTION, items.get(0)).run(gui);
            appliedAny = true;

            NUtils.getUI().core.addTask(new WaitTicks(APPLY_SETTLE_TICKS));
            int newCount = NBuffChecker.getScentOfTansyCount();
            if (isBuffCountUnknown(newCount)) {
                // Overlay still loading: wait again and re-read instead of treating -1 as a
                // failed stack growth that would stop after one rub.
                NUtils.getUI().core.addTask(new WaitTicks(APPLY_SETTLE_TICKS));
                newCount = NBuffChecker.getScentOfTansyCount();
            }
            if (isBuffCountUnknown(newCount)) {
                consecutiveUnknown++;
                if (shouldStopOnUnknownCount(consecutiveUnknown))
                    break;
                continue;
            }
            consecutiveUnknown = 0;
            if (failedStackGrowth(count, newCount)) {
                // The stack count didn't move - already capped.
                // Stop instead of spinning through the rest of the Take area for nothing.
                break;
            }
            count = newCount;
        }

        return (appliedAny || count >= targetStacks) ? Results.SUCCESS() : Results.FAIL();
    }
}
