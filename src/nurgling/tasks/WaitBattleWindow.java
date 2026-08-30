package nurgling.tasks;

import haven.Fightview;
import nurgling.NGameUI;
import nurgling.NUtils;

public class WaitBattleWindow extends NTask
{
    public WaitBattleWindow(long id, boolean noWait)
    {
        this.id = id;
    }

    public WaitBattleWindow()
    {
        this.id = -1;
    }

    long id;

    Fightview fightview()
    {
        NGameUI gui = NUtils.getGameUI();
        return (gui == null) ? null : gui.fv;
    }

    @Override
    public boolean check()
    {
        Fightview fv = fightview();
        if(fv == null)
            return false;

        if(id==-1)
        {
            return !fv.lsrel.isEmpty();
        }
        else
        {
            for(Fightview.Relation rel : fv.lsrel)
            {
                if(rel.gobid == id)
                    return true;
            }
        }
        return false;
    }
}
