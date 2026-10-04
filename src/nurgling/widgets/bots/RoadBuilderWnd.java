package nurgling.widgets.bots;

import haven.Button;
import haven.Coord;
import haven.Dropbox;
import haven.GOut;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.actions.bots.road.RoadRouteStore;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerRouteStore;
import nurgling.widgets.nsettings.ForagerRouteMapWindow;

import java.util.ArrayList;
import java.util.List;

/** Picks a saved road route and whether the stones are wood or stone. */
public class RoadBuilderWnd extends Window implements Checkable {
    public ForagerPath route;
    public boolean stone = true;
    public boolean cancelled = false;
    private boolean ready = false;

    private final Dropbox<String> routes;
    private final TextEntry nameEntry;
    private ForagerRouteMapWindow editor;

    public RoadBuilderWnd() {
        super(new Coord(UI.scale(280), UI.scale(180)), L10n.get("road.wnd_title"));

        Widget prev = add(new Label(L10n.get("road.route")), Coord.z);
        prev = add(routes = new Dropbox<String>(UI.scale(200), 8, UI.scale(16)) {
            @Override
            protected String listitem(int i) {
                List<String> names = RoadRouteStore.listRouteNames();
                return i >= 0 && i < names.size() ? names.get(i) : null;
            }

            @Override
            protected int listitems() {
                return RoadRouteStore.listRouteNames().size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                if (item != null)
                    g.text(item, Coord.z);
            }

            @Override
            public void change(String item) {
                if (item == null) return;
                super.change(item);
            }
        }, prev.pos("bl").add(UI.scale(0, 4)));

        List<String> existing = RoadRouteStore.listRouteNames();
        if (!existing.isEmpty())
            routes.change(existing.get(0));

        prev = add(nameEntry = new TextEntry(UI.scale(140), ""), prev.pos("bl").add(UI.scale(0, 8)));
        add(new Button(UI.scale(80), L10n.get("road.new_route")) {
            @Override
            public void click() {
                createRoute();
            }
        }, prev.pos("ur").add(UI.scale(6, 0)));

        final String stoneLabel = L10n.get("road.stone");
        final String woodLabel = L10n.get("road.wood");
        final List<String> kinds = new ArrayList<String>();
        kinds.add(stoneLabel);
        kinds.add(woodLabel);
        Dropbox<String> kind = add(new Dropbox<String>(UI.scale(200), 2, UI.scale(16)) {
            @Override
            protected String listitem(int i) { return kinds.get(i); }

            @Override
            protected int listitems() { return kinds.size(); }

            @Override
            protected void drawitem(GOut g, String item, int i) { g.text(item, Coord.z); }

            @Override
            public void change(String item) {
                if (item == null) return;
                super.change(item);
                stone = stoneLabel.equals(item);
            }
        }, routes.pos("bl").add(UI.scale(0, 36)));
        kind.change(stoneLabel);
        prev = kind;

        add(new Button(UI.scale(120), L10n.get("road.edit")) {
            @Override
            public void click() {
                openEditor();
            }
        }, prev.pos("bl").add(UI.scale(0, 8)));
        add(new Button(UI.scale(120), L10n.get("road.start")) {
            @Override
            public void click() {
                start();
            }
        }, prev.pos("bl").add(UI.scale(130, 8)));
        pack();
    }

    private void createRoute() {
        String name = nameEntry.text() == null ? "" : nameEntry.text().trim();
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('.') >= 0) {
            NUtils.getGameUI().error(L10n.get("road.err.name"));
            return;
        }
        if (!RoadRouteStore.listRouteNames().contains(name)) {
            try {
                RoadRouteStore.save(new ForagerPath(name));
            } catch (Exception e) {
                NUtils.getGameUI().error(L10n.get("road.err.name"));
                return;
            }
        }
        routes.change(name);
        openEditor();
    }

    private void openEditor() {
        if (routes.sel == null) {
            NUtils.getGameUI().error(L10n.get("road.err.no_route"));
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.mmap == null || gui.mmap.file == null) {
            if (gui != null)
                gui.error(L10n.get("road.err.segment"));
            return;
        }
        ForagerRouteStore.LoadResult loaded = RoadRouteStore.load(routes.sel);
        if (loaded.failed() || loaded.path() == null) {
            gui.error(L10n.get("road.err.no_route"));
            return;
        }
        final ForagerPath path = loaded.path();
        if (editor != null)
            editor.reqdestroy();
        ForagerRouteMapWindow wnd = new ForagerRouteMapWindow(gui.mmap.file, L10n.get("road.edit_title", path.name));
        wnd.map.setRoute(path);
        gui.activeRouteEditor = wnd.map;
        wnd.onClosed = new Runnable() {
            @Override
            public void run() {
                try {
                    RoadRouteStore.save(path);
                } catch (Exception ignored) {
                }
                if (gui.activeRouteEditor == wnd.map)
                    gui.activeRouteEditor = null;
                if (editor == wnd)
                    editor = null;
            }
        };
        editor = wnd;
        gui.add(wnd, UI.scale(80, 80));
    }

    private void start() {
        NGameUI gui = NUtils.getGameUI();
        if (editor != null && editor.map.getRoute() != null) {
            ForagerPath open = editor.map.getRoute();
            try {
                RoadRouteStore.save(open);
            } catch (Exception e) {
                gui.error(L10n.get("road.err.name"));
                return;
            }
            editor.reqdestroy();
            editor = null;
            if (open.waypoints == null || open.waypoints.size() < 2) {
                gui.error(L10n.get("road.err.short"));
                return;
            }
            route = open;
            ready = true;
            return;
        }
        if (routes.sel == null) {
            gui.error(L10n.get("road.err.no_route"));
            return;
        }
        ForagerRouteStore.LoadResult loaded = RoadRouteStore.load(routes.sel);
        if (loaded.failed() || loaded.path() == null || loaded.path().waypoints == null
                || loaded.path().waypoints.size() < 2) {
            gui.error(L10n.get("road.err.short"));
            return;
        }
        route = loaded.path();
        ready = true;
    }

    @Override
    public boolean check() {
        return ready;
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close")) {
            cancelled = true;
            ready = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }
}
