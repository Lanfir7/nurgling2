package nurgling.widgets;

import haven.Area;
import haven.Coord;
import haven.Equipory;
import haven.GItem;
import haven.GOut;
import haven.GameUI;
import haven.Loading;
import haven.Resource;
import haven.Tex;
import haven.Text;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGItem;
import nurgling.NGameUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One window for every open pouch of the same kind. Each pouch stays its own
 * inventory, framed so the split between bags stays visible.
 */
public class MergedBagWindow extends Window {
    private static final Map<String, MergedBagWindow> OPEN = new HashMap<String, MergedBagWindow>();
    private static boolean opening;

    private final String typeKey;
    private final List<GItem.ContentsWindow> sections = new ArrayList<GItem.ContentsWindow>();
    private final Guides guides;
    private final Set<GItem> openRequested = Collections.newSetFromMap(new IdentityHashMap<GItem, Boolean>());
    private boolean closing;
    private Coord laidOut = Coord.z;

    public static class SectionDeco extends Window.Deco {
        public Area ca;

        public void iresize(Coord isz) {
            Coord ul = Window.wbox.btloff();
            ca = Area.sized(ul, isz);
            resize(ca.br.add(Window.wbox.bbroff()));
        }

        public Area contarea() {
            return ca;
        }

        public void draw(GOut g) {
            Window.wbox.draw(g, Coord.z, sz);
        }
    }

    public MergedBagWindow(String title, String typeKey) {
        super(Coord.z, title == null || title.isEmpty() ? typeKey : title);
        this.typeKey = typeKey;
        guides = new Guides();
        guides.z = -50;
        add(guides);
    }

    public static boolean tryOpen(GItem.ContentsWindow origin) {
        if (origin == null || origin.cont == null)
            return false;
        if (origin.parent instanceof MergedBagWindow) {
            MergedBagWindow live = (MergedBagWindow) origin.parent;
            if (!live.closing) {
                live.raise();
                return true;
            }
        }
        String key = bagKey(origin);
        if (key == null)
            return false;
        if (opening)
            return false;
        Widget gui = origin.parent;
        if (gui == null)
            return false;
        MergedBagWindow live = OPEN.get(key);
        if (live != null && live.parent != null && !live.closing) {
            live.embed(origin);
            live.sync();
            live.layout();
            live.raise();
            return true;
        }
        List<GItem> mates = carriedPouches(gui, key);
        if (mates.size() < 2)
            return false;
        opening = true;
        MergedBagWindow wnd = null;
        try {
            wnd = new MergedBagWindow(titleOf(origin, key), key);
            gui.add(wnd, Coord.z);
            wnd.posmem("merged-pouch/" + key);
            Coord anchor = origin.mergeAnchor();
            wnd.move(wnd.restorepos(anchor == null ? Coord.z : anchor));
            OPEN.put(key, wnd);
            wnd.embed(origin);
            wnd.sync();
            wnd.layout();
            wnd.raise();
        } finally {
            opening = false;
        }
        if (wnd != null)
            wnd.requestSiblings();
        return true;
    }

    public static boolean tryClose(GItem.ContentsWindow origin) {
        if (origin != null && origin.parent instanceof MergedBagWindow) {
            ((MergedBagWindow) origin.parent).reqclose();
            return true;
        }
        return false;
    }

    @Override
    public void reqclose() {
        closeAll(false);
    }

    @Override
    public void remove() {
        if (OPEN.get(typeKey) == this)
            OPEN.remove(typeKey);
        super.remove();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (closing || parent == null)
            return;
        sync();
        int carried = carriedPouches(parent, typeKey).size();
        if (carried < 2) {
            closeAll(!sections.isEmpty());
            return;
        }
        requestSiblings();
        if (!sections.isEmpty())
            layout();
    }

    @Override
    public void cdestroy(Widget ch) {
        super.cdestroy(ch);
        if (ch instanceof GItem.ContentsWindow)
            sections.remove(ch);
    }

    private void sync() {
        Iterator<GItem.ContentsWindow> it = sections.iterator();
        while (it.hasNext()) {
            GItem.ContentsWindow cw = it.next();
            if (cw.parent != this || cw.cont == null || cw.inv == null)
                it.remove();
        }
        if (parent == null)
            return;
        for (GItem item : carriedPouches(parent, typeKey)) {
            GItem.ContentsWindow cw = item.contentswnd;
            if (cw != null && cw.inv != null && !sections.contains(cw))
                embed(cw);
        }
        for (GItem.ContentsWindow cw : collect(parent, typeKey, null)) {
            if (!sections.contains(cw))
                embed(cw);
        }
    }

    private void embed(GItem.ContentsWindow cw) {
        if (cw == null || cw.inv == null || closing)
            return;
        cw.enterMerge();
        if (cw.parent != this) {
            if (cw.parent != null) {
                cw.unlink();
                cw.parent = null;
            }
            cw.parent = this;
            cw.link();
        }
        if (!sections.contains(cw))
            sections.add(cw);
    }

    private void layout() {
        int gap = UI.scale(18);
        int pad = UI.scale(8);
        int header = UI.scale(16);
        int thickness = Math.max(2, UI.scale(3));
        List<Coord> sizes = new ArrayList<Coord>();
        List<String> captions = new ArrayList<String>();
        for (int i = 0; i < sections.size(); i++) {
            GItem.ContentsWindow cw = sections.get(i);
            Coord sz = cw.sz == null ? Coord.of(1, 1) : cw.sz;
            sizes.add(Coord.of(Math.max(1, sz.x), Math.max(1, sz.y) + header));
            captions.add(sectionCaption(cw, i + 1));
        }
        List<MergedBagLayout.Box> boxes = MergedBagLayout.place(sizes, gap, UI.scale(680), pad);
        List<MergedBagLayout.Box> grids = new ArrayList<MergedBagLayout.Box>();
        for (int i = 0; i < boxes.size() && i < sections.size(); i++) {
            MergedBagLayout.Box box = boxes.get(i);
            GItem.ContentsWindow cw = sections.get(i);
            cw.c = Coord.of(box.x, box.y + header);
            Coord sz = cw.sz == null ? Coord.of(box.w, 1) : cw.sz;
            grids.add(new MergedBagLayout.Box(box.x, box.y + header, sz.x, sz.y));
        }
        Coord bounds = MergedBagLayout.bounds(boxes, pad);
        guides.seams = MergedBagLayout.seams(boxes, gap, thickness);
        guides.grids = grids;
        guides.captions = captions;
        guides.captionBoxes = boxes;
        guides.resize(bounds);
        guides.c = Coord.z;
        guides.raise();
        if (!bounds.equals(laidOut)) {
            laidOut = bounds;
            resize(bounds);
        }
    }

    private static String sectionCaption(GItem.ContentsWindow cw, int index) {
        if (cw != null && cw.cont instanceof NGItem) {
            Float quality = ((NGItem) cw.cont).quality;
            if (quality != null && quality > 0)
                return String.format(java.util.Locale.US, "%.1f", quality.floatValue());
        }
        return Integer.toString(index);
    }

    private void closeAll(boolean reopenSole) {
        if (closing)
            return;
        closing = true;
        Widget gui = parent;
        List<GItem.ContentsWindow> copy = new ArrayList<GItem.ContentsWindow>(sections);
        GItem.ContentsWindow sole = copy.size() == 1 ? copy.get(0) : null;
        sections.clear();
        if (OPEN.get(typeKey) == this)
            OPEN.remove(typeKey);
        for (GItem.ContentsWindow cw : copy) {
            if (cw.parent == this && gui != null) {
                cw.unlink();
                cw.parent = null;
                cw.parent = gui;
                cw.link();
            }
            cw.leaveMerge();
        }
        if (reopenSole && sole != null && sole.parent != null && sole.cont != null)
            sole.wndshow(true);
        reqdestroy();
    }

    private void requestSiblings() {
        GameUI game = getparent(GameUI.class);
        if (game != null && game.vhand != null)
            return;
        for (GItem item : carriedPouches(parent, typeKey)) {
            if (item.contentswnd != null || !openRequested.add(item))
                continue;
            item.wdgmsg("iact", Coord.z, 0);
        }
    }

    private static String titleOf(GItem.ContentsWindow origin, String key) {
        if (origin.cont != null && origin.cont.contentsnm != null && !origin.cont.contentsnm.isEmpty())
            return origin.cont.contentsnm;
        return key;
    }

    static String bagKey(GItem.ContentsWindow cw) {
        if (cw == null || cw.cont == null || cw.inv == null)
            return null;
        if (cw.inv instanceof haven.res.ui.stackinv.ItemStack)
            return null;
        return itemKey(cw.cont);
    }

    static String itemKey(GItem item) {
        if (item == null)
            return null;
        String itemName = item instanceof NGItem ? ((NGItem) item).name() : null;
        return PouchKinds.key(itemName, item.contentsnm, resourceName(item));
    }

    private static String resourceName(GItem item) {
        try {
            Resource res = item.getres();
            return res == null ? null : res.name;
        } catch (Loading ignored) {
            return null;
        }
    }

    private static List<GItem> carriedPouches(Widget gui, String key) {
        List<GItem> out = new ArrayList<GItem>();
        if (gui == null || key == null)
            return out;
        collectCarried(gui, key, out);
        return out;
    }

    private static void collectCarried(Widget widget, String key, List<GItem> out) {
        if (widget instanceof GItem) {
            GItem item = (GItem) widget;
            if (key.equals(itemKey(item)) && carried(item))
                out.add(item);
        }
        for (Widget child = widget.child; child != null; child = child.next)
            collectCarried(child, key, out);
    }

    /** Backpack and belt pouches. Pouches sitting in an open chest stay separate. */
    static boolean carried(GItem item) {
        if (item == null)
            return false;
        NGameUI gui = (item.ui != null && item.ui.gui instanceof NGameUI) ? (NGameUI) item.ui.gui : null;
        boolean own = false;
        boolean chest = false;
        for (Widget parent = item.parent; parent != null; parent = parent.parent) {
            if (gui != null && parent == gui.maininv)
                own = true;
            if (parent instanceof Equipory)
                own = true;
            if (parent instanceof Window && !(parent instanceof GItem.ContentsWindow) && !(parent instanceof MergedBagWindow)) {
                if (gui == null || gui.maininv == null || parent != gui.maininv.parent)
                    chest = true;
            }
        }
        return own || !chest;
    }

    private static List<GItem.ContentsWindow> collect(Widget gui, String key, GItem.ContentsWindow first) {
        List<GItem.ContentsWindow> out = new ArrayList<GItem.ContentsWindow>();
        if (belongs(first, key))
            out.add(first);
        if (gui == null)
            return out;
        for (Widget w = gui.child; w != null; w = w.next) {
            if (!(w instanceof GItem.ContentsWindow))
                continue;
            GItem.ContentsWindow cw = (GItem.ContentsWindow) w;
            if (cw == first || !belongs(cw, key))
                continue;
            out.add(cw);
        }
        return out;
    }

    private static boolean belongs(GItem.ContentsWindow cw, String key) {
        return key != null && key.equals(bagKey(cw)) && carried(cw.cont);
    }

    private static final class Guides extends Widget {
        private static final Text.Foundry CAP = new Text.Foundry(Text.sans, 12).aa(true);
        List<MergedBagLayout.Box> seams = new ArrayList<MergedBagLayout.Box>();
        List<MergedBagLayout.Box> grids = new ArrayList<MergedBagLayout.Box>();
        List<MergedBagLayout.Box> captionBoxes = new ArrayList<MergedBagLayout.Box>();
        List<String> captions = new ArrayList<String>();
        private final List<String> captionKeys = new ArrayList<String>();
        private final List<Tex> captionTex = new ArrayList<Tex>();

        public void draw(GOut g) {
            g.chcolor(186, 154, 72, 230);
            if (grids != null) {
                for (MergedBagLayout.Box grid : grids)
                    g.rect(Coord.of(grid.x - 2, grid.y - 2), Coord.of(grid.w + 4, grid.h + 4));
            }
            if (seams != null) {
                for (MergedBagLayout.Box seam : seams)
                    g.frect(Coord.of(seam.x, seam.y), Coord.of(Math.max(2, seam.w), Math.max(2, seam.h)));
            }
            g.chcolor();
            ensureCaptions();
            if (captionBoxes == null)
                return;
            for (int i = 0; i < captionTex.size() && i < captionBoxes.size(); i++) {
                MergedBagLayout.Box box = captionBoxes.get(i);
                g.image(captionTex.get(i), Coord.of(box.x, box.y));
            }
        }

        private void ensureCaptions() {
            if (captions == null) {
                captionTex.clear();
                captionKeys.clear();
                return;
            }
            if (captionTex.size() == captions.size()) {
                boolean same = true;
                for (int i = 0; i < captions.size(); i++) {
                    String key = i < captionKeys.size() ? captionKeys.get(i) : "";
                    if (!captions.get(i).equals(key)) {
                        same = false;
                        break;
                    }
                }
                if (same)
                    return;
            }
            captionTex.clear();
            captionKeys.clear();
            for (String caption : captions) {
                captionKeys.add(caption);
                captionTex.add(CAP.render(caption, java.awt.Color.WHITE).tex());
            }
        }
    }
}
