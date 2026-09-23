package nurgling.overlays.map;

import haven.*;
import nurgling.NConfig;
import nurgling.tools.ExploredArea;
import nurgling.tools.ExploredAreaPolicy;
import nurgling.widgets.NCornerMiniMap;
import nurgling.widgets.NMiniMap;

import java.awt.Color;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Draws explored-area fog on the same cells as the minimap image.
 * One texture per visible map cell, shared by every window at that zoom,
 * and rebuilt only when that cell's tiles change.
 */
public class MinimapExploredAreaRenderer {

    private static final int MAX_CACHE_SIZE = 512;
    private static final long SIG_BASIS = 0xCBF29CE484222325L;

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
            if (map.ui.gui.mmap instanceof NCornerMiniMap) {
                exploredArea = ((NCornerMiniMap) map.ui.gui.mmap).exploredArea;
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

            int dataLevel = nmap.getDataLevelPublic();
            // Same factor NMiniMap.drawmap uses to place each display cell.
            float scaleFactor = nmap.getCurrentScale() * (1 << dataLevel);
            Coord hsz = map.sz.div(2);
            Coord camera = map.dloc.tc.div(map.scalef());
            long segmentId = map.sessloc.seg.id;

            drawLayer(g, map, exploredArea, display, dgext, hsz, camera,
                    scaleFactor, dataLevel, segmentId, false, NMiniMap.VIEW_EXPLORED_COLOR,
                    ExploredArea.seq);
            if (exploredArea.isSessionActive()) {
                drawLayer(g, map, exploredArea, display, dgext, hsz, camera,
                        scaleFactor, dataLevel, segmentId, true, NMiniMap.VIEW_SESSION_COLOR,
                        ExploredArea.sessionSeq);
            }
        } catch (Exception e) {
            // Silently handle errors
        }
    }

    private static void drawLayer(GOut g, MiniMap map, ExploredArea exploredArea,
            MiniMap.DisplayGrid[] display, Area dgext, Coord hsz, Coord camera,
            float scaleFactor, int dataLevel, long segmentId, boolean session, Color color,
            long worldSeq) {
        g.chcolor(color);
        try {
            for (Coord gc : dgext) {
                MiniMap.DisplayGrid disp = display[dgext.ri(gc)];
                if (disp == null)
                    continue;
                Coord[] bounds = cellBounds(gc, scaleFactor, camera, hsz);
                Coord ul = bounds[0];
                Coord br = bounds[1];
                if (br.x <= ul.x || br.y <= ul.y)
                    continue;
                if (br.x < 0 || br.y < 0 || ul.x > map.sz.x || ul.y > map.sz.y)
                    continue;

                CacheKey key = new CacheKey(gc, segmentId, dataLevel, session);
                OverlayCache cache = overlayCache.get(key);
                if (cache != null && cache.worldSeq == worldSeq) {
                    cache.lastAccess = accessCounter.incrementAndGet();
                    blit(g, cache.img, ul, br.sub(ul));
                    continue;
                }

                long sig = cellSignature(exploredArea, gc, segmentId, dataLevel, session);
                if (cache != null && cache.sig == sig) {
                    cache.worldSeq = worldSeq;
                    cache.lastAccess = accessCounter.incrementAndGet();
                    blit(g, cache.img, ul, br.sub(ul));
                    continue;
                }
                Tex img = buildCell(exploredArea, gc, segmentId, dataLevel, session, color);
                store(key, cache, img, sig, worldSeq);
                blit(g, img, ul, br.sub(ul));
            }
        } finally {
            g.chcolor();
        }
    }

    private static void blit(GOut g, Tex img, Coord ul, Coord sz) {
        if (img != null)
            g.image(img, ul, sz);
    }

    /**
     * Screen rectangle of one minimap display cell. Upper edge is floored and the
     * lower edge is ceiled, matching {@code NMiniMap.drawmap}, so neighbouring cells share a border.
     */
    static Coord[] cellBounds(Coord cell, float scaleFactor, Coord camera, Coord hsz) {
        Coord2d origin = new Coord2d(camera);
        Coord2d ulDouble = new Coord2d(UI.scale(cell.mul(MCache.cmaps))).mul(scaleFactor).sub(origin).add(new Coord2d(hsz));
        Coord2d brDouble = new Coord2d(UI.scale(cell.add(1, 1).mul(MCache.cmaps))).mul(scaleFactor).sub(origin).add(new Coord2d(hsz));
        Coord ul = new Coord((int) Math.floor(ulDouble.x), (int) Math.floor(ulDouble.y));
        Coord br = new Coord((int) Math.ceil(brDouble.x), (int) Math.ceil(brDouble.y));
        return new Coord[] {ul, br};
    }

    /** Pixel in the display-cell fog image for an absolute tile, or -1 when the tile is outside the cell. */
    static int coveragePixel(int dataLevel, Coord cell, int tileX, int tileY) {
        int step = 1 << dataLevel;
        int tiles = MCache.cmaps.x;
        int dx = tileX - cell.x * tiles * step;
        int dy = tileY - cell.y * tiles * step;
        if (dx < 0 || dy < 0)
            return -1;
        int px = dx / step;
        int py = dy / step;
        if (px >= tiles || py >= tiles)
            return -1;
        return px + py * tiles;
    }

    static long cellSignature(ExploredArea exploredArea, Coord cell, long segmentId, int dataLevel, boolean session) {
        int step = 1 << dataLevel;
        long sig = SIG_BASIS;
        int originX = cell.x * step;
        int originY = cell.y * step;
        for (int by = 0; by < step; by++) {
            for (int bx = 0; bx < step; bx++) {
                long gen = exploredArea.gridGeneration(originX + bx, originY + by, segmentId, session);
                sig ^= (gen + bx + ((long) by << 16)) * 0x100000001B3L;
            }
        }
        return sig;
    }

    private static Tex buildCell(ExploredArea exploredArea, Coord cell, long segmentId, int dataLevel, boolean session, Color color) {
        int tiles = MCache.cmaps.x;
        boolean[] cover = new boolean[tiles * tiles];
        int step = 1 << dataLevel;
        int originX = cell.x * step;
        int originY = cell.y * step;
        boolean any = false;
        for (int by = 0; by < step; by++) {
            for (int bx = 0; bx < step; bx++) {
                ExploredArea.GridMask grid = session
                        ? exploredArea.getSessionMaskForGrid(new Coord(originX + bx, originY + by), segmentId)
                        : exploredArea.getExploredMaskForGrid(new Coord(originX + bx, originY + by), segmentId, 0);
                if (grid == null || !grid.hasAny || grid.mask == null)
                    continue;
                if (paintMask(cover, tiles, dataLevel, cell, originX + bx, originY + by, grid.mask))
                    any = true;
            }
        }
        if (!any)
            return null;
        WritableRaster buf = PUtils.imgraster(MCache.cmaps);
        byte[] raw = ((DataBufferByte) buf.getDataBuffer()).getData();
        byte r = (byte) color.getRed();
        byte g = (byte) color.getGreen();
        byte b = (byte) color.getBlue();
        byte a = (byte) color.getAlpha();
        for (int i = 0; i < cover.length; i++) {
            if (!cover[i])
                continue;
            int o = i * 4;
            raw[o] = r;
            raw[o + 1] = g;
            raw[o + 2] = b;
            raw[o + 3] = a;
        }
        return new TexI(PUtils.rasterimg(buf));
    }

    /** Sets destination pixels covered by one base-grid mask. Far zoom samples the block center. */
    static boolean paintMask(boolean[] dest, int width, int dataLevel, Coord cell, int baseGx, int baseGy, boolean[] mask) {
        int step = 1 << dataLevel;
        int tiles = MCache.cmaps.x;
        int originX = cell.x * tiles * step;
        int originY = cell.y * tiles * step;
        int baseX = baseGx * tiles;
        int baseY = baseGy * tiles;
        boolean any = false;
        int sample = step / 2;
        int yStride = dataLevel > 2 ? step : 1;
        int xStride = yStride;
        int ty0 = 0;
        int tx0 = 0;
        if (dataLevel > 2) {
            ty0 = sample - Math.floorMod(baseY - originY, step);
            if (ty0 < 0)
                ty0 += step;
            tx0 = sample - Math.floorMod(baseX - originX, step);
            if (tx0 < 0)
                tx0 += step;
        }
        for (int ty = ty0; ty < tiles; ty += yStride) {
            int relY = baseY + ty - originY;
            if (relY < 0)
                continue;
            int py = relY / step;
            if (py >= width)
                continue;
            int row = ty * tiles;
            for (int tx = tx0; tx < tiles; tx += xStride) {
                if (!mask[row + tx])
                    continue;
                int relX = baseX + tx - originX;
                if (relX < 0)
                    continue;
                int px = relX / step;
                if (px >= width)
                    continue;
                dest[py * width + px] = true;
                any = true;
            }
        }
        return any;
    }

    private static class OverlayCache {
        Tex img;
        long sig;
        long worldSeq;
        long lastAccess;
    }

    private static final class CacheKey {
        final Coord cell;
        final long segmentId;
        final int dataLevel;
        final boolean session;

        CacheKey(Coord cell, long segmentId, int dataLevel, boolean session) {
            this.cell = cell;
            this.segmentId = segmentId;
            this.dataLevel = dataLevel;
            this.session = session;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey k = (CacheKey) o;
            return session == k.session && dataLevel == k.dataLevel && segmentId == k.segmentId && cell.equals(k.cell);
        }

        @Override
        public int hashCode() {
            int h = cell.hashCode();
            h = h * 31 + Long.hashCode(segmentId);
            h = h * 31 + dataLevel;
            return h * 31 + (session ? 1 : 0);
        }
    }

    private static final ConcurrentHashMap<CacheKey, OverlayCache> overlayCache = new ConcurrentHashMap<>();
    private static final AtomicLong accessCounter = new AtomicLong(0);

    public static void clearCaches() {
        for (OverlayCache entry : overlayCache.values()) {
            if (entry.img != null) {
                try { entry.img.dispose(); } catch (Exception ignore) {}
            }
        }
        overlayCache.clear();
    }

    private static void store(CacheKey key, OverlayCache cache, Tex img, long sig, long worldSeq) {
        if (cache != null && cache.img != null && cache.img != img) {
            try { cache.img.dispose(); } catch (Exception ignore) {}
        }
        if (cache == null) {
            cache = new OverlayCache();
            overlayCache.put(key, cache);
        }
        cache.img = img;
        cache.sig = sig;
        cache.worldSeq = worldSeq;
        cache.lastAccess = accessCounter.incrementAndGet();
        evictIfNeeded();
    }

    private static void evictIfNeeded() {
        if (overlayCache.size() <= MAX_CACHE_SIZE) return;
        int toRemove = overlayCache.size() - MAX_CACHE_SIZE + MAX_CACHE_SIZE / 4;
        long minAccess = Long.MAX_VALUE;
        for (OverlayCache v : overlayCache.values()) {
            if (v.lastAccess < minAccess) minAccess = v.lastAccess;
        }
        long threshold = minAccess + (accessCounter.get() - minAccess) / 2;
        int removed = 0;
        Iterator<Map.Entry<CacheKey, OverlayCache>> it = overlayCache.entrySet().iterator();
        while (it.hasNext() && removed < toRemove) {
            OverlayCache entry = it.next().getValue();
            if (entry.lastAccess <= threshold) {
                if (entry.img != null) {
                    try { entry.img.dispose(); } catch (Exception ignore) {}
                }
                it.remove();
                removed++;
            }
        }
    }
}
