package nurgling.widgets;

import haven.Avaview;
import haven.Coord;
import haven.Equipory;
import haven.Frame;
import haven.WItem;
import haven.Widget;
import haven.Window;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Equipment-style characteristics drawn on a Wardrobe paper-doll (Avaview),
 * sourced from items on that doll — not the player's Equipory.
 */
public class WardrobeDollOverlay extends EquipmentStatsWidget {
    public WardrobeDollOverlay(Coord sz) {
        super(sz);
    }

    public static boolean isWardrobeCap(String cap) {
        if (cap == null)
            return false;
        String title = cap.trim();
        return title.equalsIgnoreCase("Wardrobe") || title.equals("Гардероб");
    }

    public static boolean isWardrobeWindow(Window window) {
        return window != null && isWardrobeCap(window.cap);
    }

    public static Widget resolveDollHost(Widget doll) {
        if (doll == null)
            return null;
        Widget host = doll.parent;
        while (host instanceof Frame)
            host = host.parent;
        return host;
    }

    public static WItem[] itemsOnDoll(Widget host) {
        if (host instanceof Equipory) {
            Equipory eq = (Equipory) host;
            if (host instanceof NEquipory)
                return ((NEquipory) host).quickslots;
            List<WItem> items = new ArrayList<>();
            for (Collection<WItem> slots : eq.wmap.values()) {
                if (slots != null)
                    items.addAll(slots);
            }
            return items.toArray(new WItem[0]);
        }
        List<WItem> items = new ArrayList<>();
        if (host == null)
            return items.toArray(new WItem[0]);
        for (Widget ch = host.child; ch != null; ch = ch.next) {
            if (ch instanceof WItem)
                items.add((WItem) ch);
        }
        return items.toArray(new WItem[0]);
    }

    public static boolean installOn(Avaview ava) {
        if (ava == null)
            return false;
        Window window = ava.getparent(Window.class);
        if (!isWardrobeWindow(window))
            return false;
        for (Widget ch = ava.child; ch != null; ch = ch.next) {
            if (ch instanceof WardrobeDollOverlay)
                return true;
        }
        WardrobeDollOverlay overlay = ava.add(new WardrobeDollOverlay(ava.sz), Coord.z);
        overlay.raise();
        return true;
    }

    public static boolean installFrom(Widget root) {
        if (root == null)
            return false;
        boolean installed = false;
        if (root instanceof Avaview)
            installed = installOn((Avaview) root);
        for (Avaview ava : root.children(Avaview.class))
            installed |= installOn(ava);
        return installed;
    }

    @Override
    public void presize() {
        if (parent != null)
            resize(parent.sz);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        Widget host = resolveDollHost(parent);
        updateStatsFromItems(itemsOnDoll(host));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        return false;
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        return false;
    }
}
