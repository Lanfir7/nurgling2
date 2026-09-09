package nurgling.tools;

import haven.Coord;
import haven.Indir;
import haven.Loading;
import haven.MCache;
import haven.Resource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Captures the connected cplot mask under the player using stable map-grid ids. */
public final class CurrentClaimArea {
    public static final class GridMask {
        public final Coord sessionGrid;
        public final long stableGridId;
        private final boolean[] mask;

        public GridMask(Coord sessionGrid, long stableGridId, boolean[] mask) {
            this.sessionGrid = sessionGrid;
            this.stableGridId = stableGridId;
            this.mask = mask.clone();
        }

        private boolean contains(Coord local) {
            return local.x >= 0 && local.y >= 0
                    && local.x < MCache.cmaps.x && local.y < MCache.cmaps.y
                    && mask[local.x + (local.y * MCache.cmaps.x)];
        }
    }

    private CurrentClaimArea() {
    }

    public static ClaimArea capture(MCache map, Coord playerTile) {
        if (map == null || playerTile == null)
            return null;
        List<GridMask> masks = new ArrayList<>();
        Coord playerGrid = playerTile.div(MCache.cmaps);
        synchronized (map.grids) {
            for (MCache.Grid grid : map.grids.values()) {
                if (grid == null || grid.ols == null || grid.ol == null)
                    continue;
                if (grid.id == 0) {
                    if (grid.gc.equals(playerGrid))
                        throw new Loading("Waiting for stable map grid id");
                    continue;
                }
                boolean[] combined = new boolean[MCache.cmaps.x * MCache.cmaps.y];
                boolean hasClaim = false;
                int count = Math.min(grid.ols.length, grid.ol.length);
                for (int i = 0; i < count; i++) {
                    Indir<Resource> resource = grid.ols[i];
                    boolean[] overlayMask = grid.ol[i];
                    if (resource == null || overlayMask == null)
                        continue;
                    MCache.ResOverlay overlay = resource.get().flayer(MCache.ResOverlay.class);
                    if (overlay == null || overlay.tags() == null || !overlay.tags().contains("cplot"))
                        continue;
                    int limit = Math.min(combined.length, overlayMask.length);
                    for (int tile = 0; tile < limit; tile++) {
                        if (overlayMask[tile]) {
                            combined[tile] = true;
                            hasClaim = true;
                        }
                    }
                }
                if (hasClaim)
                    masks.add(new GridMask(grid.gc, grid.id, combined));
            }
        }
        return capture(playerTile, masks);
    }

    public static ClaimArea capture(Coord playerTile, Collection<GridMask> grids) {
        if (playerTile == null || grids == null)
            return null;
        Map<Coord, GridMask> byGrid = new HashMap<>();
        for (GridMask grid : grids) {
            if (grid != null)
                byGrid.put(grid.sessionGrid, grid);
        }
        GridMask startGrid = gridAt(playerTile, byGrid);
        if (startGrid == null || !startGrid.contains(localTile(playerTile, startGrid.sessionGrid)))
            return null;

        ClaimArea.Tile anchor = stableTile(playerTile, startGrid);
        List<ClaimArea.Tile> stableTiles = new ArrayList<>();
        ArrayDeque<Coord> pending = new ArrayDeque<>();
        Set<Coord> visited = new HashSet<>();
        pending.add(playerTile);
        while (!pending.isEmpty()) {
            Coord tile = pending.removeFirst();
            if (!visited.add(tile))
                continue;
            GridMask grid = gridAt(tile, byGrid);
            if (grid == null || !grid.contains(localTile(tile, grid.sessionGrid)))
                continue;
            stableTiles.add(stableTile(tile, grid));
            pending.add(tile.add(1, 0));
            pending.add(tile.add(-1, 0));
            pending.add(tile.add(0, 1));
            pending.add(tile.add(0, -1));
        }
        return new ClaimArea(anchor, stableTiles);
    }

    private static GridMask gridAt(Coord tile, Map<Coord, GridMask> grids) {
        return grids.get(tile.div(MCache.cmaps));
    }

    private static Coord localTile(Coord tile, Coord grid) {
        return tile.sub(grid.mul(MCache.cmaps));
    }

    private static ClaimArea.Tile stableTile(Coord tile, GridMask grid) {
        Coord local = localTile(tile, grid.sessionGrid);
        return new ClaimArea.Tile(grid.stableGridId, local.x, local.y);
    }
}
