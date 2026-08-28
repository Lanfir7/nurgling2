package nurgling.actions;

import nurgling.NUtils;
import nurgling.tasks.NTask;

public class WaitStockpile extends NTask {

    boolean exist = true;
    public WaitStockpile(boolean exist) {
        this.exist = exist;
    }

    public WaitStockpile(boolean exist, int maxCounter, boolean criticalOnTimeout) {
        this.exist = exist;
        this.infinite = false;
        this.maxCounter = Math.max(1, maxCounter);
        this.criticalOnTimeout = criticalOnTimeout;
    }

    @Override
    public boolean check() {
        return (NUtils.getGameUI().getStockpile()!=null)==exist;
    }
}
