package nurgling.overlays.map;

import haven.Area;
import haven.Coord;
import haven.Coord2d;
import haven.GOut;
import haven.GobIcon;
import haven.Loading;
import haven.MapFile;
import haven.MiniMap;
import haven.Tex;
import haven.TexI;
import haven.Text;
import haven.UI;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.map.FloorOverlayAligner;
import nurgling.navigation.ChunkNavGraph;
import nurgling.navigation.ChunkNavManager;
import nurgling.tools.FloorOverlayMarkerLogic;
import nurgling.widgets.NMiniMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static haven.MCache.cmaps;

/**
 * Draws another MapFile segment on top of the current minimap, using a private DisplayGrid cache.
 */
public class MinimapFloorOverlayRenderer {
    public static final int DEFAULT_ALPHA = 255;
    private static final int MAX_CACHE = 256;

    private final Map<CacheKey, MiniMap.DisplayGrid> cache = new HashMap<>();
    private long cacheSegId = Long.MIN_VALUE;
    private int cacheLvl = -1;
    private List<DrawItem> lastItems = Collections.emptyList();
    private FloorOverlayAligner.FloorLink lastLink = null;

    public void render(NMiniMap mm, GOut g) {
        if (!enabled() || mm.dloc == null || mm.file == null) {
            lastItems = Collections.emptyList();
            lastLink = null;
            return;
        }
        FloorOverlayAligner.FloorLink link = mm.selectedFloorLink;
        if (link == null || mm.dloc.seg == null || link.toSegId == mm.dloc.seg.id) {
            lastItems = Collections.emptyList();
            lastLink = null;
            return;
        }
        int dataLevel = mm.getDataLevelPublic();
        float currentScale = mm.getCurrentScale();
        Coord hsz = mm.sz.div(2);
        int alpha = overlayAlpha();

        List<DrawItem> items = collectVisible(mm, link, dataLevel);
        lastItems = items;
        lastLink = link;
        if (items.isEmpty()) {
            return;
        }

        g.chcolor(255, 255, 255, alpha);
        try {
            for (DrawItem item : items) {
                Coord2d ulDouble = new Coord2d(UI.scale(item.srcTileUl)).mul(currentScale)
                        .sub(new Coord2d(mm.dloc.tc.div(mm.scalef())))
                        .add(new Coord2d(hsz));
                Coord2d brDouble = new Coord2d(UI.scale(item.srcTileBr)).mul(currentScale)
                        .sub(new Coord2d(mm.dloc.tc.div(mm.scalef())))
                        .add(new Coord2d(hsz));
                Coord ul = new Coord((int) Math.floor(ulDouble.x), (int) Math.floor(ulDouble.y));
                Coord br = new Coord((int) Math.ceil(brDouble.x), (int) Math.ceil(brDouble.y));
                if (br.x < 0 || br.y < 0 || ul.x > mm.sz.x || ul.y > mm.sz.y) {
                    continue;
                }
                try {
                    Tex img = item.disp.img();
                    if (img != null) {
                        g.image(img, ul, br.sub(ul));
                    }
                } catch (Loading ignored) {
                }
            }
            drawOverlayMarks(mm, g, link, items, alpha);
        } finally {
            g.chcolor();
        }
    }

    private void drawOverlayMarks(NMiniMap mm, GOut g, FloorOverlayAligner.FloorLink link,
                                 List<DrawItem> items, int alpha) {
        if (mm.dloc == null || mm.dloc.seg == null
                || !FloorOverlayMarkerLogic.overlayActive(true, link.toSegId, mm.dloc.seg.id)) {
            return;
        }
        Coord hsz = mm.sz.div(2);
        float uiScale = UI.scale(1f);
        String pattern = mm.markerSearchPattern();
        boolean hideAll = mm.markersHidden();

        if (!hideAll) {
            for (DrawItem item : items) {
                for (MiniMap.DisplayMarker mark : item.disp.markers(true)) {
                    if (mm.filter(mark)) {
                        continue;
                    }
                    if (!FloorOverlayMarkerLogic.matchesSearch(mark.m.nm, pattern)) {
                        continue;
                    }
                    Coord screen = FloorOverlayMarkerLogic.destToScreen(
                            mark.m.tc, link.tileOffset, mm.dloc.tc, mm.scalef(),
                            mm.getCurrentScale(), hsz, uiScale);
                    if (!FloorOverlayMarkerLogic.onScreen(screen, mm.sz)) {
                        continue;
                    }
                    try {
                        mark.draw(g, screen);
                    } catch (Loading ignored) {
                    }
                }
            }
        }

        if (!FloorOverlayMarkerLogic.shouldShowProspecting(mm.showProspectingIcons, hideAll)) {
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.prospectingLocationService == null) {
            return;
        }
        for (nurgling.ProspectingLocation loc : gui.prospectingLocationService.getProspectingLocationsForSegment(link.toSegId)) {
            if (!FloorOverlayMarkerLogic.matchesSearch(loc.getResourceType(), pattern)) {
                continue;
            }
            Coord screen = FloorOverlayMarkerLogic.destToScreen(
                    loc.getTileCoords(), link.tileOffset, mm.dloc.tc, mm.scalef(),
                    mm.getCurrentScale(), hsz, uiScale);
            if (!FloorOverlayMarkerLogic.onScreen(screen, mm.sz)) {
                continue;
            }
            g.chcolor(255, 255, 255, alpha);
            mm.drawProspectingIconAt(g, screen, loc.getResourceType());
            g.chcolor(255, 255, 255, alpha);
        }
    }

    public Object tooltip(NMiniMap mm, Coord c) {
        if (mm.dloc == null || mm.dloc.seg == null || lastLink == null || lastItems.isEmpty()) {
            return null;
        }
        if (!enabled() || !FloorOverlayMarkerLogic.overlayActive(true, lastLink.toSegId, mm.dloc.seg.id)) {
            return null;
        }
        Coord hsz = mm.sz.div(2);
        float uiScale = UI.scale(1f);
        String pattern = mm.markerSearchPattern();
        boolean hideAll = mm.markersHidden();
        int prospectThreshold = UI.scale(10);

        if (FloorOverlayMarkerLogic.shouldShowProspecting(mm.showProspectingIcons, hideAll)) {
            NGameUI gui = NUtils.getGameUI();
            if (gui != null && gui.prospectingLocationService != null) {
                for (nurgling.ProspectingLocation loc : gui.prospectingLocationService.getProspectingLocationsForSegment(lastLink.toSegId)) {
                    if (!FloorOverlayMarkerLogic.matchesSearch(loc.getResourceType(), pattern)) {
                        continue;
                    }
                    Coord screen = FloorOverlayMarkerLogic.destToScreen(
                            loc.getTileCoords(), lastLink.tileOffset, mm.dloc.tc, mm.scalef(),
                            mm.getCurrentScale(), hsz, uiScale);
                    if (FloorOverlayMarkerLogic.hoverHit(c, screen, prospectThreshold)) {
                        String resourceType = loc.getResourceType();
                        return Text.render(resourceType != null ? resourceType : "Unknown");
                    }
                }
            }
        }

        if (!hideAll) {
            for (DrawItem item : lastItems) {
                for (MiniMap.DisplayMarker mark : item.disp.markers(false)) {
                    if (mm.filter(mark) || !FloorOverlayMarkerLogic.matchesSearch(mark.m.nm, pattern)) {
                        continue;
                    }
                    Coord screen = FloorOverlayMarkerLogic.destToScreen(
                            mark.m.tc, lastLink.tileOffset, mm.dloc.tc, mm.scalef(),
                            mm.getCurrentScale(), hsz, uiScale);
                    try {
                        GobIcon.Icon icon = mark.icon();
                        if (icon != null && icon.checkhit(c.sub(screen))) {
                            return new TexI(mark.tooltip());
                        }
                    } catch (Loading ignored) {
                    }
                }
            }
        }
        return null;
    }

    public static boolean enabled() {
        Object val = NConfig.get(NConfig.Key.floorOverlayEnable);
        return val instanceof Boolean && (Boolean) val;
    }

    public static int overlayAlpha() {
        return DEFAULT_ALPHA;
    }

    public static long selectedSegId() {
        Object val = NConfig.get(NConfig.Key.floorOverlaySegId);
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }

    public static List<FloorOverlayAligner.FloorLink> computeLinks(NMiniMap mm) {
        if (mm == null || mm.dloc == null || mm.file == null) {
            return Collections.emptyList();
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || !(gui.map instanceof NMapView)) {
            return Collections.emptyList();
        }
        ChunkNavManager manager = ((NMapView) gui.map).getChunkNavManager();
        if (manager == null) {
            return Collections.emptyList();
        }
        ChunkNavGraph graph = manager.getGraph();
        if (graph == null) {
            return Collections.emptyList();
        }
        try {
            if (!mm.file.lock.readLock().tryLock(50, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            return FloorOverlayAligner.linksFrom(mm.file, graph, mm.dloc.seg.id);
        } finally {
            mm.file.lock.readLock().unlock();
        }
    }

    public static FloorOverlayAligner.FloorLink pickLink(List<FloorOverlayAligner.FloorLink> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        long want = selectedSegId();
        if (want != 0L) {
            for (FloorOverlayAligner.FloorLink link : links) {
                if (link.toSegId == want) {
                    return link;
                }
            }
        }
        return links.get(0);
    }

    private List<DrawItem> collectVisible(NMiniMap mm, FloorOverlayAligner.FloorLink link, int dataLevel) {
        List<DrawItem> items = new ArrayList<>();
        Area dgext = mm.getDgext();
        if (dgext == null || !dgext.positive()) {
            return items;
        }
        int gridTileSize = cmaps.x * (1 << dataLevel);
        Area currentTiles = Area.sized(dgext.ul.mul(gridTileSize), dgext.sz().mul(gridTileSize));
        Area destGrids = FloorOverlayAligner.visibleDestGridArea(currentTiles, link.tileOffset, dataLevel);
        if (!destGrids.positive()) {
            return items;
        }
        try {
            if (!mm.file.lock.readLock().tryLock(50, TimeUnit.MILLISECONDS)) {
                return items;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return items;
        }
        try {
            MapFile.Segment destSeg = mm.file.segments.get(link.toSegId);
            if (destSeg == null) {
                return items;
            }
            if (cacheSegId != link.toSegId || cacheLvl != dataLevel) {
                cache.clear();
                cacheSegId = link.toSegId;
                cacheLvl = dataLevel;
            }
            int step = 1 << dataLevel;
            for (int y = destGrids.ul.y; y < destGrids.br.y; y += step) {
                for (int x = destGrids.ul.x; x < destGrids.br.x; x += step) {
                    Coord aligned = new Coord(x, y);
                    if (!hasDestGrid(destSeg, aligned, step)) {
                        continue;
                    }
                    Coord srcUl = aligned.mul(cmaps).add(link.tileOffset);
                    Coord srcBr = aligned.add(step, step).mul(cmaps).add(link.tileOffset);
                    MiniMap.DisplayGrid disp = displayGrid(mm, destSeg, aligned, dataLevel);
                    if (disp != null) {
                        items.add(new DrawItem(disp, srcUl, srcBr));
                    }
                }
            }
        } finally {
            mm.file.lock.readLock().unlock();
        }
        return items;
    }

    private static boolean hasDestGrid(MapFile.Segment destSeg, Coord aligned, int step) {
        if (step <= 1) {
            return destSeg.map.get(aligned) != null;
        }
        for (int dy = 0; dy < step; dy++) {
            for (int dx = 0; dx < step; dx++) {
                if (destSeg.map.get(aligned.add(dx, dy)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private MiniMap.DisplayGrid displayGrid(NMiniMap mm, MapFile.Segment destSeg, Coord aligned, int dataLevel) {
        CacheKey key = new CacheKey(destSeg.id, aligned, dataLevel);
        MiniMap.DisplayGrid disp = cache.get(key);
        if (disp != null) {
            return disp;
        }
        if (cache.size() >= MAX_CACHE) {
            return cache.get(key);
        }
        Coord zc = new Coord(aligned.x >> dataLevel, aligned.y >> dataLevel);
        disp = new MiniMap.DisplayGrid(mm, destSeg, zc, dataLevel, destSeg.grid(dataLevel, aligned));
        cache.put(key, disp);
        return disp;
    }

    private static final class DrawItem {
        final MiniMap.DisplayGrid disp;
        final Coord srcTileUl;
        final Coord srcTileBr;

        DrawItem(MiniMap.DisplayGrid disp, Coord srcTileUl, Coord srcTileBr) {
            this.disp = disp;
            this.srcTileUl = srcTileUl;
            this.srcTileBr = srcTileBr;
        }
    }

    private static final class CacheKey {
        final long segId;
        final Coord sc;
        final int lvl;

        CacheKey(long segId, Coord sc, int lvl) {
            this.segId = segId;
            this.sc = sc;
            this.lvl = lvl;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) o;
            return segId == other.segId && lvl == other.lvl && Objects.equals(sc, other.sc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(segId, sc, lvl);
        }
    }
}
