package nurgling.tasks;

import nurgling.*;

public class NFlowerMenuIsClosed extends NTask
{

    public NFlowerMenuIsClosed()
    {
    }

    @Override
    public boolean check()
    {
        NFlowerMenu menu = (NFlowerMenu) NUtils.getUI().findInRoot(NFlowerMenu.class);
        return menu == null || !menu.visible();
    }
}
