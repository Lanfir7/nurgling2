package nurgling.actions;

import haven.*;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.db.dao.StorageItemDao;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.tasks.NTask;

import java.util.function.BooleanSupplier;

import static haven.OCache.posres;

/** Navigate to a recorded container, then resolve its hash in the caller's session. */
public final class StorageNav {
    private StorageNav() { }

    public static Coord parseCoord(String coord) {
        if (coord == null) return null;
        String[] parts = coord.replace("(", "").replace(")", "").replace(" ", "").split(",");
        if (parts.length != 2) return null;
        try {
            double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 100000 || Math.abs(y) > 100000)
                return null;
            return new Coord((int) x, (int) y);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    interface Navigation<T> {
        T find(String hash);
        boolean local(long gridId, Coord local) throws InterruptedException;
        boolean chunk(long gridId, Coord local) throws InterruptedException;
        T await(String hash) throws InterruptedException;
    }

    static <T> T reach(String hash, long gridId, String coordinates,
                       BooleanSupplier cancelled, Navigation<T> navigation) throws InterruptedException {
        if (hash == null || hash.isBlank() || cancelled == null || navigation == null || cancelled.getAsBoolean())
            return null;
        T found = navigation.find(hash);
        if (found != null) return found;
        Coord local = parseCoord(coordinates);
        if (local == null || cancelled.getAsBoolean()) return null;
        if (navigation.local(gridId, local)) {
            if (cancelled.getAsBoolean()) return null;
            found = navigation.await(hash);
            if (cancelled.getAsBoolean()) return null;
            if (found != null) return found;
        }
        if (cancelled.getAsBoolean() || !navigation.chunk(gridId, local) || cancelled.getAsBoolean()) return null;
        found = navigation.await(hash);
        return cancelled.getAsBoolean() ? null : found;
    }

    public static Gob reach(NGameUI gui, StorageItemDao.LocatedItem row) throws InterruptedException {
        if (row == null) return null;
        return reach(gui, row.container, row.gridId, row.coord, () -> false);
    }

    public static Gob reach(NGameUI gui, String hash, long gridId, String coordinates,
                            BooleanSupplier cancelled) throws InterruptedException {
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null
                || gui.map.glob != gui.ui.sess.glob
                || Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean())
            return null;
        Gob visible = findInSession(gui, hash);
        if (visible != null) return visible;
        if (gui.map.glob == null || gui.map.glob.map == null || !(gui.map instanceof NMapView))
            return null;
        return reach(hash, gridId, coordinates,
                () -> Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean(),
                new Navigation<Gob>() {
                    @Override public Gob find(String target) { return findInSession(gui, target); }

                    @Override public boolean local(long grid, Coord local) throws InterruptedException {
                        MCache mcache = gui.map.glob.map;
                        MCache.Grid loaded = null;
                        synchronized (mcache.grids) {
                            for (MCache.Grid entry : mcache.grids.values())
                                if (entry.id == grid) { loaded = entry; break; }
                        }
                        if (loaded == null) return false;
                        Coord2d origin = new Coord2d(loaded.ul.x * MCache.tilesz.x,
                                loaded.ul.y * MCache.tilesz.y);
                        Coord2d target = origin.add(local.x * posres.x, local.y * posres.y);
                        try {
                            return new PathFinder(target).run(gui).IsSuccess();
                        } catch (Loading loading) {
                            return false;
                        }
                    }

                    @Override public boolean chunk(long grid, Coord local) throws InterruptedException {
                        ChunkNavManager nav = ((NMapView) gui.map).getChunkNavManager();
                        if (nav == null || !nav.isInitialized()) return false;
                        Coord tile = new Coord2d(local.x * posres.x, local.y * posres.y).floor(MCache.tilesz);
                        try {
                            ChunkPath path = nav.planToGridCoord(grid, tile);
                            return path != null && nav.navigateWithPath(path, null, gui).IsSuccess();
                        } catch (Loading loading) {
                            return false;
                        }
                    }

                    @Override public Gob await(String target) throws InterruptedException {
                        final Gob[] result = {null};
                        NTask wait = new NTask() {
                            { infinite = false; criticalOnTimeout = false; maxCounter = 300; }
                            @Override public boolean check() {
                                if (Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean())
                                    return true;
                                result[0] = findInSession(gui, target);
                                return result[0] != null;
                            }
                        };
                        gui.ui.core.addTask(wait);
                        return result[0];
                    }
                });
    }

    public static Gob findVisible(NGameUI gui, String hash) {
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null
                || gui.map.glob != gui.ui.sess.glob) return null;
        return findInSession(gui, hash);
    }

    private static Gob findInSession(NGameUI gui, String hash) {
        if (gui.ui.sess == null || gui.ui.sess.glob == null || hash == null) return null;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                try {
                    if (gob.ngob != null && gob.ngob.name != null && hash.equals(gob.ngob.storageHash()))
                        return gob;
                } catch (Loading ignored) {
                    // A still-loading gob is not a verified target.
                }
            }
        }
        return null;
    }
}
