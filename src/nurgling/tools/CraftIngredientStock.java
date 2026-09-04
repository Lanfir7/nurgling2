package nurgling.tools;

import haven.*;
import nurgling.NCore;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.db.StorageOrphanPolicy;
import nurgling.db.StorageTableInfo;
import nurgling.db.dao.ContainerDao;
import nurgling.db.dao.StorageItemDao;
import nurgling.db.service.ContainerService;
import nurgling.db.service.StorageItemService;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Storage lookup for craft-window ingredient search.
 * Resolves a slot (concrete item or VSpec group) to grouped warehouse rows.
 */
public final class CraftIngredientStock {

    private CraftIngredientStock() {}

    public static String qualityKey(double quality) {
        return String.format(Locale.ROOT, "%.2f", quality);
    }

    public static final class Totals {
        public final int count;
        public final double maxQuality;

        public Totals(int count, double maxQuality) {
            this.count = count;
            this.maxQuality = maxQuality;
        }
    }

    /**
     * Names to query for one craft slot.
     * Category without a picked member expands to every VSpec member.
     */
    public static List<String> namesFor(String specName, boolean category, String selectedIngName) {
        if (selectedIngName != null && !selectedIngName.isEmpty()) {
            return List.of(selectedIngName);
        }
        if (specName == null || specName.isEmpty()) {
            return List.of();
        }
        if (category || VSpec.categories.containsKey(specName)) {
            try {
                ArrayList<String> members = VSpec.getCategoryContent(specName);
                if (members != null && !members.isEmpty()) {
                    return new ArrayList<>(members);
                }
            } catch (Exception ignored) {
            }
        }
        return List.of(specName);
    }

    public static Totals totals(List<GroupedItem> items) {
        if (items == null || items.isEmpty()) {
            return new Totals(0, 0);
        }
        int count = 0;
        double maxQ = 0;
        for (GroupedItem item : items) {
            count += item.count;
            maxQ = Math.max(maxQ, item.maxQuality);
        }
        return new Totals(count, maxQ);
    }

    /**
     * Load warehouse rows for the given names and group by name + quality.
     */
    public static List<GroupedItem> search(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        if (NCore.databaseManager == null || !NCore.databaseManager.isReady()) {
            return List.of();
        }
        try {
            StorageItemService storageService = new StorageItemService(NCore.databaseManager);
            ContainerService containerService = new ContainerService(NCore.databaseManager);
            List<StorageItemDao.StorageItemData> raw = storageService.loadStorageItemsByNames(names);
            Map<String, ContainerDao.ContainerData> containers = new HashMap<>();
            for (ContainerDao.ContainerData container : containerService.loadAllContainers()) {
                containers.put(container.getHash(), container);
            }
            return groupByQuality(raw, containers);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public static List<GroupedItem> groupByQuality(List<StorageItemDao.StorageItemData> raw,
                                                   Map<String, ContainerDao.ContainerData> containers) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, String> liveNames = liveStorageNames();
        Map<String, List<StorageItemDao.StorageItemData>> grouped = raw.stream()
                .filter(item -> item.getQuality() >= 0)
                .collect(Collectors.groupingBy(item ->
                        item.getName() + "|" + qualityKey(item.getQuality()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<GroupedItem> result = new ArrayList<>();
        Map<String, ContainerDao.ContainerData> byHash = containers != null ? containers : Map.of();
        for (List<StorageItemDao.StorageItemData> itemGroup : grouped.values()) {
            if (itemGroup.isEmpty()) {
                continue;
            }
            StorageItemDao.StorageItemData first = itemGroup.get(0);
            Location location = resolveGroupLocation(itemGroup, byHash, liveNames);
            result.add(new GroupedItem(
                    first.getName(),
                    first.getQuality(),
                    itemGroup.size(),
                    itemGroup,
                    location.distanceTiles,
                    location.storageName
            ));
        }
        result.sort(Comparator.comparingDouble((GroupedItem a) -> a.quality).reversed()
                .thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static final class Location {
        final int distanceTiles;
        final String storageName;

        Location(int distanceTiles, String storageName) {
            this.distanceTiles = distanceTiles;
            this.storageName = storageName;
        }
    }

    private static Location resolveGroupLocation(List<StorageItemDao.StorageItemData> itemGroup,
                                                 Map<String, ContainerDao.ContainerData> containers,
                                                 Map<String, String> liveNames) {
        int bestDist = StorageTableInfo.UNKNOWN_DIST;
        String nearestName = "—";
        List<String> names = new ArrayList<>();
        for (StorageItemDao.StorageItemData item : itemGroup) {
            String hash = item.getContainer();
            String name = liveNames.getOrDefault(hash, "—");
            names.add(name);
            int dist = distanceTiles(hash, containers);
            if (dist >= 0 && (bestDist < 0 || dist < bestDist)) {
                bestDist = dist;
                nearestName = name;
            }
        }
        return new Location(bestDist, StorageTableInfo.storageLabel(nearestName, names));
    }

    private static Map<String, String> liveStorageNames() {
        Map<String, String> names = new HashMap<>();
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) {
            return names;
        }
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.ngob == null || gob.ngob.hash == null || gob.ngob.name == null) {
                    continue;
                }
                names.put(gob.ngob.hash, StorageTableInfo.containerTitle(gob.ngob.name));
            }
        }
        return names;
    }

    private static int distanceTiles(String containerHash, Map<String, ContainerDao.ContainerData> containers) {
        if (containerHash == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        ContainerDao.ContainerData data = containers.get(containerHash);
        Gob player = NUtils.player();
        NGameUI gui = NUtils.getGameUI();
        if (data == null || player == null || player.rc == null || gui == null
                || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null
                || gui.ui.sess.glob.map == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        Coord stored = StorageOrphanPolicy.parseGcoord(data.getCoord());
        if (stored == null) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
        MCache map = gui.ui.sess.glob.map;
        try {
            Coord pltc = new Coord2d(player.rc.x / MCache.tilesz.x, player.rc.y / MCache.tilesz.y).floor();
            MCache.Grid playerGrid;
            synchronized (map.grids) {
                if (!map.grids.containsKey(pltc.div(MCache.cmaps))) {
                    return StorageTableInfo.UNKNOWN_DIST;
                }
                playerGrid = map.getgridt(pltc);
            }
            if (playerGrid == null) {
                return StorageTableInfo.UNKNOWN_DIST;
            }
            if (playerGrid.id == data.getGridId()) {
                Coord playerGcoord = player.rc.sub(playerGrid.ul.mul(Coord2d.of(11, 11))).floor(OCache.posres);
                return StorageTableInfo.tilesBetween(playerGcoord, stored);
            }
            MCache.Grid containerGrid = findGridById(map, data.getGridId());
            if (containerGrid == null) {
                return StorageTableInfo.UNKNOWN_DIST;
            }
            Coord2d containerRc = Coord2d.of(containerGrid.ul).mul(MCache.tilesz)
                    .add(Coord2d.of(stored).mul(OCache.posres));
            return (int) Math.round(player.rc.dist(containerRc) / MCache.tilesz.x);
        } catch (Loading e) {
            return StorageTableInfo.UNKNOWN_DIST;
        }
    }

    private static MCache.Grid findGridById(MCache map, long gridId) {
        synchronized (map.grids) {
            for (MCache.Grid grid : map.grids.values()) {
                if (grid != null && grid.id == gridId) {
                    return grid;
                }
            }
        }
        return null;
    }
}
