package nurgling.tasks;

import haven.Coord;
import haven.GItem;
import haven.Gob;
import haven.WItem;
import haven.Widget;
import nurgling.NGItem;
import nurgling.NUtils;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

public class WaitCollectState extends NTask
{
    public WaitCollectState(Gob target, Coord targetCoord)
    {
        this.player = NUtils.player();
        this.target = target;
        this.targetCoord = targetCoord;
    }

    public WaitCollectState(Gob target, Coord targetCoord, NAlias stopItems, int stopAt)
    {
        this(target, targetCoord);
        this.stopItems = stopItems;
        this.stopAt = stopAt;
    }

    Gob player;
    Gob target;
    Coord targetCoord;
    NAlias stopItems = null;
    int stopAt = 0;

    public enum State
    {
        WORKING,
        NOFREESPACE,
        NOITEMSFORCOLLECT
    }

    State state = State.WORKING;
    @Override
    public boolean check() {
        if (stopAt > 0 && stopItems != null && countPieces(stopItems) >= stopAt) {
            state = State.NOITEMSFORCOLLECT;
            return true;
        }
        String cpose = NUtils.player().pose();
        if (cpose != null && cpose.contains("gfx/borka/idle")) {
            state = State.NOITEMSFORCOLLECT;
        } else if (NUtils.getGameUI().getInventory().calcNumberFreeCoord(targetCoord) == 0) {
            state = State.NOFREESPACE;
        }
        return state != State.WORKING;
    }

    public State getState() {
        return state;
    }

    static int countPieces(NAlias name) {
        int count = 0;
        Widget inv = NUtils.getGameUI().getInventory();
        for (Widget widget = inv.child; widget != null; widget = widget.next) {
            if (!(widget instanceof WItem))
                continue;
            WItem item = (WItem) widget;
            String itemName = ((NGItem) item.item).name();
            if (itemName == null || !NParser.checkName(itemName, name))
                continue;
            GItem.Amount amount = ((NGItem) item.item).getInfo(GItem.Amount.class);
            count += (amount != null && amount.itemnum() > 0) ? amount.itemnum() : 1;
        }
        return count;
    }
}
