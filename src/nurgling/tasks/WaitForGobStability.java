package nurgling.tasks;

import haven.Gob;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.List;

public class WaitForGobStability extends NTask {
    private final long stabilityWindow;
    private final long maxWaitTime;

    private int lastGobCount = -1;
    private long lastGobCountChangeTime = -1;
    private long startTime = -1;

    public WaitForGobStability() {
        this(600, 5000);
    }

    public WaitForGobStability(long stabilityWindow, long maxWaitTime) {
        this.stabilityWindow = stabilityWindow;
        this.maxWaitTime = maxWaitTime;
    }

    @Override
    public boolean check() {
        List<Gob> nearbyGobs = Finder.findGobs(new NAlias(""));
        return checkAt(nearbyGobs.size(), System.currentTimeMillis());
    }

    boolean checkAt(int currentGobCount, long currentTime) {
        if (startTime == -1) {
            startTime = currentTime;
        }

        // If gob count changed, reset stability timer
        if (currentGobCount != lastGobCount) {
            lastGobCount = currentGobCount;
            lastGobCountChangeTime = currentTime;
        }

        // Stop once the gob list settles, but never let continuous loading keep the bot blocked.
        return currentTime - lastGobCountChangeTime >= stabilityWindow
                || currentTime - startTime >= maxWaitTime;
    }
}
