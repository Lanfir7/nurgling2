package monitoring;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Loading;
import haven.MCache;
import haven.OCache;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.db.StockpileStoragePolicy;
import nurgling.db.StorageOrphanPolicy;
import nurgling.db.dao.ContainerDao;
import nurgling.db.service.ContainerService;
import nurgling.tools.ClaimLand;
import nurgling.tools.NSearchItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drops DB rows for containers inside ~30 tiles of the player
 * after that grid has finished streaming gobs.
 */
public class StorageOrphanCleaner implements OCache.ChangeCallback {
    private final ConcurrentHashMap<Long, Long> lastGobActivityMs = new ConcurrentHashMap<>();
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private OCache registeredOc;
    private long lastPlayerGridId = Long.MIN_VALUE;
    private long gridEnteredAtMs = 0;
    private long lastCheckAtMs = 0;

    public void tick(DatabaseManager databaseManager) {
        if (databaseManager == null || !databaseManager.isReady()) {
            return;
        }
        if (!Boolean.TRUE.equals(NConfig.get(NConfig.Key.ndbenable))) {
            return;
        }

        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return;
        }

        ensureCallback(gui.ui.sess.glob.oc);

        Gob player = NUtils.player();
        if (player == null || player.rc == null) {
            return;
        }
        if (!ClaimLand.isOnClaimOrVillage(player)) {
            return;
        }

        MCache.Grid grid = playerGrid(gui, player);
        if (grid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (grid.id != lastPlayerGridId) {
            lastPlayerGridId = grid.id;
            gridEnteredAtMs = now;
        }

        if (now - lastCheckAtMs < StorageOrphanPolicy.CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckAtMs = now;

        long lastActivity = lastGobActivityMs.getOrDefault(grid.id, 0L);
        if (!StorageOrphanPolicy.isGridIdle(now, gridEnteredAtMs, lastActivity)) {
            return;
        }

        Coord playerGcoord = playerGcoord(player, grid);
        if (playerGcoord == null) {
            return;
        }
        if (!scanning.compareAndSet(false, true)) {
            return;
        }

        final long gridId = grid.id;
        databaseManager.submitTask(() -> {
            try {
                scan(databaseManager, gridId);
            } finally {
                scanning.set(false);
            }
        });
    }

    @Override
    public void added(Gob ob) {
        markActivity(ob);
    }

    @Override
    public void removed(Gob ob) {
        markActivity(ob);
    }

    private void ensureCallback(OCache oc) {
        if (oc == null || oc == registeredOc) {
            return;
        }
        if (registeredOc != null) {
            registeredOc.uncallback(this);
        }
        oc.callback(this);
        registeredOc = oc;
        lastGobActivityMs.clear();
        lastPlayerGridId = Long.MIN_VALUE;
        gridEnteredAtMs = 0;
    }

    private void markActivity(Gob gob) {
        if (gob == null || gob.rc == null || skipGob(gob)) {
            return;
        }
        try {
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null
                    || gui.ui.sess.glob.map == null) {
                return;
            }
            MCache map = gui.ui.sess.glob.map;
            Coord tc = gob.rc.floor(MCache.tilesz);
            MCache.Grid grid;
            synchronized (map.grids) {
                if (!map.grids.containsKey(tc.div(MCache.cmaps))) {
                    return;
                }
                grid = map.getgridt(tc);
            }
            if (grid != null) {
                lastGobActivityMs.put(grid.id, System.currentTimeMillis());
            }
        } catch (Loading ignored) {
        } catch (Exception ignored) {
        }
    }

    private void scan(DatabaseManager databaseManager, long gridId) {
        NGameUI gui = NUtils.getGameUI();
        Gob player = NUtils.player();
        if (gui == null || player == null || player.rc == null) {
            return;
        }
        MCache.Grid grid = playerGrid(gui, player);
        if (grid == null || grid.id != gridId) {
            return;
        }
        Coord currentGcoord = playerGcoord(player, grid);
        if (currentGcoord == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastActivity = lastGobActivityMs.getOrDefault(gridId, 0L);
        if (!StorageOrphanPolicy.isGridIdle(now, gridEnteredAtMs, lastActivity)) {
            return;
        }

        LiveScan live = scanLiveGobs(gui, player);
        if (live.nearbyUnhashed) {
            return;
        }

        List<ContainerDao.ContainerData> containers;
        try {
            containers = databaseManager.getContainerService().loadContainersByGrid(gridId);
        } catch (Exception e) {
            return;
        }

        List<String> toPurge = new ArrayList<>();
        for (ContainerDao.ContainerData container : containers) {
            Coord stored = StorageOrphanPolicy.parseGcoord(container.getCoord());
            if (!StorageOrphanPolicy.isNearby(currentGcoord, stored)) {
                continue;
            }
            boolean present = StorageOrphanPolicy.gobPresent(
                    live.hashes.contains(container.getHash()),
                    occupiesStockpileTile(live, stored));
            if (StorageOrphanPolicy.shouldPurge(true, true, true, present, false)) {
                toPurge.add(container.getHash());
            }
        }

        if (toPurge.isEmpty()) {
            return;
        }

        ContainerService containersSvc = databaseManager.getContainerService();
        for (String hash : toPurge) {
            try {
                containersSvc.deleteContainer(hash);
                ItemWatcher.invalidateContainerCache(hash);
                synchronized (NGlobalSearchItems.containerHashes) {
                    NGlobalSearchItems.containerHashes.remove(hash);
                    NGlobalSearchItems.updateVersion++;
                }
                NGlobalSearchItems.clearQueryCache();
                NSearchItem.notifyContainerDataChanged();
                System.out.println("StorageOrphanCleaner: purged missing container " + hash);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static MCache.Grid playerGrid(NGameUI gui, Gob player) {
        if (gui.ui.sess.glob.map == null || player.rc == null) {
            return null;
        }
        MCache map = gui.ui.sess.glob.map;
        Coord pltc = new Coord2d(player.rc.x / MCache.tilesz.x, player.rc.y / MCache.tilesz.y).floor();
        try {
            synchronized (map.grids) {
                if (!map.grids.containsKey(pltc.div(MCache.cmaps))) {
                    return null;
                }
                return map.getgridt(pltc);
            }
        } catch (Loading e) {
            return null;
        }
    }

    private static Coord playerGcoord(Gob player, MCache.Grid grid) {
        if (player.rc == null || grid == null) {
            return null;
        }
        return player.rc.sub(grid.ul.mul(Coord2d.of(11, 11))).floor(OCache.posres);
    }

    private static LiveScan scanLiveGobs(NGameUI gui, Gob player) {
        LiveScan live = new LiveScan();
        double near = StorageOrphanPolicy.NEAR_TILES * MCache.tilesz.x;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (skipGob(gob) || gob.ngob == null) {
                    continue;
                }
                if (gob.ngob.hash != null) {
                    live.hashes.add(gob.ngob.hash);
                }
                if (gob.ngob.gcoord != null && StockpileStoragePolicy.isStockpileRes(gob.ngob.name)) {
                    live.stockpileGcoords.add(gob.ngob.gcoord);
                }
                if (gob.rc != null && gob.ngob.hash == null && player.rc.dist(gob.rc) <= near) {
                    live.nearbyUnhashed = true;
                }
            }
        }
        return live;
    }

    private static boolean occupiesStockpileTile(LiveScan live, Coord stored) {
        for (Coord liveCoord : live.stockpileGcoords) {
            if (StorageOrphanPolicy.sameTile(stored, liveCoord)) {
                return true;
            }
        }
        return false;
    }

    private static boolean skipGob(Gob gob) {
        if (gob instanceof OCache.Virtual) {
            return true;
        }
        if (gob.attr.isEmpty()) {
            return true;
        }
        return gob.getClass().getName().contains("GlobEffector");
    }

    private static final class LiveScan {
        final Set<String> hashes = new HashSet<>();
        final List<Coord> stockpileGcoords = new ArrayList<>();
        boolean nearbyUnhashed;
    }
}
