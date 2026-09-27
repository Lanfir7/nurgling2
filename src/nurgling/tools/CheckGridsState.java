package nurgling.tools;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import haven.MapView;
import haven.OCache;
import haven.UI;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUI;
import nurgling.areas.NGlobalCoord;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;
import nurgling.sessions.ThreadLocalUI;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitForMapLoad;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CheckGridsState implements Runnable {

    private static final AtomicReference<ExecutorService> executorRef = new AtomicReference<>(createExecutor());

    private final NMapView map;
    private final NUI ui;
    private final NGameUI gui;

    private CheckGridsState(NMapView map, NUI ui, NGameUI gui) {
        this.map = map;
        this.ui = ui;
        this.gui = gui;
    }

    private static ExecutorService createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), r -> {
                    Thread t = new Thread(r, "CheckGridsState");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /** A plob message already identifies the owning map, including headless sessions. */
    public static void submit(MapView source) {
        if(!(source instanceof NMapView)) return;
        UI ownerUI = source.ui;
        if(!(ownerUI instanceof NUI)) return;
        NMapView map = (NMapView)source;
        NUI ui = (NUI)ownerUI;
        NGameUI gui = ui.gui;
        if(!owns(map, ui, gui)) return;
        enqueue(new CheckGridsState(map, ui, gui));
    }

    /** Resolve a moving player's session by its Glob, never by the active visual UI. */
    public static void submit(Gob gob) {
        if(gob == null || gob.glob == null || gob.glob.sess == null) return;
        SessionContext session = SessionManager.getInstance().findBySession(gob.glob.sess);
        if(session == null) return;
        NUI ui = session.ui;
        if(ui == null) return;
        NGameUI gui = session.getGameUI();
        if(gui == null || !(gui.map instanceof NMapView)) return;
        NMapView map = (NMapView)gui.map;
        if(map.glob != gob.glob || map.plgob != gob.id || !owns(map, ui, gui)) return;
        enqueue(new CheckGridsState(map, ui, gui));
    }

    private static void enqueue(CheckGridsState work) {
        ExecutorService ex = executorRef.get();
        if(ex != null && !ex.isShutdown()) ex.execute(work);
    }

    public static void resetExecutor() {
        ExecutorService old = executorRef.getAndSet(createExecutor());
        if(old != null) old.shutdownNow();
    }

    static boolean owns(NMapView map, NUI ui, NGameUI gui) {
        return map != null && ui != null && gui != null && ui.sess != null &&
                sameOwner(map, ui, gui, map.ui, gui.ui, ui.gui, gui.map,
                        map.glob, ui.sess.glob, map.parent != null, ui.core != null);
    }

    static boolean sameOwner(Object map, Object ui, Object gui, Object mapUi, Object guiUi,
                             Object uiGui, Object guiMap, Object mapGlob, Object sessionGlob,
                             boolean attached, boolean coreReady) {
        return map != null && ui != null && gui != null && mapGlob != null && attached && coreReady &&
                mapUi == ui && guiUi == ui && uiGui == gui && guiMap == map && mapGlob == sessionGlob;
    }

    private boolean owns() {
        return owns(map, ui, gui) && !Thread.currentThread().isInterrupted();
    }

    /** Same grid/local coordinate as NGlobalCoord(Coord2d), using this map's cache. */
    private NGlobalCoord coordinateOf(Coord2d position) {
        if(position == null || map.glob == null || map.glob.map == null)
            return new NGlobalCoord(0, null);
        MCache cache = map.glob.map;
        Coord tile = position.floor(MCache.tilesz);
        synchronized(cache.grids) {
            MCache.Grid grid = cache.grids.get(tile.div(MCache.cmaps));
            if(grid == null || grid.ul == null) return new NGlobalCoord(0, null);
            Coord local = position.sub(grid.ul.mul(Coord2d.of(11, 11))).floor(OCache.posres);
            return new NGlobalCoord(grid.id, local);
        }
    }

    @Override
    public void run() {
        NUI previous = ThreadLocalUI.get();
        ThreadLocalUI.set(ui);
        try {
            if(!owns()) return;
            ui.core.addTask(new NTask() {
                @Override public boolean check() {
                    return !owns() || map.plgob != -1;
                }
            });
            if(!owns()) return;
            Gob player = map.player();
            if(player == null || player.glob != map.glob) return;

            ui.core.addTask(new NTask() {
                @Override public boolean check() {
                    return !owns() || coordinateOf(player.rc).getGridId() != 0;
                }
            });
            if(!owns()) return;
            NGlobalCoord newCoord = coordinateOf(player.rc);
            if(newCoord.getGridId() == 0) return;
            NGlobalCoord previousCoord = map.lastGC;
            if(previousCoord == null || newCoord.getGridId() != previousCoord.getGridId()) {
                map.lastGC = newCoord;
                WaitForMapLoad wait = new WaitForMapLoad(gui, newCoord);
                ui.core.addTask(new NTask() {
                    @Override public boolean check() {
                        return !owns() || wait.check();
                    }
                });
                if(owns()) map.requestAreaLabelSync();
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if(previous == null) ThreadLocalUI.clear();
            else ThreadLocalUI.set(previous);
        }
    }
}
