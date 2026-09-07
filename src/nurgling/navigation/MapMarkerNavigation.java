package nurgling.navigation;

import haven.Coord2d;
import haven.MiniMap;
import haven.OCache;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.hotkeys.Hotkeys;
import nurgling.i18n.L10n;
import nurgling.map.SharedMarkerLocator;
import nurgling.sessions.BotExecutor;

import static haven.MCache.tilesz;

public final class MapMarkerNavigation {
    public enum Outcome {
        CHUNK_NAV,
        PATHFINDER,
        DIRECT,
        UNAVAILABLE
    }

    @FunctionalInterface
    public interface Attempt {
        boolean run() throws InterruptedException;
    }

    private MapMarkerNavigation() {
    }

    public static boolean isTrigger(int button, boolean press, int mods) {
        return !press && Hotkeys.matchesMapMarkerNavigate(button, mods);
    }

    public static Outcome tryInOrder(Attempt chunkNav, Attempt pathfinder, Attempt direct)
            throws InterruptedException {
        if (chunkNav.run())
            return Outcome.CHUNK_NAV;
        if (pathfinder.run())
            return Outcome.PATHFINDER;
        if (direct.run())
            return Outcome.DIRECT;
        return Outcome.UNAVAILABLE;
    }

    public static Coord2d worldTarget(MiniMap.Location marker, MiniMap.Location session) {
        if (marker == null || session == null || marker.seg.id != session.seg.id)
            return null;
        return marker.tc.sub(session.tc).mul(tilesz).add(tilesz.div(2));
    }

    public static Outcome navigate(NGameUI gui, SharedMarkerLocator.Ref ref, Coord2d target)
            throws InterruptedException {
        return tryInOrder(
            () -> tryChunkNav(gui, ref),
            () -> tryPathfinder(gui, target),
            () -> tryDirect(gui, target));
    }

    public static void start(haven.MapFile file, haven.MapFile.Marker marker,
                             MiniMap.Location location, MiniMap.Location session) {
        SharedMarkerLocator.Ref ref = SharedMarkerLocator.export(file, marker);
        Coord2d target = worldTarget(location, session);
        BotExecutor.runAsync("MapMarkerNavigation", gui -> {
            Outcome outcome = navigate(gui, ref, target);
            if (outcome == Outcome.UNAVAILABLE) {
                gui.error(L10n.get("marker.navigation.unavailable"));
                return Results.FAIL();
            }
            return Results.SUCCESS();
        });
    }

    private static boolean tryChunkNav(NGameUI gui, SharedMarkerLocator.Ref ref)
            throws InterruptedException {
        if (gui == null || ref == null || !(gui.map instanceof NMapView))
            return false;
        ChunkNavManager manager = ((NMapView)gui.map).getChunkNavManager();
        if (manager == null || !manager.isInitialized())
            return false;
        try {
            ChunkPath path = manager.planToGridCoord(ref.gridId, ref.local);
            return path != null && manager.navigateWithPath(path, null, gui).IsSuccess();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryPathfinder(NGameUI gui, Coord2d target)
            throws InterruptedException {
        if (gui == null || target == null)
            return false;
        try {
            return new PathFinder(target).run(gui).IsSuccess();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryDirect(NGameUI gui, Coord2d target) {
        if (gui == null || gui.map == null || target == null)
            return false;
        try {
            gui.map.wdgmsg("click", haven.Coord.z, target.floor(OCache.posres), 1, 0);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
