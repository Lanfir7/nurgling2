package nurgling.tasks;

import nurgling.NGameUI;
import nurgling.NUtils;

public class WaitPlob extends NTask {
    private final boolean requireReady;

    public WaitPlob() {
        this(true);
    }

    public WaitPlob(boolean requireReady) {
        this.requireReady = requireReady;
    }

    public static WaitPlob withTimeout(boolean requireReady, int ticks) {
        WaitPlob wait = new WaitPlob(requireReady);
        wait.infinite = false;
        wait.maxCounter = ticks;
        return wait;
    }

    public static WaitPlob withSoftTimeout(boolean requireReady, int ticks) {
        WaitPlob wait = withTimeout(requireReady, ticks);
        wait.criticalOnTimeout = false;
        return wait;
    }

    /**
     * {@code requireReady=false}: server already sent "place", even if the GL ghost
     * never finishes loading in a background/headless session.
     */
    public static boolean isSatisfied(boolean hasMap, boolean placingExists, boolean placingReady, boolean requireReady) {
        if (!hasMap || !placingExists) {
            return false;
        }
        return !requireReady || placingReady;
    }

    @Override
    public boolean check() {
        NGameUI gui = NUtils.getGameUI();
        boolean hasMap = gui != null && gui.map != null;
        boolean placingExists = hasMap && gui.map.placing != null;
        boolean placingReady = placingExists && gui.map.placing.ready();
        return isSatisfied(hasMap, placingExists, placingReady, requireReady);
    }
}
