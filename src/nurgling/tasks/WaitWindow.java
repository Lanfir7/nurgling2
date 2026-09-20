package nurgling.tasks;

import haven.GItem;
import haven.WItem;
import haven.Widget;
import nurgling.NGItem;
import nurgling.NISBox;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;

public class WaitWindow extends NTask
{
    String name = null;

    GItem target = null;
    public WaitWindow(String name)
    {
        this.name = name;

    }

    public static WaitWindow withSoftTimeout(String name, int ticks) {
        WaitWindow wait = new WaitWindow(name);
        wait.infinite = false;
        wait.maxCounter = ticks;
        wait.criticalOnTimeout = false;
        return wait;
    }

    @Override
    public boolean check()
    {
        return NUtils.getGameUI().getWindow(name)!=null;
    }

}
