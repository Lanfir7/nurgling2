package nurgling.tasks;

import nurgling.*;

public class FindNFlowerMenu extends NTask
{
    final long startTime;
    final long startFrame;

    public FindNFlowerMenu()
    {
        startTime = System.currentTimeMillis();
        startFrame = NUtils.getTickId();
    }

    @Override
    public boolean check() {
        NFlowerMenu found = (NFlowerMenu) NUtils.getUI().findInRoot(NFlowerMenu.class);
        res = (found != null && found.visible()) ? found : null;
        return res != null || (NUtils.getTickId() - startFrame) > 240 || System.currentTimeMillis() - startTime > 4000;
    }

    NFlowerMenu res = null;

    public NFlowerMenu getResult(){
        return res;
    }
}
