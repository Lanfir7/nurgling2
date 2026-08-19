package nurgling.map;

import haven.Area;
import haven.Coord;
import haven.MapFile;
import nurgling.navigation.ChunkNavData;
import nurgling.navigation.ChunkNavGraph;
import nurgling.navigation.ChunkPortal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static haven.MCache.cmaps;

/**
 * Aligns MapFile segments of different floors using recorded vertical ChunkNav portals.
 * Does not mutate MapFile or the navigation graph.
 */
public final class FloorOverlayAligner {
    private static final int OFFSET_DISAGREE_TILES = 2;

    private FloorOverlayAligner() {}

    public static boolean isVertical(ChunkPortal.PortalType type) {
        return type == ChunkPortal.PortalType.CAVEIN
                || type == ChunkPortal.PortalType.CAVEOUT
                || type == ChunkPortal.PortalType.MINEHOLE
                || type == ChunkPortal.PortalType.LADDER;
    }

    public static boolean isDown(ChunkPortal.PortalType type) {
        return type == ChunkPortal.PortalType.CAVEIN
                || type == ChunkPortal.PortalType.MINEHOLE;
    }

    public static final class GridRef {
        public final long segId;
        public final Coord sc;

        public GridRef(long segId, Coord sc) {
            this.segId = segId;
            this.sc = sc;
        }
    }

    public interface GridLookup {
        GridRef find(long gridId);
        int segmentGridCount(long segId);
    }

    public static final class FloorLink {
        public final long fromSegId;
        public final long toSegId;
        /** Add to a destination-segment tile coord to get the current-segment tile coord. */
        public final Coord tileOffset;
        public final boolean destIsBelow;
        public final int destChunkCount;

        public FloorLink(long fromSegId, long toSegId, Coord tileOffset, boolean destIsBelow, int destChunkCount) {
            this.fromSegId = fromSegId;
            this.toSegId = toSegId;
            this.tileOffset = tileOffset;
            this.destIsBelow = destIsBelow;
            this.destChunkCount = destChunkCount;
        }

        public Coord destTileToSrc(Coord destTc) {
            return destTc.add(tileOffset);
        }

        public String label() {
            String dir = destIsBelow ? "Below" : "Above";
            return dir + " (" + destChunkCount + " chunks)";
        }
    }

    /**
     * Tile offset that maps destination-segment tiles onto the source segment.
     * When {@code exitLocal} is missing, falls back to chunk-origin alignment.
     */
    public static Coord computeOffset(GridRef src, Coord local, GridRef dst, Coord exitLocal) {
        if (src == null || dst == null || src.sc == null || dst.sc == null) {
            return Coord.z;
        }
        if (exitLocal == null) {
            return src.sc.sub(dst.sc).mul(cmaps);
        }
        Coord localCoord = local != null ? local : Coord.z;
        Coord srcTc = src.sc.mul(cmaps).add(localCoord);
        Coord dstTc = dst.sc.mul(cmaps).add(exitLocal);
        return srcTc.sub(dstTc);
    }

    /**
     * Destination-segment grid coords (level-0, aligned to {@code dataLevel}) that overlap
     * the currently visible tile area of the source segment.
     */
    public static Area visibleDestGridArea(Area currentTiles, Coord tileOffset, int dataLevel) {
        if (currentTiles == null || tileOffset == null || !currentTiles.positive()) {
            return Area.corn(Coord.z, Coord.z);
        }
        int step = 1 << Math.max(0, dataLevel);
        int mask = ~(step - 1);
        Coord destUl = currentTiles.ul.sub(tileOffset).div(cmaps);
        Coord destBrIncl = currentTiles.br.sub(1, 1).sub(tileOffset).div(cmaps);
        Coord ul = new Coord(destUl.x & mask, destUl.y & mask);
        Coord br = new Coord((destBrIncl.x & mask) + step, (destBrIncl.y & mask) + step);
        return Area.corn(ul, br);
    }

    public static List<FloorLink> linksFrom(long currentSegId, Collection<ChunkNavData> chunks, GridLookup lookup) {
        if (chunks == null || lookup == null) {
            return Collections.emptyList();
        }
        Map<Long, List<SegEdge>> adj = buildAdjacency(chunks, lookup);

        Set<Long> visited = new HashSet<>();
        visited.add(currentSegId);
        ArrayDeque<FloorLink> queue = new ArrayDeque<>();
        queue.add(new FloorLink(currentSegId, currentSegId, Coord.z, false, lookup.segmentGridCount(currentSegId)));

        Map<Long, FloorLink> discovered = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            FloorLink cur = queue.remove();
            List<SegEdge> edges = adj.getOrDefault(cur.toSegId, Collections.emptyList());
            for (SegEdge edge : edges) {
                if (!visited.add(edge.toSegId)) {
                    continue;
                }
                boolean destIsBelow = cur.fromSegId == cur.toSegId ? edge.destIsBelow : cur.destIsBelow;
                FloorLink link = new FloorLink(
                        currentSegId,
                        edge.toSegId,
                        edge.offset.add(cur.tileOffset),
                        destIsBelow,
                        lookup.segmentGridCount(edge.toSegId));
                discovered.put(edge.toSegId, link);
                queue.add(link);
            }
        }
        return new ArrayList<>(discovered.values());
    }

    /**
     * Caller must hold {@code file.lock} read or write lock: {@code gridinfo} and {@code segments} require it.
     */
    public static GridLookup fromMapFile(MapFile file) {
        return new MapFileLookup(file);
    }

    public static List<FloorLink> linksFrom(MapFile file, ChunkNavGraph graph, long currentSegId) {
        if (file == null || graph == null) {
            return Collections.emptyList();
        }
        return linksFrom(currentSegId, graph.getAllChunks(), fromMapFile(file));
    }

    private static Map<Long, List<SegEdge>> buildAdjacency(Collection<ChunkNavData> chunks, GridLookup lookup) {
        Map<Long, SegEdge> byPair = new LinkedHashMap<>();
        for (ChunkNavData chunk : chunks) {
            if (chunk == null || !hasVerticalLink(chunk)) {
                continue;
            }
            GridRef src = lookup.find(chunk.gridId);
            if (src == null) {
                continue;
            }
            for (ChunkPortal portal : chunk.portals) {
                if (portal == null || !isVertical(portal.type) || portal.connectsToGridId == -1) {
                    continue;
                }
                GridRef dst = lookup.find(portal.connectsToGridId);
                if (dst == null || dst.segId == src.segId) {
                    continue;
                }
                Coord offset = computeOffset(src, portal.localCoord, dst, portal.exitLocalCoord);
                long key = pairKey(src.segId, dst.segId);
                SegEdge existing = byPair.get(key);
                if (existing == null) {
                    byPair.put(key, new SegEdge(src.segId, dst.segId, offset, isDown(portal.type)));
                } else if (disagrees(existing.offset, offset)) {
                    // Keep the first recorded alignment.
                }
            }
        }
        Map<Long, List<SegEdge>> adj = new HashMap<>();
        for (SegEdge edge : byPair.values()) {
            adj.computeIfAbsent(edge.fromSegId, k -> new ArrayList<>()).add(edge);
        }
        return adj;
    }

    private static boolean hasVerticalLink(ChunkNavData chunk) {
        if (chunk.portals == null) {
            return false;
        }
        for (ChunkPortal portal : chunk.portals) {
            if (portal != null && isVertical(portal.type) && portal.connectsToGridId != -1) {
                return true;
            }
        }
        return false;
    }

    private static boolean disagrees(Coord a, Coord b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)) > OFFSET_DISAGREE_TILES;
    }

    private static long pairKey(long fromSeg, long toSeg) {
        return (fromSeg * 31L) + toSeg;
    }

    private static final class SegEdge {
        final long fromSegId;
        final long toSegId;
        final Coord offset;
        final boolean destIsBelow;

        SegEdge(long fromSegId, long toSegId, Coord offset, boolean destIsBelow) {
            this.fromSegId = fromSegId;
            this.toSegId = toSegId;
            this.offset = offset;
            this.destIsBelow = destIsBelow;
        }
    }

    private static final class MapFileLookup implements GridLookup {
        private final MapFile file;

        MapFileLookup(MapFile file) {
            this.file = file;
        }

        @Override
        public GridRef find(long gridId) {
            MapFile.GridInfo info = file.gridinfo.get(gridId);
            if (info == null) {
                return null;
            }
            return new GridRef(info.seg, info.sc);
        }

        @Override
        public int segmentGridCount(long segId) {
            MapFile.Segment seg = file.segments.get(segId);
            return seg == null ? 0 : seg.map.size();
        }
    }
}
