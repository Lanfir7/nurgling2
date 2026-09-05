package nurgling.tasks;

import haven.*;
import nurgling.*;
import nurgling.tools.Finder;

public class FindNISBox extends NTask
{
    public FindNISBox(String name)
    {
        this(name, (Gob) null);
    }

    public FindNISBox(String name, Gob gob)
    {
        this.name = name;
        this.gobid = gob == null ? -1 : gob.id;
        this.tracked = gob != null;
    }

    public FindNISBox(String name, int maxCounter)
    {
        this(name);
        this.infinite = false;
        this.maxCounter = maxCounter;
        this.criticalOnTimeout = false;
    }

    String name;
    long gobid;
    boolean tracked;
    private static final int GONE_FOR = 150;
    private int missing = 0;

    static boolean targetGone(boolean tracked, boolean targetPresent, int consecutiveMissing)
    {
        return tracked && !targetPresent && consecutiveMissing >= GONE_FOR;
    }

    @Override
    public boolean check()
    {
        Window wnd = NUtils.getGameUI().getWindow(name);
        if(wnd == null) {
            boolean targetPresent = !tracked || Finder.findGob(gobid) != null;
            if(targetPresent) {
                missing = 0;
                return false;
            }
            return targetGone(tracked, false, ++missing);
        }
        missing = 0;
        for(Widget w2 = wnd.lchild ; w2 !=null ; w2= w2.prev )
        {
            if ( w2 instanceof NISBox ) {
                return true;
            }
        }
        return false;
    }
}
