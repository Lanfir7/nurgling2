package nurgling.widgets.login;

import haven.*;
import nurgling.conf.NCharTags;
import nurgling.conf.NSavedAccounts.Account;
import nurgling.i18n.L10n;
import nurgling.widgets.NCharTagsWnd;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class NAccountList extends SListBox<Account, Widget> {
    public static final int ROWH = UI.scale(30);
    public static final int DEFROWS = 8;
    private static final int DELW = UI.scale(22), EDITW = UI.scale(16), DRAGTHRESH = UI.scale(4), EDGESCROLL = UI.scale(4);
    private static final double DBLCLICK = 0.4;
    public static final Account ANOTHER = new Account("", false, null, 0);
    private static final Text CROSS = NLoginTheme.name.render("×", new Color(201, 128, 128));
    private static final Text CONFIRM = NLoginTheme.badge.render(L10n.get("login.remove_confirm"), NLoginTheme.err);
    private static Text deltip = null;

    public interface Listener {
        void select(Account a);
        void activate(Account a);
        void remove(Account a);
        void reorder(List<Account> order);
    }

    private final List<Account> items = new ArrayList<>();
    private final Listener l;
    private Account confirm = null, lastclick = null, dragging = null;
    private double lastclickt = 0;
    private boolean dragged = false;
    private int dragy = 0;
    private UI.Grab dgrab = null;
    private int maxrows = DEFROWS;

    public NAccountList(int w, Listener l) {
        super(Coord.of(w, ROWH), ROWH);
        this.l = l;
        setcanfocus(true);
    }

    public void set(List<Account> accs) {
        items.clear(); items.addAll(accs); items.add(ANOTHER); confirm = null;
        if (!items.contains(sel)) sel = null;
        fit();
    }

    /** Most rows to show before scrolling; the list is never taller than its accounts. */
    public void maxrows(int n) {
        n = Math.max(1, n);
        if (n == maxrows)
            return;
        maxrows = n;
        fit();
    }

    private void fit() {
        resize(Coord.of(sz.x, Math.min(items.size(), maxrows) * ROWH));
    }

    public int saved() { return (items.size() - 1); }
    public Account find(String name) {
        for (Account a : items) if ((a != ANOTHER) && a.name.equals(name)) return (a);
        return (null);
    }
    public void show(Account a) { sel = (a == null) ? ANOTHER : a; confirm = null; display(sel); }
    public void step(int d) {
        if (items.isEmpty()) return;
        int i = items.indexOf(sel);
        i = (i < 0) ? ((d > 0) ? 0 : items.size() - 1) : Utils.clip(i + d, 0, items.size() - 1);
        pick(items.get(i));
    }
    private void pick(Account a) { change(a); confirm = null; display(a); l.select(a); }
    private void askremove(Account a) {
        if (confirm == a) {
            confirm = null;
            /* Flush and close first: a live editor must not recreate annotations after forget. */
            NCharTagsWnd.close();
            l.remove(a);
        } else confirm = a;
    }
    protected List<Account> items() { return (items); }
    protected Widget makeitem(Account a, int idx, Coord sz) { return (new Row(a, sz)); }
    protected void drawslot(GOut g, Account item, int idx, Area area) {}
    protected boolean unselect(int button) { return (false); }

    public boolean keydown(KeyDownEvent ev) {
        switch (ev.code) {
            case KeyEvent.VK_UP: step(-1); return (true);
            case KeyEvent.VK_DOWN: step(1); return (true);
            case KeyEvent.VK_ENTER: if (sel != null) l.activate(sel); return (true);
            case KeyEvent.VK_DELETE: if ((sel != null) && (sel != ANOTHER)) askremove(sel); return (true);
            case KeyEvent.VK_ESCAPE: pick(ANOTHER); return (true);
        }
        return (super.keydown(ev));
    }

    private void startdrag(Account a, int y) {
        dragging = a; dragged = false; dragy = y;
        if (dgrab == null) dgrab = ui.grabmouse(this);
    }
    private void dragto(int y) {
        if (!dragged) { if (Math.abs(y - dragy) < DRAGTHRESH) return; dragged = true; }
        if (y < (ROWH / 2)) scrollval(Math.max(0, scrollval() - EDGESCROLL));
        else if (y > (sz.y - (ROWH / 2))) scrollval(Math.min(Math.max(0, scrollmax()), scrollval() + EDGESCROLL));
        int n = saved(); if (n < 2) return;
        int idx = Utils.clip((y + scrollval()) / ROWH, 0, n - 1);
        int cur = items.indexOf(dragging);
        if ((cur >= 0) && (cur != idx)) { items.remove(cur); items.add(idx, dragging); }
    }
    private void enddrag() {
        if (dgrab != null) { dgrab.remove(); dgrab = null; }
        boolean moved = dragged; dragging = null; dragged = false;
        if (moved) { List<Account> order = new ArrayList<>(items); order.remove(ANOTHER); l.reorder(order); }
    }
    public void mousemove(MouseMoveEvent ev) { if (dragging != null) dragto(ev.c.y); super.mousemove(ev); }
    public void dispose() {
        NCharTagsWnd.close();
        super.dispose();
    }
    public boolean mouseup(MouseUpEvent ev) {
        if ((ev.b == 1) && (dragging != null)) { enddrag(); return (true); }
        return (super.mouseup(ev));
    }

    private class Row extends Widget {
        private final Account a;
        private final Text nm, meta;
        private boolean hover = false;
        private int delx, editx;
        Row(Account a, Coord sz) {
            super(sz); this.a = a; delx = sz.x - DELW; editx = delx - EDITW;
            if (a == ANOTHER) { nm = NLoginTheme.body.render(L10n.get("login.another"), NLoginTheme.muted); meta = null; }
            else { nm = NLoginTheme.name.render(a.name); String ago = NLoginTheme.ago(a.used); meta = ago.isEmpty() ? null : NLoginTheme.meta.render(ago); }
        }
        private boolean delvisible() { return ((a != ANOTHER) && (hover || (sel == a))); }
        private boolean indel(Coord c) { return (delvisible() && (c.x >= delx)); }
        private boolean inedit(Coord c) { return (delvisible() && (confirm != a) && (c.x >= editx) && (c.x < delx)); }
        public void draw(GOut g) {
            boolean s = (sel == a), drag = dragged && (dragging == a);
            if (s || drag) { g.chcolor(NLoginTheme.sel); g.frect(Coord.z, sz); g.chcolor(NLoginTheme.accent); g.frect(Coord.z, Coord.of(UI.scale(3), sz.y)); }
            else if (hover) { g.chcolor(NLoginTheme.hover); g.frect(Coord.z, sz); }
            if (drag) { g.chcolor(NLoginTheme.accent); NLoginTheme.outline(g, Coord.z, sz); }
            g.chcolor(NLoginTheme.rowline); g.frect(Coord.of(0, sz.y - 1), Coord.of(sz.x, 1)); g.chcolor();
            int x = UI.scale(12), cy = sz.y / 2;
            g.image(nm.tex(), Coord.of(x, cy - (nm.sz().y / 2)));
            boolean conf = (confirm == a); delx = sz.x - DELW; editx = delx - EDITW;
            if (delvisible()) {
                if (conf) { int w = CONFIRM.sz().x; delx = sz.x - UI.scale(8) - w; g.image(CONFIRM.tex(), Coord.of(delx, cy - (CONFIRM.sz().y / 2))); delx -= UI.scale(4); }
                else {
                    g.image(CROSS.tex(), Coord.of(sz.x - (DELW / 2) - (CROSS.sz().x / 2), cy - (CROSS.sz().y / 2)));
                    NLoginTheme.drawNote(g, Coord.of(editx + UI.scale(4), cy - UI.scale(5)), NLoginTheme.muted);
                }
            }
            if (conf)
                return;
            int right = editx - UI.scale(4);
            if (meta != null) {
                right -= meta.sz().x;
                g.image(meta.tex(), Coord.of(right, cy - (meta.sz().y / 2)));
                right -= UI.scale(6);
            }
            if (a != ANOTHER)
                drawtags(g, x + nm.sz().x + UI.scale(8), cy, right);
        }

        private void drawtags(GOut g, int cx, int cy, int maxx) {
            if (NCharTags.hasAccNote(a.name)) {
                if ((cx + UI.scale(8)) > maxx)
                    return;
                NLoginTheme.drawNote(g, Coord.of(cx, cy - UI.scale(5)), NLoginTheme.note);
                cx += UI.scale(14);
            }
            List<String> tags = NCharTags.accTags(a.name);
            int ch = NLoginTheme.chiph(), cty = cy - (ch / 2), shown = 0;
            for (String t : tags) {
                Text tt = NLoginTheme.chiptext(t);
                int w = tt.sz().x + UI.scale(8);
                int reserve = ((tags.size() - shown) > 1) ? UI.scale(24) : 0;
                if ((cx + w) > (maxx - reserve))
                    break;
                NLoginTheme.drawChip(g, Coord.of(cx, cty), tt, NCharTags.accColor(t));
                cx += w + UI.scale(3);
                shown++;
            }
            if (shown < tags.size())
                NLoginTheme.drawChip(g, Coord.of(cx, cty), NLoginTheme.chiptext("+" + (tags.size() - shown)), NLoginTheme.muted);
        }
        public void mousemove(MouseMoveEvent ev) { hover = ev.c.isect(Coord.z, sz); super.mousemove(ev); }
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 3) {
                if (a == ANOTHER)
                    return (super.mousedown(ev));
                NCharTagsWnd.openacc(ui, a.name);
                return (true);
            }
            if (ev.b != 1) return (super.mousedown(ev));
            NAccountList.this.parent.setfocus(NAccountList.this);
            if (indel(ev.c)) { askremove(a); return (true); }
            if (inedit(ev.c)) { NCharTagsWnd.openacc(ui, a.name); return (true); }
            double now = Utils.rtime(); boolean dbl = (lastclick == a) && ((now - lastclickt) < DBLCLICK);
            lastclick = a; lastclickt = now; pick(a);
            if (dbl) { l.activate(a); return (true); }
            if (a != ANOTHER) startdrag(a, ev.c.y + c.y);
            return (true);
        }
        public Object tooltip(Coord c, Widget prev) {
            if (indel(c)) {
                if (deltip == null) deltip = NLoginTheme.tip.render(L10n.get("login.remove_tip"));
                return (deltip);
            }
            if (a == ANOTHER)
                return (null);
            if (inedit(c))
                return (NLoginTheme.tiptext(L10n.get("login.tags_tip")));
            StringBuilder text = new StringBuilder();
            List<String> tags = NCharTags.accTags(a.name);
            String note = NCharTags.accNote(a.name);
            if (!tags.isEmpty()) text.append(String.join(", ", tags));
            if (!note.isEmpty()) {
                if (text.length() > 0) text.append("\n\n");
                text.append(note);
            }
            return ((text.length() == 0) ? null : NLoginTheme.tiptext(text.toString()));
        }
    }
}
