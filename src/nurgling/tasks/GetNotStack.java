package nurgling.tasks;

import haven.WItem;
import haven.Widget;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.tools.NAlias;
import nurgling.tools.StackSupporter;

public class GetNotStack extends NTask
{
    NAlias name;
    NInventory inventory;

    final int maxSize;

    public GetNotStack(NInventory inventory, NAlias name)
    {
        this.name = name;
        this.inventory = inventory;
        maxSize = StackSupporter.getFullStackSize(name.getDefault());
    }


    @Override
    public boolean check()
    {
        result = null;
        return !checkContainer(inventory.child);
    }

    private boolean checkContainer(Widget first) {
        for (Widget widget = first; widget != null; widget = widget.next) {
            if (widget instanceof WItem) {
                WItem item = (WItem) widget;

                if (!NGItem.validateItem(item)) {
                    return true;
                } else {
                    if (matchesRequestedName(name, ((NGItem)item.item).name())) {
                        if (
                                item.item.contents == null
                                && StackSupporter.isStackable((NInventory) item.item.parent, ((NGItem) item.item).name())
                        ) {
                            result = item;
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    static boolean matchesRequestedName(NAlias requestedName, String candidateName) {
        return requestedName.matchesExact(candidateName);
    }

    private WItem result = null;

    public WItem getResult(){
        return result;
    }
}
