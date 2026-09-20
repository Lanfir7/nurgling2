package nurgling.widgets.charsel;

import haven.*;
import haven.render.Location;
import haven.render.Projection;
import nurgling.NCharlist;
import nurgling.NConfig;
import nurgling.conf.FontSettings;
import nurgling.conf.NCharTags;
import nurgling.plugins.NPluginManager;
import nurgling.widgets.login.NBackdrop;
import nurgling.widgets.login.NLoginTheme;

import java.util.*;

public class NCharselScreen extends Widget {
    private static final Coord SRVSZ = new Coord(800, 600), ARTSZ = new Coord(1376, 768);
    private static final int SRV_RIGHT = 300, AVACROP = UI.scale(12), AVATOP = UI.scale(16), AVABOT = UI.scale(40), AVAWIDE = UI.scale(90);
    private static final double ROTSPEED = 0.015;
    private static final Object ROTID = new Object();
    private final NBackdrop backdrop;
    private final NamePlate plate;
    private NCharlist list;
    private Widget avatar;
    private IButton newchar;
    private final List<Img> badges = new ArrayList<>();
    private final Map<Widget, Coord> loose = new HashMap<>();
    private final List<ActionLink> actions = new ArrayList<>();
    private Widget panel;
    private boolean pluginsNotified = false;
    private Avaview avaview;
    private UI.Grab rotgrab = null;
    private double rot = 0, rotstart = 0;
    private int rotx = 0;

    public static boolean isCharsel(Coord srvsz) { return (SRVSZ.equals(srvsz)); }
    public NCharselScreen() {
        super(UI.scale(ARTSZ));
        backdrop = add(new NBackdrop(this::art, NBackdrop.SCRIMW), Coord.z);
        plate = add(new NamePlate(), Coord.z);
    }
    private Tex art() { return (Img.getCharselBg((list != null) ? list.artWorld() : (String) NConfig.get(NConfig.Key.selectedWorld))); }
    protected void added() { presize(); }
    public void presize() { c = parent.sz.div(2).sub(sz.div(2)); }

    public void addchild(Widget child, Object... args) {
        super.addchild(child, args);
        if (child instanceof Img) {
            Img img = (Img) child;
            switch (img.charselType) {
                case BACKGROUND: img.hide(); break;
                case VERIFY:
                case SUB: img.hide(); badges.add(img); break;
                default: loose.put(child, child.c);
            }
        } else if (child instanceof NCharlist) {
            list = (NCharlist) child;
        } else if (child instanceof ProxyFrame) {
            ProxyFrame<?> pf = (ProxyFrame<?>) child; pf.color = null;
            Widget view = pf.ch;
            if (view instanceof Avaview) {
                avaview = (Avaview) view;
                Coord osz = view.sz, nsz = osz.add(AVAWIDE, AVATOP + AVABOT);
                view.resize(nsz);
                float field = 0.5f * ((float) nsz.x / (float) osz.x);
                float half = (((float) nsz.y) / ((float) nsz.x)) * field;
                float wpp = (2 * field) / nsz.x;
                float shift = ((AVABOT - AVATOP) / 2f) * wpp;
                avaview.basic(Projection.class, Projection.frustum(-field, field, -half - shift, half - shift, 1, 5000));
            }
            view.move(Coord.of(0, -AVACROP)); pf.resize(Coord.of(view.sz.x, view.sz.y - AVACROP)); avatar = pf;
        } else if (child instanceof Avaview) {
            avatar = child; avaview = (Avaview) child;
        } else if ((child instanceof IButton) && (newchar == null)) {
            newchar = (IButton) child; newchar.hide();
        } else loose.put(child, child.c);
        wire(); layout();
        if ((list != null) && !pluginsNotified) {
            pluginsNotified = true;
            NPluginManager.onCharsel(this);
        }
    }

    public void cdestroy(Widget ch) {
        super.cdestroy(ch);
        if (ch == list) list = null;
        if (ch == avatar) { avatar = null; avaview = null; }
        if (ch == newchar) newchar = null;
        if (ch == panel) {
            panel = null;
            setListShown(true);
            layout();
        }
        actions.remove(ch);
        badges.remove(ch); loose.remove(ch); wire();
    }
    private void wire() { if (list != null) { list.badgeSources(badges); list.newCharSource(newchar); } }

    /** Adds a plugin action under the character list footer. */
    public void addAction(String label, Runnable action) {
        if (label == null)
            throw new IllegalArgumentException("Action label must not be null");
        if (action == null)
            throw new IllegalArgumentException("Action must not be null");
        ActionLink link = add(new ActionLink(label, action), Coord.z);
        loose.remove(link);
        actions.add(link);
        if (panel != null)
            link.hide();
        layout();
    }

    /** The available area for a plugin panel, matching the character-list column. */
    public Coord panelSize() { return (Coord.of(NCharlist.W, NCharlist.H)); }

    /** Shows a plugin panel in place of the character list. */
    public <T extends Widget> T showPanel(T widget) {
        if (widget == null)
            throw new IllegalArgumentException("Panel must not be null");
        closePanel();
        T added = add(widget, Coord.z);
        loose.remove(added);
        panel = added;
        setListShown(false);
        layout();
        return (added);
    }

    /** Removes the plugin panel and restores the character list and actions. */
    public void closePanel() {
        if (panel != null) {
            Widget previous = panel;
            panel = null;
            previous.reqdestroy();
        }
        setListShown(true);
        layout();
    }

    /** The visible plugin panel, or null when the character list is shown. */
    public Widget panel() { return (panel); }

    /** The server-provided New Character button, or null until it arrives. */
    public IButton newCharButton() { return (newchar); }

    private void setListShown(boolean shown) {
        if (list != null) {
            if (shown) list.show(); else list.hide();
        }
        for (ActionLink link : actions) {
            if (shown) link.show(); else link.hide();
        }
    }
    private void layout() {
        int lx = UI.scale(56), ly = (sz.y - NCharlist.H) / 2;
        if (list != null) list.move(Coord.of(lx, (sz.y - list.sz.y) / 2));
        if (panel != null) panel.move(Coord.of(lx, ly));
        int ax = lx;
        for (ActionLink link : actions) {
            link.move(Coord.of(ax, ly + NCharlist.H + UI.scale(8)));
            ax += link.sz.x + UI.scale(16);
        }
        int cx = (backdrop.scrimw() + sz.x) / 2, gap = UI.scale(10);
        int h = ((avatar != null) ? avatar.sz.y + gap : 0) + plate.sz.y, y = (sz.y - h) / 2;
        if (avatar != null) { avatar.move(Coord.of(cx - (avatar.sz.x / 2), y)); y += avatar.sz.y + gap; }
        plate.move(Coord.of(cx - (plate.sz.x / 2), y));
        int srvw = UI.scale(SRVSZ.x);
        for (Map.Entry<Widget, Coord> e : loose.entrySet()) { Coord o = e.getValue(); e.getKey().move((o.x > UI.scale(SRV_RIGHT)) ? o.add(sz.x - srvw, 0) : o); }
    }
    private void setrot(double a) { rot = a; if (avaview != null) avaview.basic(ROTID, Location.rot(new Coord3f(0, 0, 1), (float) rot)); }
    public boolean mousedown(MouseDownEvent ev) {
        if (ev.propagate(this)) return (true);
        if ((ev.b == 1) && (avaview != null) && (avatar != null) && ev.c.isect(avatar.c, avatar.sz)) {
            rotgrab = ui.grabmouse(this); rotx = ev.c.x; rotstart = rot; return (true);
        }
        return (super.mousedown(ev));
    }
    public void mousemove(MouseMoveEvent ev) { if (rotgrab != null) setrot(rotstart + ((ev.c.x - rotx) * ROTSPEED)); super.mousemove(ev); }
    public boolean mouseup(MouseUpEvent ev) {
        if ((ev.b == 1) && (rotgrab != null)) { rotgrab.remove(); rotgrab = null; return (true); }
        return (super.mouseup(ev));
    }
    public void tick(double dt) { super.tick(dt); if (avatar instanceof ProxyFrame) ((ProxyFrame<?>) avatar).color = null; }

    /** Accent-coloured link contributed by a plugin. */
    private static class ActionLink extends Widget {
        private static final Text.Foundry fnd = new Text.Foundry(FontSettings.getOpenSansSemibold(), 12, NLoginTheme.accent).aa(true);
        private final Text text;
        private final Runnable action;
        private boolean hover = false;

        ActionLink(String label, Runnable action) {
            super(Coord.z);
            this.text = fnd.render(label);
            this.action = action;
            resize(text.sz().add(0, UI.scale(2)));
        }

        public void draw(GOut g) {
            g.image(text.tex(), Coord.z);
            if (hover) {
                g.chcolor(NLoginTheme.accent);
                g.frect(Coord.of(0, text.sz().y), Coord.of(text.sz().x, UI.scale(1)));
                g.chcolor();
            }
        }

        public void mousemove(MouseMoveEvent ev) { hover = ev.c.isect(Coord.z, sz); }

        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1) {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    System.err.println("[Plugins] Character-selection action error: " + e);
                } catch (Error error) {
                    rethrowFatal(error);
                    System.err.println("[Plugins] Character-selection action error: " + error);
                }
                return (true);
            }
            return (super.mousedown(ev));
        }

        private static void rethrowFatal(Error error) {
            if (error instanceof VirtualMachineError)
                throw (VirtualMachineError) error;
            if (error instanceof ThreadDeath)
                throw (ThreadDeath) error;
        }
    }

    private class NamePlate extends Widget {
        private Charlist.Char shown = null;
        private Text nm = null, ln = null, nt = null;
        private String lns = null, nts = null;
        NamePlate() { super(UI.scale(new Coord(460, 110))); }
        public void draw(GOut g) {
            Charlist.Char ch = (list != null) ? list.selected() : null;
            if (ch == null) return;
            String acc = NCharTags.account(ui);
            if (ch != shown) { shown = ch; nm = NLoginTheme.plate.render(ch.name); }
            String l = list.metaline(acc, ch, true);
            if (!l.equals(lns)) { lns = l; ln = NLoginTheme.sub.render(l); }
            int y = 0; g.image(nm.tex(), Coord.of((sz.x - nm.sz().x) / 2, y)); y += nm.sz().y - UI.scale(4);
            g.image(ln.tex(), Coord.of((sz.x - ln.sz().x) / 2, y)); y += ln.sz().y + UI.scale(4);
            List<String> tags = NCharTags.tags(acc, ch.name);
            if (!tags.isEmpty()) {
                int gap = UI.scale(4), tw = -gap;
                for (String t : tags) tw += NCharlist.chiptext(t).sz().x + UI.scale(8) + gap;
                int x = (sz.x - tw) / 2;
                for (String t : tags) x += NLoginTheme.drawChip(g, Coord.of(x, y), NCharlist.chiptext(t), NCharTags.color(t)) + gap;
                y += NLoginTheme.chiph() + UI.scale(6);
            }
            String note = NCharTags.note(acc, ch.name);
            if (!note.isEmpty()) {
                String first = note.split("\n", 2)[0]; if (first.length() > 70) first = first.substring(0, 69) + "…";
                if (!first.equals(nts)) { nts = first; nt = NLoginTheme.hint.render(first); }
                g.image(nt.tex(), Coord.of((sz.x - nt.sz().x) / 2, y));
            }
        }
    }
}
