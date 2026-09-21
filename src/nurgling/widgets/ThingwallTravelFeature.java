package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.Listbox;
import haven.MapFile;
import haven.MapMarkerVisibility;
import haven.MessageBuf;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Label;
import nurgling.i18n.L10n;
import nurgling.plugins.UiEvents;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Observes server Thingwall dialogs and adds safe, route-based travel controls. */
public final class ThingwallTravelFeature {
    private static final String THINGWALL = "ui/thingwall";
    private static final String PROVINCES = "ui/provinces";
    private static final String VISITED_PREF = "map/visited-thingwalls";
    private static final String SERVER_KNOWLEDGE_PREF = "thingwall/server-knowledge";
    private static final String FAVORITES_PREF = "thingwall/favorites";
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 260;
    private static final double ADVERTISEMENT_TIMEOUT = 3.0;
    private static final AtomicBoolean installed = new AtomicBoolean();
    private static final Map<UI, State> states = Collections.synchronizedMap(new WeakHashMap<UI, State>());
    private static final Object knowledgeLock = new Object();
    private static final Object favoritesLock = new Object();

    private ThingwallTravelFeature() {
    }

    public static void install() {
        if(installed.compareAndSet(false, true))
            UiEvents.addSystemListener(new Observer());
    }

    private static State state(UI ui) {
        synchronized(states) {
            State result = states.get(ui);
            if(result == null) {
                result = new State(ui);
                states.put(ui, result);
            }
            return(result);
        }
    }

    private static final class Observer implements UiEvents.Listener {
        public void onNewWidget(UI ui, int id, String type, Widget widget, Object[] cargs) {
            State state = state(ui);
            if(matchesDynamicType(type, PROVINCES)) {
                state.provinces = widget;
                state.provinceSequence = Integer.MIN_VALUE;
                state.refreshCurrent();
            } else if(matchesDynamicType(type, THINGWALL)) {
                String name = ((cargs != null) && (cargs.length > 0) && (cargs[0] instanceof String))
                    ? (String)cargs[0] : null;
                state.open(new Dialog(widget, name));
            }
        }

        public void onAddWidget(UI ui, int id, Widget widget, int parent, Widget parentWidget, Object[] pargs) {
            State state = state(ui);
            Dialog dialog = state.dialogs.get(widget);
            if(dialog != null)
                state.attach(dialog);
        }

        public void onUiMsg(UI ui, int id, Widget widget, String message, Object[] args) {
            State state = state(ui);
            Dialog dialog = state.dialogs.get(widget);
            if((dialog != null) && "dest".equals(message)) {
                dialog.addDestination(args);
                state.rememberKnowledge(dialog);
                state.refresh(dialog);
                state.tryDispatch();
            } else if(widget == state.provinces) {
                state.refreshCurrent();
            }
        }

        public void onDestroyWidget(UI ui, int id, Widget widget) {
            State state = state(ui);
            if(widget == state.provinces) {
                state.provinces = null;
                state.provinceSequence = Integer.MIN_VALUE;
            }
            Dialog dialog = state.dialogs.remove(widget);
            if(dialog != null)
                state.closed(dialog);
        }
    }

    private static final class State {
        private final WeakReference<UI> ui;
        private final Map<Widget, Dialog> dialogs = new IdentityHashMap<>();
        private Widget provinces;
        private int provinceSequence = Integer.MIN_VALUE;
        private int markerSequence = Integer.MIN_VALUE;
        private String visitedPreference;
        private String favoritePreference;
        private Dialog current;
        private RouteRun running;

        private State(UI ui) {
            this.ui = new WeakReference<>(ui);
        }

        private void open(Dialog dialog) {
            dialogs.put(dialog.widget, dialog);
            current = dialog;
            rememberKnowledge(dialog);
            if(running != null) {
                if(!safeEquals(running.expectedName, dialog.name)) {
                    fail();
                    return;
                }
                running.index++;
                running.sent = false;
                running.waitingSince = Utils.rtime();
                if(running.index >= running.route.path.size() - 1) {
                    running = null;
                }
            }
        }

        private void attach(Dialog dialog) {
            if(dialog.panel != null || dialog.widget.parent == null)
                return;
            Coord original = dialog.widget.sz;
            dialog.panel = new RoutePanel(this, dialog);
            dialog.widget.add(dialog.panel, original.x + UI.scale(8), UI.scale(18));
            dialog.widget.resize(original.add(dialog.panel.sz.x + UI.scale(16), 0));
            refresh(dialog);
        }

        private void closed(Dialog dialog) {
            if(current == dialog) {
                current = null;
                if((running != null) && !running.sent)
                    fail();
            }
        }

        private void refreshCurrent() {
            if(current != null)
                refresh(current);
        }

        private void refresh(Dialog dialog) {
            rememberKnowledge(dialog);
            if(dialog.panel == null)
                return;
            provinceSequence = provinceSequence();
            observeMapState();
            dialog.panel.routes(routesFor(dialog));
        }

        private void tick(Dialog dialog) {
            if(dialog != current)
                return;
            int sequence = provinceSequence();
            if(sequence != provinceSequence) {
                provinceSequence = sequence;
                refresh(dialog);
            }
            if(observeMapState())
                refresh(dialog);
            if((running != null) && !running.sent && (Utils.rtime() - running.waitingSince > ADVERTISEMENT_TIMEOUT))
                fail();
        }

        private List<ThingwallRoutePlanner.Route> routesFor(Dialog dialog) {
            Snapshot snapshot = snapshot(dialog.name);
            List<ThingwallRoutePlanner.Route> graph = (snapshot == null)
                ? Collections.<ThingwallRoutePlanner.Route>emptyList()
                : ThingwallRoutePlanner.routes(snapshot.currentId, snapshot.provinces, snapshot.markers, snapshot.segment);
            List<ThingwallRoutePlanner.Route> direct = new ArrayList<>();
            for(AdvertisedDestination destination : dialog.advertisedDestinations.values()) {
                direct.add(ThingwallRoutePlanner.directRoute("advertised:" + destination.id, destination.name,
                    dialog.name, destination.distanceTiles));
            }
            return(ThingwallRoutePlanner.mergeRoutes(graph, direct));
        }

        private void rememberKnowledge(Dialog dialog) {
            String scope = knowledgeScope();
            if(scope == null)
                return;
            List<String> names = new ArrayList<>(dialog.destinations.keySet());
            if(dialog.name != null)
                names.add(dialog.name);
            synchronized(knowledgeLock) {
                Utils.setpref(SERVER_KNOWLEDGE_PREF, ThingwallKnowledgeStore.record(
                    Utils.getpref(SERVER_KNOWLEDGE_PREF, ""), scope, names));
            }
        }

        private String knowledgeScope() {
            UI active = ui.get();
            if((active == null) || (active.gui == null) || (active.gui.mmap == null)
                    || (active.gui.chrid == null) || (active.gui.mmap.sessloc == null) || (active.gui.mmap.file == null))
                return(null);
            return(ThingwallKnowledgeStore.scope(active.gui.chrid, active.gui.mmap.file.filename,
                active.gui.mmap.sessloc.seg.id));
        }

        private void begin(ThingwallRoutePlanner.Route route) {
            if((current == null) || (route.path.size() < 2))
                return;
            running = new RouteRun(route);
            tryDispatch();
        }

        private void tryDispatch() {
            if((running == null) || (current == null) || running.sent)
                return;
            if(!alignWithCurrentGraph()) {
                fail();
                return;
            }
            if(running.index >= running.route.path.size() - 1) {
                running = null;
                return;
            }
            String nextName = running.route.hopNames.get(running.index + 1);
            Integer destinationId = current.destinations.get(nextName);
            if(destinationId == null)
                return;
            running.expectedName = nextName;
            running.sent = true;
            current.widget.wdgmsg("trav", destinationId);
        }

        /** Replan from the actually opened Thingwall before every hop. */
        private boolean alignWithCurrentGraph() {
            for(ThingwallRoutePlanner.Route candidate : routesFor(current)) {
                if(candidate.destinationId.equals(running.destinationId)) {
                    running.route = candidate;
                    running.index = 0;
                    return(true);
                }
            }
            return(false);
        }

        private void fail() {
            running = null;
            UI active = ui.get();
            if(active != null)
                active.error(L10n.get("thingwall.route.error"));
        }

        private int provinceSequence() {
            if(provinces == null)
                return(Integer.MIN_VALUE);
            try {
                Object value = provinces.getClass().getField("seq").get(provinces);
                return(value instanceof Number ? ((Number)value).intValue() : Integer.MIN_VALUE);
            } catch(ReflectiveOperationException | RuntimeException ignored) {
                return(Integer.MIN_VALUE);
            }
        }

        /** Returns true when map markers, learned state, or the global favorites changed. */
        private boolean observeMapState() {
            UI active = ui.get();
            int sequence = Integer.MIN_VALUE;
            if((active != null) && (active.gui != null) && (active.gui.mmap != null)
                    && (active.gui.mmap.file != null))
                sequence = active.gui.mmap.file.markerseq;
            String preference = Utils.getpref(VISITED_PREF, "");
            String favorites = Utils.getpref(FAVORITES_PREF, "");
            boolean changed = (sequence != markerSequence) || !safeEquals(preference, visitedPreference)
                || !safeEquals(favorites, favoritePreference);
            markerSequence = sequence;
            visitedPreference = preference;
            favoritePreference = favorites;
            return(changed);
        }

        private Set<String> favoriteNames() {
            return(ThingwallFavorites.names(Utils.getpref(FAVORITES_PREF, "")));
        }

        private void toggleFavorite(String name) {
            synchronized(favoritesLock) {
                Utils.setpref(FAVORITES_PREF, ThingwallFavorites.toggle(
                    Utils.getpref(FAVORITES_PREF, ""), name));
            }
            refreshCurrent();
        }

        private Snapshot snapshot(String currentName) {
            UI active = ui.get();
            if((active == null) || (currentName == null) || (provinces == null) || (active.gui == null)
                    || (active.gui.chrid == null) || (active.gui.mmap == null) || (active.gui.mmap.sessloc == null)
                    || (active.gui.mmap.file == null))
                return(null);
            MapFile file = active.gui.mmap.file;
            long segment = active.gui.mmap.sessloc.seg.id;
            List<ThingwallRoutePlanner.Province> graph = reflectedProvinces(provinces);
            if(graph.isEmpty())
                return(null);
            Set<String> visited = MapMarkerVisibility.decode(Utils.getpref(VISITED_PREF, ""));
            Set<String> serverConfirmed = ThingwallKnowledgeStore.names(
                Utils.getpref(SERVER_KNOWLEDGE_PREF, ""), ThingwallKnowledgeStore.scope(active.gui.chrid,
                    file.filename, segment));
            List<MapFile.SMarker> matching = new ArrayList<>();
            List<ThingwallRoutePlanner.Marker> markers = new ArrayList<>();
            List<String> markerNames = new ArrayList<>();
            file.lock.readLock().lock();
            try {
                for(MapFile.Marker marker : file.markers) {
                    if(!(marker instanceof MapFile.SMarker) || (marker.seg != segment)
                            || !MapMarkerVisibility.isThingwall(marker))
                        continue;
                    MapFile.SMarker thingwall = (MapFile.SMarker)marker;
                    String id = provinceId(thingwall);
                    if(id == null)
                        continue;
                    markerNames.add(thingwall.nm);
                }
                for(MapFile.Marker marker : file.markers) {
                    if(!(marker instanceof MapFile.SMarker) || (marker.seg != segment)
                            || !MapMarkerVisibility.isThingwall(marker))
                        continue;
                    MapFile.SMarker thingwall = (MapFile.SMarker)marker;
                    String id = provinceId(thingwall);
                    if(id == null)
                        continue;
                    if(currentName.equals(thingwall.nm))
                        matching.add(thingwall);
                    if(MapMarkerVisibility.hasVisitedThingwallHalo(visited, marker)
                            || (serverConfirmed.contains(thingwall.nm)
                                && ThingwallKnowledgeStore.isUniqueName(thingwall.nm, markerNames)))
                        markers.add(new ThingwallRoutePlanner.Marker(id, thingwall.nm, thingwall.seg,
                            thingwall.tc.x, thingwall.tc.y, true));
                }
            } finally {
                file.lock.readLock().unlock();
            }
            if(matching.isEmpty())
                return(null);
            MapFile.SMarker currentMarker = closest(matching, active.gui.mmap.sessloc.tc);
            String currentId = provinceId(currentMarker);
            if(currentId != null) {
                for(java.util.Iterator<ThingwallRoutePlanner.Marker> iterator = markers.iterator(); iterator.hasNext();) {
                    if(currentId.equals(iterator.next().provinceId))
                        iterator.remove();
                }
                markers.add(new ThingwallRoutePlanner.Marker(currentId, currentMarker.nm, currentMarker.seg,
                    currentMarker.tc.x, currentMarker.tc.y, true));
            }
            return((currentId == null) ? null : new Snapshot(currentId, segment, graph, markers));
        }
    }

    private static final class Dialog {
        private final Widget widget;
        private final String name;
        private final Map<String, Integer> destinations = new java.util.LinkedHashMap<>();
        private final Map<String, AdvertisedDestination> advertisedDestinations = new java.util.LinkedHashMap<>();
        private int nextDestinationId;
        private RoutePanel panel;

        private Dialog(Widget widget, String name) {
            this.widget = widget;
            this.name = name;
        }

        private void addDestination(Object[] args) {
            if((args != null) && (args.length > 0) && (args[0] instanceof String)) {
                String destinationName = (String)args[0];
                destinations.putIfAbsent(destinationName, nextDestinationId);
                double distanceTiles = ((args.length > 2) && (args[2] instanceof Number))
                    ? ((Number)args[2]).doubleValue() / 100.0 : 0.0;
                advertisedDestinations.putIfAbsent(destinationName,
                    new AdvertisedDestination(destinationName, nextDestinationId, distanceTiles));
            }
            nextDestinationId++;
        }
    }

    private static final class AdvertisedDestination {
        private final String name;
        private final int id;
        private final double distanceTiles;

        private AdvertisedDestination(String name, int id, double distanceTiles) {
            this.name = name;
            this.id = id;
            this.distanceTiles = distanceTiles;
        }
    }

    private static final class RouteRun {
        private ThingwallRoutePlanner.Route route;
        private final String destinationId;
        private int index;
        private String expectedName;
        private boolean sent;
        private double waitingSince = Utils.rtime();

        private RouteRun(ThingwallRoutePlanner.Route route) {
            this.route = route;
            this.destinationId = route.destinationId;
        }
    }

    private static final class Snapshot {
        private final String currentId;
        private final long segment;
        private final List<ThingwallRoutePlanner.Province> provinces;
        private final List<ThingwallRoutePlanner.Marker> markers;

        private Snapshot(String currentId, long segment, List<ThingwallRoutePlanner.Province> provinces,
                         List<ThingwallRoutePlanner.Marker> markers) {
            this.currentId = currentId;
            this.segment = segment;
            this.provinces = provinces;
            this.markers = markers;
        }
    }

    private static final class RoutePanel extends Widget {
        private final State state;
        private final Dialog dialog;
        private final RouteList list;

        private RoutePanel(State state, Dialog dialog) {
            super(Coord.of(UI.scale(PANEL_WIDTH), UI.scale(PANEL_HEIGHT)));
            this.state = state;
            this.dialog = dialog;
            add(new Label(L10n.get("thingwall.route.title")), UI.scale(4), UI.scale(2));
            int itemHeight = UI.scale(22);
            int rows = Math.max(1, (sz.y - UI.scale(28)) / itemHeight);
            list = add(new RouteList(sz.x - UI.scale(8), rows, itemHeight, state::begin,
                state::toggleFavorite),
                UI.scale(4), UI.scale(22));
        }

        private void routes(List<ThingwallRoutePlanner.Route> routes) {
            list.routes(routes, state.favoriteNames());
        }

        public void tick(double dt) {
            super.tick(dt);
            state.tick(dialog);
        }
    }

    static final class RouteList extends Listbox<ThingwallRoutePlanner.Route> {
        private static final int STAR_WIDTH = UI.scale(24);
        private final RouteListState state;

        RouteList(int width, int rows, int itemHeight, Consumer<ThingwallRoutePlanner.Route> action,
                  Consumer<String> favoriteAction) {
            super(width, rows, itemHeight);
            this.state = new RouteListState(action, favoriteAction, STAR_WIDTH);
        }

        void routes(List<ThingwallRoutePlanner.Route> routes, Set<String> favorites) {
            state.routes(routes, favorites);
            sel = null;
            sb.val = 0;
            sb.max = Math.max(0, state.routes().size() - h);
        }

        protected int listitems() {
            return(state.routes().size());
        }

        protected ThingwallRoutePlanner.Route listitem(int index) {
            return(state.routes().get(index));
        }

        protected void drawbg(GOut g) {
            g.chcolor(0, 0, 0, 72);
            g.frect(Coord.z, sz);
            g.chcolor();
        }

        protected void drawsel(GOut g) {
            g.chcolor(255, 190, 64, 72);
            g.frect(Coord.z, g.sz());
            g.chcolor();
        }

        protected void drawitem(GOut g, ThingwallRoutePlanner.Route route, int index) {
            if((index & 1) != 0) {
                g.chcolor(255, 255, 255, 12);
                g.frect(Coord.z, g.sz());
                g.chcolor();
            }
            if(route == state.hovered()) {
                g.chcolor(255, 190, 64, 40);
                g.frect(Coord.z, g.sz());
                g.chcolor();
            }
            g.chcolor(state.favorite(route.destinationName) ? 255 : 190,
                state.favorite(route.destinationName) ? 190 : 190, 64, 255);
            g.text(state.favorite(route.destinationName) ? "★" : "☆", Coord.of(UI.scale(5), UI.scale(3)));
            g.chcolor();
            String label = L10n.get("thingwall.route.destination", route.destinationName,
                Math.round(route.distanceTiles));
            g.text(label, Coord.of(STAR_WIDTH, UI.scale(3)));
        }

        public void mousemove(MouseMoveEvent event) {
            super.mousemove(event);
            state.hover(itemat(event.c));
        }

        public boolean mousehover(MouseHoverEvent event, boolean hovering) {
            state.hover(hovering ? itemat(event.c) : null);
            return(false);
        }

        protected void itemclick(ThingwallRoutePlanner.Route route, Coord coordinate, int button) {
            if(button == 1) {
                super.change(route);
                state.click(route, coordinate.x);
            } else {
                super.itemclick(route, coordinate, button);
            }
        }
    }

    static final class RouteListState {
        private final List<ThingwallRoutePlanner.Route> routes = new ArrayList<>();
        private final List<ThingwallRoutePlanner.Route> view = Collections.unmodifiableList(routes);
        private final Consumer<ThingwallRoutePlanner.Route> action;
        private final Consumer<String> favoriteAction;
        private final int starWidth;
        private Set<String> favorites = Collections.emptySet();
        private ThingwallRoutePlanner.Route hovered;

        RouteListState(Consumer<ThingwallRoutePlanner.Route> action, Consumer<String> favoriteAction,
                       int starWidth) {
            this.action = action;
            this.favoriteAction = favoriteAction;
            this.starWidth = starWidth;
        }

        void routes(List<ThingwallRoutePlanner.Route> routes, Set<String> favorites) {
            this.routes.clear();
            this.routes.addAll(routes);
            this.favorites = new java.util.HashSet<>(favorites);
            this.routes.sort(Comparator
                .comparing((ThingwallRoutePlanner.Route route) -> !favorite(route.destinationName))
                .thenComparing(route -> route.destinationName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(route -> route.destinationName));
            hovered = null;
        }

        List<ThingwallRoutePlanner.Route> routes() {
            return(view);
        }

        void hover(ThingwallRoutePlanner.Route route) {
            hovered = route;
        }

        ThingwallRoutePlanner.Route hovered() {
            return(hovered);
        }

        boolean favorite(String name) {
            return(favorites.contains(name));
        }

        void click(ThingwallRoutePlanner.Route route, int x) {
            if(x < starWidth)
                favoriteAction.accept(route.destinationName);
            else
                action.accept(route);
        }
    }

    static List<ThingwallRoutePlanner.Province> reflectedProvinces(Object widget) {
        if(widget == null)
            return(Collections.emptyList());
        try {
            Object value = widget.getClass().getField("byid").get(widget);
            if(!(value instanceof Map))
                return(Collections.emptyList());
            List<ThingwallRoutePlanner.Province> result = new ArrayList<>();
            for(Object province : ((Map<?, ?>)value).values()) {
                if(province == null)
                    continue;
                String id = fieldString(province, "id");
                String name = fieldString(province, "name");
                Object neighborsValue = province.getClass().getField("neighbors").get(province);
                List<String> neighbors = new ArrayList<>();
                if(neighborsValue instanceof Collection) {
                    for(Object neighbor : (Collection<?>)neighborsValue)
                        if(neighbor != null)
                            neighbors.add(neighbor.toString());
                }
                if((id != null) && (name != null))
                    result.add(new ThingwallRoutePlanner.Province(id, name, neighbors));
            }
            return(result);
        } catch(ReflectiveOperationException | RuntimeException ignored) {
            return(Collections.emptyList());
        }
    }

    private static String fieldString(Object value, String field) throws ReflectiveOperationException {
        Object result = value.getClass().getField(field).get(value);
        return((result == null) ? null : result.toString());
    }

    private static String provinceId(MapFile.SMarker marker) {
        try {
            return((marker.data == null) ? null : new MessageBuf(marker.data).uniqid().toString());
        } catch(RuntimeException ignored) {
            return(null);
        }
    }

    private static MapFile.SMarker closest(List<MapFile.SMarker> markers, Coord location) {
        Collections.sort(markers, new Comparator<MapFile.SMarker>() {
            public int compare(MapFile.SMarker a, MapFile.SMarker b) {
                long ad = distanceSquared(a.tc, location);
                long bd = distanceSquared(b.tc, location);
                if(ad != bd)
                    return(ad < bd ? -1 : 1);
                int compared = a.tc.compareTo(b.tc);
                return((compared != 0) ? compared : a.nm.compareTo(b.nm));
            }
        });
        return(markers.get(0));
    }

    private static long distanceSquared(Coord a, Coord b) {
        long x = (long)a.x - b.x;
        long y = (long)a.y - b.y;
        return(x * x + y * y);
    }

    private static boolean safeEquals(String a, String b) {
        return((a == null) ? (b == null) : a.equals(b));
    }

    static boolean matchesDynamicType(String type, String base) {
        if(base.equals(type))
            return(true);
        if((type == null) || !type.startsWith(base) || (type.length() <= base.length())
                || (type.charAt(base.length()) != ':'))
            return(false);
        for(int index = base.length() + 1; index < type.length(); index++) {
            if(!Character.isDigit(type.charAt(index)))
                return(false);
        }
        return(type.length() > base.length() + 1);
    }
}
