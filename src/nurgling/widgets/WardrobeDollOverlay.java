package nurgling.widgets;

import haven.Avaview;
import haven.Coord;
import haven.Equipory;
import haven.Frame;
import haven.Inventory;
import haven.WItem;
import haven.Widget;
import haven.Window;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Equipment-style characteristics drawn over a Wardrobe paper-doll,
 * sourced from items on that doll — not the player's Equipory.
 * Hosted as a sibling of the doll on Equipory/host, not as an Avaview child
 * (Avaview skips {@code super.draw} when avatar images are present).
 */
public class WardrobeDollOverlay extends EquipmentStatsWidget {
    private Widget doll;

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
            else if (ch instanceof Inventory) {
                for (WItem w : ch.children(WItem.class))
                    items.add(w);
            }
        }
        return items.toArray(new WItem[0]);
    }

    static WardrobeDollOverlay findOverlay(Widget host) {
        if (host == null)
            return null;
        for (Widget ch = host.child; ch != null; ch = ch.next) {
            if (ch instanceof WardrobeDollOverlay)
                return (WardrobeDollOverlay) ch;
        }
        return null;
    }

    static boolean attachAsSibling(Widget doll, Widget overlay) {
        if (doll == null || overlay == null)
            return false;
        Widget host = resolveDollHost(doll);
        if (host == null || host == doll)
            return false;
        Coord pos = doll.parentpos(host);
        overlay.resize(doll.sz);
        if (overlay.parent != host)
            host.add(overlay, pos);
        else
            overlay.move(pos);
        if (overlay instanceof WardrobeDollOverlay)
            ((WardrobeDollOverlay) overlay).bindDoll(doll);
        overlay.raise();
        return true;
    }

    static void syncOverlayToDoll(Widget overlay, Widget doll) {
        if (overlay == null || doll == null || overlay.parent == null || doll.parent == null)
            return;
        overlay.move(doll.parentpos(overlay.parent));
        overlay.resize(doll.sz);
    }

    public static boolean installOn(Widget doll) {
        if (doll == null)
            return false;
        Window window = doll.getparent(Window.class);
        if (!isWardrobeWindow(window))
            return false;
        Widget host = resolveDollHost(doll);
        if (host == null || host == doll)
            return false;
        WardrobeDollOverlay existing = findOverlay(host);
        if (existing != null) {
            existing.bindDoll(doll);
            existing.syncToDoll();
            existing.raise();
            return true;
        }
        WardrobeDollOverlay overlay = new WardrobeDollOverlay(doll.sz);
        overlay.bindDoll(doll);
        return attachAsSibling(doll, overlay);
    }

    public static boolean installOn(Avaview ava) {
        return installOn((Widget) ava);
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

    void bindDoll(Widget doll) {
        this.doll = doll;
    }

    void syncToDoll() {
        syncOverlayToDoll(this, doll);
    }

    @Override
    public void presize() {
        syncToDoll();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        syncToDoll();
        Widget host = parent;
        if (host != null)
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
