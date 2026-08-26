package nurgling.tasks;

import haven.WItem;
import nurgling.NUtils;

public class WaitFreeHand extends NTask
{

    public WaitFreeHand()
    {
        this(200, true);
    }

    public WaitFreeHand(int maxCounter, boolean criticalOnTimeout)
    {
        infinite = false;
        this.maxCounter = maxCounter;
        this.criticalOnTimeout = criticalOnTimeout;
    }

    @Override
    public boolean check() {
        WItem res;
        return (res = NUtils.getGameUI().vhand) == null;
    }
}
