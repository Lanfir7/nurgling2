package nurgling;

import haven.*;
import nurgling.widgets.NBotsMenu;

import java.awt.image.BufferedImage;

/** Tooltip for a hotbelt slot icon — same source as the matching menu/button tip. */
public final class NToolBeltTip {
    private NToolBeltTip() {}

    public static Object from(Object item, boolean detailed) {
        if(item == null)
            return null;
        if(item instanceof NBotsMenu.NButton)
            return ((NBotsMenu.NButton)item).btn.tooltip(Coord.z, null);
        if(item instanceof Widget)
            return ((Widget)item).tooltip(Coord.z, null);
        if(item instanceof GameUI.PagBeltSlot) {
            BufferedImage ti = ((GameUI.PagBeltSlot)item).pag.button().rendertt(detailed);
            return ti == null ? null : new TexI(ti);
        }
        if(item instanceof GameUI.ResBeltSlot)
            return resourceTip((GameUI.ResBeltSlot)item);
        return null;
    }

    private static Object resourceTip(GameUI.ResBeltSlot slot) {
        Resource r = slot.rdt.res.get();
        Resource.Tooltip tt = r.layer(Resource.tooltip);
        String name = tt != null ? tt.text() : r.basename();
        BufferedImage img = NRecipeTooltip.build(name, null);
        return img == null ? null : new TexI(img);
    }
}
