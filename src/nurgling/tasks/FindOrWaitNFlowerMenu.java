package nurgling.tasks;

import nurgling.NFlowerMenu;
import nurgling.NUtils;

public class FindOrWaitNFlowerMenu extends NTask
{

    int count = 0;
    public FindOrWaitNFlowerMenu()
    {
    }

    @Override
    public boolean check()
    {
        count++;
        NFlowerMenu found = (NFlowerMenu) NUtils.getUI().findInRoot(NFlowerMenu.class);
        res = (found != null && found.visible()) ? found : null;
        return res != null && res.opts.length>0 || count > 50;
    }

    NFlowerMenu res = null;

    public NFlowerMenu getResult(){
        return res;
    }
}
