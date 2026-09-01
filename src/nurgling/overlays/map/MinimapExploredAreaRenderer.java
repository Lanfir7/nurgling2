package nurgling.overlays.map;

import haven.*;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.tools.ExploredArea;
import nurgling.tools.ExploredAreaPolicy;
import nurgling.widgets.NCornerMiniMap;
import nurgling.widgets.NMiniMap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Renders explored area overlay on the minimap.
 * Uses ExploredArea rectangle data to generate overlay masks for each DisplayGrid.
 * Similar to MinimapClaimRenderer but for explored (visited) areas.
 * 
 * Supports rendering both main explored area and session layer.
 */
public class MinimapExploredAreaRenderer {

    private static final int MAX_CACHE_SIZE = 200;

    public static void renderExploredArea(MiniMap map, GOut g) {
        if (!ExploredAreaPolicy.shouldDraw(NConfig.get(NConfig.Key.exploredAreaEnable))) {
            return;
        }
        
        if (map.ui == null || map.ui.gui == null || map.ui.gui.map == null) {
            return;
        }

        MapView mv = map.ui.gui.map;
        if (mv == null || map.dloc == null || map.sessloc == null) {
            return;
        }

        if (!(map instanceof NMiniMap)) {
            return;
        }
        NMiniMap nmap = (NMiniMap) map;

        MiniMap.DisplayGrid[] display = nmap.getDisplay();
        Area dgext = nmap.getDgext();

        if (display == null || dgext == null) {
            return;
        }

        try {
            ExploredArea exploredArea = null;
            if (NUtils.getGameUI() != null && NUtils.getGameUI().mmap instanceof NCornerMiniMap) {
                exploredArea = ((NCornerMiniMap) NUtils.getGameUI().mmap).exploredArea;
            }
            if (exploredArea == null) {
                exploredArea = nmap.exploredArea;
            }
            if (exploredArea == null) {
                return;
            }

            if (exploredArea.isLoadingInProgress()) {
                return;
            }

            boolean playerSegment = (map.sessloc != null) && 
                                   ((map.curloc == null) || (map.sessloc.seg.id == map.curloc.seg.id));
            if (!playerSegment) {
                return;
            }

            Coord hsz = map.sz.div(2);
            int dataLevel = nmap.getDataLevelPublic();
            float scaleFactor = nmap.getCurrentScale();
            int gridScale = (1 << dataLevel);
            
            for (Coord gc : dgext) {
                MiniMap.DisplayGrid disp = display[dgext.ri(gc)];
                if (disp == null) {
                    continue;
                }
                
                Coord baseGridStart = disp.sc.mul(gridScale);
                Coord baseGridEnd = baseGridStart.add(gridScale, gridScale);
                
                for (int bgy = baseGridStart.y; bgy < baseGridEnd.y; bgy++) {
                    for (int bgx = baseGridStart.x; bgx < baseGridEnd.x; bgx++) {
                        Coord baseGridCoord = new Coord(bgx, bgy);
                        
                        ExploredArea.GridMask baseGridMask = exploredArea.getExploredMaskForGrid(baseGridCoord, map.sessloc.seg.id, 0);
                        if (baseGridMask != null && baseGridMask.hasAny) {
                            renderGridOverlay(g, map, nmap, baseGridCoord, baseGridMask, 
                                NMiniMap.VIEW_EXPLORED_COLOR, hsz, scaleFactor, dataLevel, false);
                        }
                        
                        ExploredArea.GridMask sessionGridMask = exploredArea.getSessionMaskForGrid(baseGridCoord, map.sessloc.seg.id);
                        if (sessionGridMask != null && sessionGridMask.hasAny) {
                            renderGridOverlay(g, map, nmap, baseGridCoord, sessionGridMask,
                                NMiniMap.VIEW_SESSION_COLOR, hsz, scaleFactor, dataLevel, true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle errors
        }
    }
    
    private static void renderGridOverlay(GOut g, MiniMap map, NMiniMap nmap, 
            Coord baseGridCoord, ExploredArea.GridMask gridMask, Color color,
            Coord hsz, float scaleFactor, int dataLevel, boolean isSession) {
        try {
            Tex overlayImg = isSession 
                ? getSessionOverlay(baseGridCoord, map.sessloc.seg.id, gridMask, dataLevel)
                : getExploredOverlay(baseGridCoord, map.sessloc.seg.id, gridMask, dataLevel);
            
            if (overlayImg != null) {
                int gridTileSize = MCache.cmaps.x;
                int bgx = baseGridCoord.x;
                int bgy = baseGridCoord.y;
                
                Coord baseTileUL = new Coord(bgx * gridTileSize, bgy * gridTileSize);
                Coord baseTileBR = new Coord((bgx + 1) * gridTileSize, (bgy + 1) * gridTileSize);
                Coord2d screenULDouble = new Coord2d(UI.scale(baseTileUL)).mul(scaleFactor).sub(new Coord2d(map.dloc.tc.div(map.scalef()))).add(new Coord2d(hsz));
                Coord2d screenBRDouble = new Coord2d(UI.scale(baseTileBR)).mul(scaleFactor).sub(new Coord2d(map.dloc.tc.div(map.scalef()))).add(new Coord2d(hsz));
                Coord screenUL = new Coord((int)Math.round(screenULDouble.x), (int)Math.round(screenULDouble.y));
                Coord screenBR = new Coord((int)Math.round(screenBRDouble.x), (int)Math.round(screenBRDouble.y));
                
                Coord imgsz = screenBR.sub(screenUL);
                
                g.chcolor(color);
                g.image(overlayImg, screenUL, imgsz);
                g.chcolor();
            }
        } catch (Exception e) {
            // Ignore rendering errors
        }
    }

    private static class ExploredOverlayCache {
        Tex img;
        long seq;
        int dataLevel;
        long lastAccess;
    }
    
    private static class CacheKey {
        final Coord baseGridCoord;
        final long segmentId;
        
        CacheKey(Coord baseGridCoord, long segmentId) {
            this.baseGridCoord = baseGridCoord;
            this.segmentId = segmentId;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey k = (CacheKey) o;
            return segmentId == k.segmentId && baseGridCoord.equals(k.baseGridCoord);
        }
        
        @Override
        public int hashCode() {
            return baseGridCoord.hashCode() * 31 + Long.hashCode(segmentId);
        }
    }
    
    private static final ConcurrentHashMap<CacheKey, ExploredOverlayCache> overlayCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<CacheKey, ExploredOverlayCache> sessionOverlayCache = new ConcurrentHashMap<>();
    private static final AtomicLong accessCounter = new AtomicLong(0);

    public static void clearCaches() {
        disposeCacheEntries(overlayCache);
        disposeCacheEntries(sessionOverlayCache);
    }

    private static void disposeCacheEntries(ConcurrentHashMap<CacheKey, ExploredOverlayCache> cache) {
        for (ExploredOverlayCache entry : cache.values()) {
            if (entry.img != null) {
                try { entry.img.dispose(); } catch (Exception ignore) {}
            }
        }
        cache.clear();
    }

    private static void evictIfNeeded(ConcurrentHashMap<CacheKey, ExploredOverlayCache> cache) {
        if (cache.size() <= MAX_CACHE_SIZE) return;
        int toRemove = cache.size() - MAX_CACHE_SIZE + MAX_CACHE_SIZE / 4;
        long minAccess = Long.MAX_VALUE;
        for (ExploredOverlayCache v : cache.values()) {
            if (v.lastAccess < minAccess) minAccess = v.lastAccess;
        }
        long threshold = minAccess + (accessCounter.get() - minAccess) / 2;
        int removed = 0;
        Iterator<Map.Entry<CacheKey, ExploredOverlayCache>> it = cache.entrySet().iterator();
        while (it.hasNext() && removed < toRemove) {
            ExploredOverlayCache entry = it.next().getValue();
            if (entry.lastAccess <= threshold) {
                if (entry.img != null) {
                    try { entry.img.dispose(); } catch (Exception ignore) {}
                }
                it.remove();
                removed++;
            }
        }
    }

    private static Tex getExploredOverlay(Coord baseGridCoord, long segmentId, ExploredArea.GridMask gridMask, int dataLevel) {
        CacheKey key = new CacheKey(baseGridCoord, segmentId);
        ExploredOverlayCache cache = overlayCache.get(key);
        long access = accessCounter.incrementAndGet();
        
        if (cache != null && cache.seq == gridMask.seq && cache.dataLevel == dataLevel) {
            cache.lastAccess = access;
            return cache.img;
        }
        
        try {
            BufferedImage overlayBuf = renderOverlayImage(gridMask.mask, NMiniMap.VIEW_EXPLORED_COLOR);
            Tex overlayTex = new TexI(overlayBuf);
            
            if (cache != null && cache.img != null) {
                try { cache.img.dispose(); } catch (Exception ignore) {}
            }
            
            if (cache == null) {
                cache = new ExploredOverlayCache();
                overlayCache.put(key, cache);
            }
            cache.img = overlayTex;
            cache.seq = gridMask.seq;
            cache.dataLevel = dataLevel;
            cache.lastAccess = access;
            
            evictIfNeeded(overlayCache);
            return overlayTex;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Tex getSessionOverlay(Coord baseGridCoord, long segmentId, ExploredArea.GridMask gridMask, int dataLevel) {
        CacheKey key = new CacheKey(baseGridCoord, segmentId);
        ExploredOverlayCache cache = sessionOverlayCache.get(key);
        long access = accessCounter.incrementAndGet();
        
        if (cache != null && cache.seq == gridMask.seq && cache.dataLevel == dataLevel) {
            cache.lastAccess = access;
            return cache.img;
        }
        
        try {
            BufferedImage overlayBuf = renderOverlayImage(gridMask.mask, NMiniMap.VIEW_SESSION_COLOR);
            Tex overlayTex = new TexI(overlayBuf);
            
            if (cache != null && cache.img != null) {
                try { cache.img.dispose(); } catch (Exception ignore) {}
            }
            
            if (cache == null) {
                cache = new ExploredOverlayCache();
                sessionOverlayCache.put(key, cache);
            }
            cache.img = overlayTex;
            cache.seq = gridMask.seq;
            cache.dataLevel = dataLevel;
            cache.lastAccess = access;
            
            evictIfNeeded(sessionOverlayCache);
            return overlayTex;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage renderOverlayImage(boolean[] mask, Color col) {
        WritableRaster buf = PUtils.imgraster(MCache.cmaps);
        
        int width = MCache.cmaps.x;
        int height = MCache.cmaps.y;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = x + y * width;
                if (mask[idx]) {
                    buf.setSample(x, y, 0, col.getRed());
                    buf.setSample(x, y, 1, col.getGreen());
                    buf.setSample(x, y, 2, col.getBlue());
                    buf.setSample(x, y, 3, col.getAlpha());
                }
            }
        }
        
        return PUtils.rasterimg(buf);
    }
}
