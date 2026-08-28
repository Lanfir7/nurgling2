package nurgling.overlays;

import haven.*;
import haven.render.*;
import haven.res.lib.tree.*;
import nurgling.*;
import nurgling.overlays.map.*;

public class NMiningSupport extends Sprite implements RenderTree.Node
{
    public static final class Spec {
        public final Integer circleRadius;
        public final int widthTiles;
        public final int lengthTiles;

        private Spec(Integer circleRadius, int widthTiles, int lengthTiles) {
            this.circleRadius = circleRadius;
            this.widthTiles = widthTiles;
            this.lengthTiles = lengthTiles;
        }

        public static Spec circle(int radius) {
            return new Spec(radius, 0, 0);
        }

        public static Spec rect(int widthTiles, int lengthTiles) {
            return new Spec(null, widthTiles, lengthTiles);
        }

        public boolean isRect() {
            return circleRadius == null;
        }
    }

    public static final class Mask {
        public final Coord begin;
        public final Coord end;
        public final boolean[][] data;

        public Mask(Coord begin, Coord end, boolean[][] data) {
            this.begin = begin;
            this.end = end;
            this.data = data;
        }
    }

    public static Spec specFor(String name) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "gfx/terobjs/map/naturalminesupport":
                return Spec.circle(92);
            case "gfx/terobjs/ladder":
            case "gfx/terobjs/minesupport":
            case "gfx/terobjs/trees/towercap":
                return Spec.circle(100);
            case "gfx/terobjs/column":
                return Spec.circle(125);
            case "gfx/terobjs/minebeam":
                return Spec.circle(150);
            case "gfx/terobjs/monumentalcolumn":
                return Spec.circle(330);
            case "gfx/terobjs/timbertunnel":
                return Spec.rect(1, 5);
            case "gfx/terobjs/reinforcedtunnel":
                return Spec.rect(2, 8);
            case "gfx/terobjs/stonearchtunnel":
                return Spec.rect(3, 15);
            default:
                return null;
        }
    }

    /**
     * Forward is gob facing ({@code a=0} → +X), snapped to the nearest cardinal.
     * Positive directions start on the gob tile; negative directions start one tile ahead.
     * Even widths are biased toward the negative world coordinate of the lateral axis
     * to match vanilla's tile anchoring in every cardinal direction.
     * {@link Mask#end} is the inclusive last lit tile.
     */
    public static Mask computeRect(Coord2d rc, double angle, int widthTiles, int lengthTiles) {
        return computeRect(rc, angle, widthTiles, lengthTiles, 0);
    }

    private static Mask computeRect(Coord2d rc, double angle, int widthTiles, int lengthTiles,
                                    int forwardShiftTiles) {
        Coord origin = rc.div(MCache.tilesz).floor();
        Coord2d localFwd = Coord2d.of(1, 0).rot(angle);
        Coord2d localRight = Coord2d.of(0, 1).rot(angle);
        Coord fwd = snapCardinal(localFwd);
        Coord right = snapCardinal(localRight);
        int i0 = ((fwd.x < 0 || fwd.y < 0) ? 1 : 0) + forwardShiftTiles;
        int i1 = i0 + lengthTiles - 1;
        int j0 = (right.x > 0 || right.y > 0)
                ? -Math.floorDiv(widthTiles, 2)
                : -Math.floorDiv(widthTiles - 1, 2);
        int j1 = j0 + widthTiles - 1;
        int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE;
        java.util.ArrayList<Coord> tiles = new java.util.ArrayList<>();
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                Coord t = origin.add(fwd.mul(i)).add(right.mul(j));
                tiles.add(t);
                minx = Math.min(minx, t.x);
                miny = Math.min(miny, t.y);
                maxx = Math.max(maxx, t.x);
                maxy = Math.max(maxy, t.y);
            }
        }
        Coord begin = new Coord(minx, miny);
        Coord end = new Coord(maxx, maxy);
        boolean[][] data = new boolean[maxx - minx + 1][maxy - miny + 1];
        for (Coord t : tiles) {
            data[t.x - minx][t.y - miny] = true;
        }
        return new Mask(begin, end, data);
    }

    private static Coord snapCardinal(Coord2d v) {
        if (Math.abs(v.x) >= Math.abs(v.y)) {
            return new Coord(v.x >= 0 ? 1 : -1, 0);
        }
        return new Coord(0, v.y >= 0 ? 1 : -1);
    }

    Gob gob;
    public Coord begin;
    public Coord end;

    private boolean [][] data;

    public boolean[][] getData()
    {
        if(isDynamic)
        {
            calcData();
        }
        return data;
    }

    void calcData()
    {
        if (rect) {
            // Built tunnel gobs use an anchor one tile behind the vanilla support footprint;
            // placement ghosts already use the footprint's starting tile.
            int forwardShiftTiles = gob.id == -1 ? 0 : 1;
            Mask mask = computeRect(gob.rc, gob.a, widthTiles, lengthTiles, forwardShiftTiles);
            begin = mask.begin;
            // NMiningOverlay iterates [begin, end) so exclusive end lights the last tunnel tile.
            end = mask.end.add(1, 1);
            data = mask.data;
            return;
        }
        if(isTree)
        {
            TreeScale ts = gob.getattr(TreeScale.class);
            if(ts!=null)
            {
                this.r = (int) Math.round(baser * (ts.scale - 0.1) / 0.9);
            }
            else
            {
                this.r = baser;
                isTree = false;
                isDynamic = false;
            }
        }
        Coord a = gob.rc.sub(r, 0).div(MCache.tilesz).round();
        Coord b = gob.rc.sub(0, r).div(MCache.tilesz).round();
        Coord c = gob.rc.add(r, 0).div(MCache.tilesz).round();
        Coord d = gob.rc.add(0, r).div(MCache.tilesz).round();
        begin = new Coord(a.x,b.y);
        end = new Coord(c.x,d.y);

        data = new boolean[c.x-a.x+1][d.y-b.y+1];
        for(int i = 0; i<=c.x-a.x; i++)
        {
            for (int j = 0; j <= d.y-b.y; j++)
            {
                data[i][j] = (gob.rc.dist(new Coord2d(i+begin.x,j+begin.y).mul(MCache.tilesz).add(MCache.tilehsz))<r);
            }
        }
    }

    public NMiningSupport(Owner owner, int r)
    {
        super(owner, null);
        this.gob = (Gob)owner;
        this.r = r;
        calcData();
        isDynamic = gob.id == -1;
        TreeScale ts = gob.getattr(TreeScale.class);
        if(ts!=null)
        {
            this.baser = r;
            isDynamic = true;
            isTree = true;
        }
    }

    public NMiningSupport(Owner owner, int widthTiles, int lengthTiles)
    {
        super(owner, null);
        this.gob = (Gob)owner;
        this.rect = true;
        this.widthTiles = widthTiles;
        this.lengthTiles = lengthTiles;
        calcData();
        isDynamic = gob.id == -1;
    }

    int r;
    int baser;
    int widthTiles;
    int lengthTiles;
    boolean rect = false;
    boolean isTree = false;
    boolean isDynamic = false;
    NMiningOverlay mo = null;

    @Override
    public boolean tick(double dt)
    {
        if(mo == null)
        {
            mo = NMapView.getMiningOl();
            if(mo!=null)
                if(gob.id!=-1)
                {
                    mo.addMineSupp(gob.id);
                }
                else
                {
                    mo.addDummySupp(gob);
                }
        }
        return false;
    }

    @Override
    public void removed(RenderTree.Slot slot)
    {
        super.removed(slot);
        if(gob.id == -1)
        {
            mo.dummy = null;
        }
    }
}
