package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NStyle;
import nurgling.NUtils;
import nurgling.actions.FollowRoute;
import nurgling.actions.RouteWalkControl;
import nurgling.i18n.L10n;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerRouteStore;
import nurgling.routes.ForagerWaypoint;
import nurgling.sessions.BotExecutor;
import nurgling.widgets.bots.PathRecordable;
import java.util.*;

/** Saved-route picker and walk controls. Waypoint actions are deliberately never executed. */
public class RouteWalkerWindow extends Window implements PathRecordable {
    private static final int W = UI.scale(216);
    private static final int GAP = UI.scale(5);
    private static final int ITEM_H = UI.scale(16);
    private static final int ROWS = 8;

    private final List<String> names = new ArrayList<>();
    private final Listbox<String> list;
    private final Label detail;
    private final Label note;
    private final Label status;
    private final Button follow, pause, stop;
    private ForagerPath path;
    private RouteWalkControl control;

    public RouteWalkerWindow() {
        super(new Coord(W, UI.scale(230)), L10n.get("routewalker.title"));
        refresh();
        list = add(new Listbox<String>(W - UI.scale(15), ROWS, ITEM_H) {
            protected int listitems() { return names.size(); }
            protected String listitem(int i) { return names.get(i); }
            protected void drawbg(GOut g) { g.chcolor(NStyle.rowEven); g.frect(Coord.z, sz); g.chcolor(); }
            protected void drawsel(GOut g) {
                g.chcolor(NStyle.border.getRed(), NStyle.border.getGreen(), NStyle.border.getBlue(), 56);
                g.frect(Coord.z, g.sz()); g.chcolor();
            }
            protected void drawitem(GOut g, String item, int i) {
                if (i % 2 == 1) { g.chcolor(NStyle.rowOdd); g.frect(Coord.z, g.sz()); g.chcolor(); }
                g.text(item, Coord.of(UI.scale(4), UI.scale(1)));
            }
            public void change(String item) {
                if (walking()) return;
                super.change(item);
                select(item);
            }
        });
        detail = add(new Label(L10n.get("routewalker.no_selection")), list.pos("bl").add(0, GAP));
        note = add(new Label(L10n.get("routewalker.walk_only")), detail.pos("bl").add(0, UI.scale(2)));
        follow = add(new Button(W, L10n.get("routewalker.follow")) {
            public void click() { super.click(); start(); }
        }, note.pos("bl").add(0, GAP));
        int half = (W - GAP) / 2;
        pause = add(new Button(half, L10n.get("routewalker.pause")) {
            public void click() {
                super.click();
                if (control != null && control.running()) control.setPaused(!control.isPaused());
                buttons();
            }
        }, follow.pos("bl").add(0, GAP));
        stop = add(new Button(half, L10n.get("routewalker.stop")) {
            public void click() { super.click(); stop(); }
        }, follow.pos("bl").add(half + GAP, GAP));
        status = add(new Label(L10n.get("routewalker.idle")), pause.pos("bl").add(0, GAP));

        Object saved = NConfig.get(NConfig.Key.routeWalkerLast);
        String last = saved instanceof String ? (String) saved : null;
        if (last != null && names.contains(last)) list.change(last);
        buttons();
        pack();
    }

    private void refresh() {
        names.clear();
        names.addAll(ForagerRouteStore.listRouteNames());
    }

    private void select(String name) {
        if (name == null) {
            path = null;
            detail.settext(L10n.get("routewalker.no_selection"));
            buttons();
            return;
        }
        ForagerRouteStore.LoadResult loaded = ForagerRouteStore.load(name);
        if (loaded.failed()) {
            path = null;
            detail.settext(L10n.get("routewalker.load_error"));
        } else {
            path = loaded.path();
            detail.settext(describe(path));
            NConfig.set(NConfig.Key.routeWalkerLast, name);
            NConfig.needUpdate();
        }
        buttons();
    }

    static String describe(ForagerPath route) {
        if (route == null || route.waypoints == null || route.waypoints.isEmpty())
            return L10n.get("routewalker.empty_route");
        int milestoneLegs = 0;
        for (int i = 1; i < route.waypoints.size(); i++) {
            ForagerWaypoint from = route.waypoints.get(i - 1);
            ForagerWaypoint to = route.waypoints.get(i);
            if (from != null && to != null && from.milestoneHash != null
                    && from.milestoneHash.equals(to.milestoneHash)) milestoneLegs++;
        }
        return milestoneLegs == 0
                ? L10n.get("routewalker.waypoints", route.waypoints.size())
                : L10n.get("routewalker.waypoints_milestones", route.waypoints.size(), milestoneLegs);
    }

    private boolean walking() { return control != null && control.running(); }

    private void start() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || walking()) return;
        String invalid = FollowRoute.validationErrorKey(path, gui);
        if (invalid != null) { gui.error(L10n.get(invalid)); return; }
        if (!FollowRoute.claimRoute(gui, path)) { gui.error(L10n.get("routewalker.busy")); return; }

        RouteWalkControl next = new RouteWalkControl();
        control = next;
        buttons();
        Thread runner = BotExecutor.runAsync(L10n.get("routewalker.title"),
                new FollowRoute(path, next, true));
        if (runner == null) {
            FollowRoute.releaseRoute(gui, path);
            next.finish("routewalker.failed");
            buttons();
            return;
        }
        next.attachThread(runner);
    }

    private void stop() {
        if (control == null || !control.running()) return;
        RouteWalkControl current = control;
        NGameUI gui = NUtils.getGameUI();
        FollowRoute.stopNow(gui);
        current.cancel();
        Thread runner = current.thread();
        if (runner != null && gui != null && gui.biw != null) gui.biw.removeObserve(runner);
        buttons();
    }

    private void buttons() {
        boolean running = walking();
        follow.disable(running || path == null);
        pause.disable(!running);
        stop.disable(!running);
        pause.change(L10n.get(running && control.isPaused()
                ? "routewalker.resume" : "routewalker.pause"));
    }

    public void tick(double dt) {
        super.tick(dt);
        buttons();
        status.settext(statusText());
    }

    private String statusText() {
        if (control == null) return L10n.get("routewalker.idle");
        if (control.isPaused() && !"routewalker.status_paused".equals(control.statusKey()))
            return L10n.get("routewalker.status_pause_pending");
        String text = L10n.get(control.statusKey());
        if (control.total() > 0 && control.waypoint() > 0 && control.running())
            return L10n.get("routewalker.status_at", text, control.waypoint(), control.total());
        return text;
    }

    public void show() {
        refresh();
        if (!walking() && path != null && !names.contains(path.name)) {
            path = null;
            list.sel = null;
            detail.settext(L10n.get("routewalker.no_selection"));
        }
        super.show();
        raise();
        buttons();
    }

    public boolean isRecording() { return false; }
    public void addWaypointToRecording(ForagerWaypoint wp) { }
    public ForagerPath getCurrentLoadedPath() { return path; }
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) hide(); else super.wdgmsg(sender, msg, args);
    }

    public static void toggle() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null) return;
        if (gui.routeWalkerWindow == null) {
            gui.routeWalkerWindow = new RouteWalkerWindow();
            gui.add(gui.routeWalkerWindow, UI.scale(new Coord(150, 150)));
            gui.routeWalkerWindow.show();
        } else if (gui.routeWalkerWindow.visible()) {
            gui.routeWalkerWindow.hide();
        } else {
            gui.routeWalkerWindow.show();
        }
    }
}
