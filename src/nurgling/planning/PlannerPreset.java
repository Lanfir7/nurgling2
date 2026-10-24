package nurgling.planning;

import haven.MCache;
import nurgling.NHitBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.function.Function;

/**
 * A piece of a base plan saved as a stamp that can be placed anywhere, in any world.
 *
 * Planning ghosts are stored against a particular grid ({@link PlanningGhost#gridId}), which ties
 * them to one spot. A preset keeps plain world-unit offsets from the top-left tile corner of the
 * group's box instead, grouped by the layers they came from. Because that corner is a tile corner
 * and the box is a whole number of tiles, moving or quarter-turning the stamp keeps every ghost on
 * the same sub-tile snap it was planned with.
 */
public class PlannerPreset
{
    public static final int FORMAT = 1;
    public static final int MAX_TILES = 512;
    public static final int MAX_GHOSTS = 4096;
    public static final int MAX_SDT = 8192;

    /** What placing makes: one new layer, or a new folder holding every layer. */
    public enum Kind { LAYER, FOLDER }

    public String name;
    public String description = "";
    public Kind kind = Kind.LAYER;
    /** Size of the box, in tiles, footprints included. */
    public int w, h;
    public final List<Layer> layers = new ArrayList<>();
    /** File name inside the presets folder. */
    public String file = null;

    public static class Layer
    {
        public String name;
        public final List<Ghost> ghosts = new ArrayList<>();

        public Layer(String name)
        {
            this.name = name;
        }
    }

    public static class Ghost
    {
        public final String res;
        /** Sprite data, as in {@link PlanningGhost#sdt}; may be null. */
        public final byte[] sdt;
        /** Offset from the box's top-left tile corner, in world units. */
        public double x, y;
        public double angle;

        public Ghost(String res, byte[] sdt, double x, double y, double angle)
        {
            this.res = res;
            this.sdt = sdt == null ? null : sdt.clone();
            this.x = x;
            this.y = y;
            this.angle = normalizedAngle(angle);
        }

        Ghost copy()
        {
            return new Ghost(res, sdt, x, y, angle);
        }
    }

    /** Thrown when a plan can't be saved; the message is shown to the player. */
    public static class CaptureException extends Exception
    {
        public CaptureException(String message)
        {
            super(message);
        }
    }

    private static double tile()
    {
        return MCache.tilesz.x;
    }

    /** Keep preview, saved plan and the game's fixed-point placement angle in one revolution. */
    static double normalizedAngle(double angle)
    {
        if (!Double.isFinite(angle)) return angle;
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }

    public int ghostCount()
    {
        int n = 0;
        for (Layer l : layers)
            n += l.ghosts.size();
        return n;
    }

    public static PlannerPreset fromJson(JSONObject obj)
    {
        if (obj.getInt("format") != FORMAT)
            throw new IllegalArgumentException("Unsupported planner preset format");
        PlannerPreset p = new PlannerPreset();
        p.name = obj.getString("name");
        p.description = obj.optString("description", "");
        String kind = obj.getString("kind");
        if (!"folder".equals(kind) && !"layer".equals(kind))
            throw new IllegalArgumentException("Unknown planner preset kind");
        p.kind = "folder".equals(kind) ? Kind.FOLDER : Kind.LAYER;
        JSONObject size = obj.getJSONObject("size");
        p.w = size.getInt("w");
        p.h = size.getInt("h");
        if (p.w <= 0 || p.h <= 0 || p.w > MAX_TILES || p.h > MAX_TILES)
            throw new IllegalArgumentException("Bad preset size");
        JSONArray jlayers = obj.getJSONArray("layers");
        for (int i = 0; i < jlayers.length(); i++) {
            JSONObject jl = jlayers.getJSONObject(i);
            Layer l = new Layer(jl.optString("name", "Layer"));
            JSONArray jghosts = jl.getJSONArray("ghosts");
            for (int j = 0; j < jghosts.length(); j++) {
                JSONObject jg = jghosts.getJSONObject(j);
                String enc = jg.optString("sdt", "");
                byte[] sdt = enc.isEmpty() ? null : Base64.getDecoder().decode(enc);
                l.ghosts.add(new Ghost(jg.getString("res"), sdt, jg.getDouble("x"), jg.getDouble("y"), jg.optDouble("angle", 0)));
            }
            if (!l.ghosts.isEmpty())
                p.layers.add(l);
        }
        p.validate();
        return p;
    }

    public void validate()
    {
        if (name == null || name.trim().isEmpty() || name.length() > 120 || description == null
                || description.length() > 8192 || kind == null || w <= 0 || h <= 0
                || w > MAX_TILES || h > MAX_TILES || layers.isEmpty() || layers.size() > 256)
            throw new IllegalArgumentException("Invalid planner preset");
        int count = 0;
        for (Layer layer : layers) {
            if (layer == null || layer.name == null || layer.name.length() > 120 || layer.ghosts.isEmpty())
                throw new IllegalArgumentException("Invalid planner preset layer");
            for (Ghost ghost : layer.ghosts) {
                if (++count > MAX_GHOSTS || ghost == null || ghost.res == null || ghost.res.isEmpty()
                        || ghost.res.length() > 512 || !Double.isFinite(ghost.x) || !Double.isFinite(ghost.y)
                        || !Double.isFinite(ghost.angle)
                        || Math.abs(ghost.x) > MAX_TILES * tile() * 2
                        || Math.abs(ghost.y) > MAX_TILES * tile() * 2
                        || ghost.x < -tile() || ghost.y < -tile()
                        || ghost.x > w * tile() + tile() || ghost.y > h * tile() + tile()
                        || (ghost.sdt != null && ghost.sdt.length > MAX_SDT))
                    throw new IllegalArgumentException("Invalid planner preset object");
                ghost.angle = normalizedAngle(ghost.angle);
            }
        }
    }

    public JSONObject toJson()
    {
        validate();
        JSONObject res = new JSONObject();
        res.put("format", FORMAT);
        res.put("kind", (kind == Kind.FOLDER) ? "folder" : "layer");
        res.put("name", name);
        if (!description.isEmpty())
            res.put("description", description);
        JSONObject size = new JSONObject();
        size.put("w", w);
        size.put("h", h);
        res.put("size", size);
        JSONArray jlayers = new JSONArray();
        for (Layer l : layers) {
            JSONObject jl = new JSONObject();
            jl.put("name", l.name);
            JSONArray jghosts = new JSONArray();
            for (Ghost g : l.ghosts) {
                JSONObject jg = new JSONObject();
                jg.put("res", g.res);
                if (g.sdt != null && g.sdt.length > 0)
                    jg.put("sdt", Base64.getEncoder().encodeToString(g.sdt));
                jg.put("x", g.x);
                jg.put("y", g.y);
                jg.put("angle", g.angle);
                jghosts.put(jg);
            }
            jl.put("ghosts", jghosts);
            jlayers.put(jl);
        }
        res.put("layers", jlayers);
        return res;
    }

    /**
     * Moves the ghosts so the box starts at (0,0) on a tile corner and sets the box size.
     * {@code extent} gives a ghost's world bounds {minx, miny, maxx, maxy} where it stands now.
     */
    void normalize(Function<Ghost, double[]> extent)
    {
        double t = tile();
        double minx = Double.MAX_VALUE, miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE, maxy = -Double.MAX_VALUE;
        for (Layer l : layers) {
            for (Ghost g : l.ghosts) {
                double[] b = extent.apply(g);
                if (b == null || b.length != 4 || !Double.isFinite(b[0]) || !Double.isFinite(b[1])
                        || !Double.isFinite(b[2]) || !Double.isFinite(b[3]) || b[2] < b[0] || b[3] < b[1])
                    throw new IllegalArgumentException("Invalid planner preset footprint");
                minx = Math.min(minx, b[0]);
                miny = Math.min(miny, b[1]);
                maxx = Math.max(maxx, b[2]);
                maxy = Math.max(maxy, b[3]);
            }
        }
        double ox = Math.floor(minx / t) * t;
        double oy = Math.floor(miny / t) * t;
        for (Layer l : layers) {
            for (Ghost g : l.ghosts) {
                g.x -= ox;
                g.y -= oy;
            }
        }
        w = Math.max(1, (int) Math.ceil((maxx - ox) / t - 1e-9));
        h = Math.max(1, (int) Math.ceil((maxy - oy) / t - 1e-9));
        validate();
    }

    /** World bounds of a ghost: its hitbox turned by its angle, or the tile around it if none is known. */
    public static double[] footprint(Ghost g)
    {
        return extent(NHitBox.findCustom(g.res), g.x, g.y, g.angle);
    }

    /** World bounds of hitbox {@code hb} standing at ({@code x}, {@code y}) turned by {@code angle}; a tile if null. */
    static double[] extent(NHitBox hb, double px, double py, double angle)
    {
        if (hb == null) {
            double hs = tile() / 2;
            return new double[] {px - hs, py - hs, px + hs, py + hs};
        }
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double[][] corners = {{hb.begin.x, hb.begin.y}, {hb.end.x, hb.begin.y}, {hb.end.x, hb.end.y}, {hb.begin.x, hb.end.y}};
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] c : corners) {
            double x = px + c[0] * cos - c[1] * sin;
            double y = py + c[0] * sin + c[1] * cos;
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.max(b[2], x);
            b[3] = Math.max(b[3], y);
        }
        return b;
    }

    /**
     * A copy turned {@code turns} quarter turns clockwise, then mirrored left to right if asked.
     * Only positions and facings mirror; the game has no mirrored models.
     */
    public PlannerPreset transformed(int turns, boolean mirror)
    {
        PlannerPreset p = new PlannerPreset();
        p.name = name;
        p.description = description;
        p.kind = kind;
        p.file = file;
        int t = Math.floorMod(turns, 4);
        p.w = ((t & 1) == 0) ? w : h;
        p.h = ((t & 1) == 0) ? h : w;
        for (Layer src : layers) {
            Layer l = new Layer(src.name);
            for (Ghost sg : src.ghosts) {
                Ghost g = sg.copy();
                double cw = w * tile(), ch = h * tile();
                for (int i = 0; i < t; i++) {
                    double nx = ch - g.y;
                    g.y = g.x;
                    g.x = nx;
                    g.angle += Math.PI / 2;
                    double tmp = cw;
                    cw = ch;
                    ch = tmp;
                }
                if (mirror) {
                    g.x = cw - g.x;
                    g.angle = Math.PI - g.angle;
                }
                g.angle = normalizedAngle(g.angle);
                l.ghosts.add(g);
            }
            p.layers.add(l);
        }
        return p;
    }
}
