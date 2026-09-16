package nurgling.tools;

import haven.Coord;
import haven.Coord2d;
import haven.Line2d;
import haven.Loading;
import haven.MCache;
import haven.MiniMap;
import haven.resutil.Ridges;
import nurgling.NGameUI;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class StraightPathObstacle {
    private StraightPathObstacle() {}

    static final int MAX_SAMPLE_TILES = 300;
    static final long RETRY_NANOS = 200_000_000L;

    public static boolean blocked(MCache map, Coord2d from, Coord2d to) {
        return inspect(map, from, to).blocked;
    }

    public static boolean blockedLeg(NGameUI gui, Coord2d from, Coord2d to) {
        if(gui == null || from == null || to == null)
            return false;
        if(gui.map == null || gui.map.glob == null || gui.map.glob.map == null)
            return false;
        return gui.pathObstacles.blocked(gui.map.glob.map, from, to);
    }

    public static Coord2d sessionWorld(MiniMap.Location sessloc, Coord tc, long segId) {
        if(sessloc == null || tc == null)
            return null;
        if(sessloc.seg.id != segId)
            return null;
        return tc.sub(sessloc.tc).mul(MCache.tilesz).add(MCache.tilehsz);
    }

    static final class Sample {
        final boolean blocked;
        final boolean complete;

        Sample(boolean blocked, boolean complete) {
            this.blocked = blocked;
            this.complete = complete;
        }
    }

    static Sample inspect(MCache map, Coord2d from, Coord2d to) {
        if(map == null || from == null || to == null)
            return new Sample(false, true);
        double dist = from.dist(to);
        if(dist < 0.01)
            return tileSample(map, from.floor(MCache.tilesz));
        Coord2d cappedTo = to;
        double maxDist = MAX_SAMPLE_TILES * MCache.tilesz.x;
        if(dist > maxDist)
            cappedTo = from.add(to.sub(from).mul(maxDist / dist));
        boolean complete = true;
        Coord2d prev = null;
        for(Coord2d p : new Line2d.GridIsect(from, cappedTo, MCache.tilesz, true)) {
            if(prev != null) {
                Coord tile = prev.add(p).div(2).floor(MCache.tilesz);
                Sample s = tileSample(map, tile);
                if(s.blocked)
                    return new Sample(true, true);
                if(!s.complete)
                    complete = false;
            }
            prev = p;
        }
        return new Sample(false, complete);
    }

    private static Sample tileSample(MCache map, Coord tile) {
        try {
            if(Ridges.brokenp(map, tile))
                return new Sample(true, true);
            String name = map.tilesetname(map.gettile(tile));
            if(name == null)
                return new Sample(false, false);
            if(name.startsWith("gfx/tiles/deepcave") || name.startsWith("gfx/tiles/deeptangle"))
                return new Sample(false, true);
            if(name.contains("/cavein") || name.contains("/caveout"))
                return new Sample(false, true);
            if(name.startsWith("gfx/tiles/cave") || name.startsWith("gfx/tiles/rocks"))
                return new Sample(true, true);
            return new Sample(false, true);
        } catch(Loading l) {
            return new Sample(false, false);
        }
    }

    public static final class Cache {
        private static final class Entry {
            boolean blocked;
            boolean complete;
            long atNanos;
        }

        private static final class Key {
            final int ax, ay, bx, by;

            Key(int ax, int ay, int bx, int by) {
                this.ax = ax;
                this.ay = ay;
                this.bx = bx;
                this.by = by;
            }

            static Key of(Coord2d from, Coord2d to) {
                Coord a = from.floor(MCache.tilesz);
                Coord b = to.floor(MCache.tilesz);
                return new Key(a.x, a.y, b.x, b.y);
            }

            @Override
            public boolean equals(Object o) {
                if(!(o instanceof Key))
                    return false;
                Key k = (Key)o;
                return ax == k.ax && ay == k.ay && bx == k.bx && by == k.by;
            }

            @Override
            public int hashCode() {
                int h = ax;
                h = 31 * h + ay;
                h = 31 * h + bx;
                h = 31 * h + by;
                return h;
            }
        }

        private final LongSupplier clock;
        private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<Key, Entry>();

        public Cache() {
            this(new LongSupplier() {
                public long getAsLong() { return System.nanoTime(); }
            });
        }

        public Cache(LongSupplier clock) {
            this.clock = clock;
        }

        public boolean blocked(MCache map, Coord2d from, Coord2d to) {
            if(map == null || from == null || to == null)
                return false;
            Key key = Key.of(from, to);
            long now = clock.getAsLong();
            Entry e = entries.get(key);
            if(e != null && e.complete)
                return e.blocked;
            if(e != null && (now - e.atNanos) < RETRY_NANOS)
                return e.blocked;
            Sample s = inspect(map, from, to);
            Entry n = new Entry();
            n.blocked = s.blocked;
            n.complete = s.complete || s.blocked;
            n.atNanos = now;
            entries.put(key, n);
            return n.blocked;
        }
    }
}
