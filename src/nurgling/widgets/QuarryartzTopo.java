package nurgling.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interpolated Quarryartz quality for one mine floor.
 * Empty cells stay transparent: no sample in range, or the tile is not dug cave.
 */
public final class QuarryartzTopo {
    public static final int RADIUS = 8;
    static final int PEAK_RADIUS = 6;
    static final int PEAK_MERGE = 12;
    /** Absolute quality stops: red, orange, yellow, green, cyan. */
    private static final double[] STOPS = {160, 200, 230, 260, 300};
    private static final int[][] STOP_RGB = {
            {196, 42, 36},
            {220, 120, 32},
            {230, 196, 48},
            {64, 180, 72},
            {36, 196, 196}
    };

    public interface Ground {
        /** 1 = dug cave to paint, anything else stays transparent. */
        int at(int x, int y);
    }

    public static final class Result {
        public final int x0, y0, width, height, stride;
        public final int[] argb;
        /** NaN where the cell is not painted. Kept so a rebuild can be checked without reading pixels. */
        public final double[] quality;
        public final float[] lines;
        public final int[] lineQ;
        public final float[] labelX, labelY;
        public final int[] labelQ;
        public final int[] peakX, peakY, peakQ;
        public final int[] sampleX, sampleY;

        Result(int x0, int y0, int width, int height, int stride, int[] argb, double[] quality, float[] lines, int[] lineQ,
               float[] labelX, float[] labelY, int[] labelQ,
               int[] peakX, int[] peakY, int[] peakQ, int[] sampleX, int[] sampleY) {
            this.x0 = x0;
            this.y0 = y0;
            this.width = width;
            this.height = height;
            this.stride = stride;
            this.argb = argb;
            this.quality = quality;
            this.lines = lines;
            this.lineQ = lineQ;
            this.labelX = labelX;
            this.labelY = labelY;
            this.labelQ = labelQ;
            this.peakX = peakX;
            this.peakY = peakY;
            this.peakQ = peakQ;
            this.sampleX = sampleX;
            this.sampleY = sampleY;
        }

        public boolean empty() {
            return width <= 0 || height <= 0;
        }
    }

    private QuarryartzTopo() {}

    public static int color(double q, int alpha) {
        double t = q;
        int r, g, b;
        if (t <= STOPS[0]) {
            r = STOP_RGB[0][0]; g = STOP_RGB[0][1]; b = STOP_RGB[0][2];
        } else if (t >= STOPS[STOPS.length - 1]) {
            int last = STOP_RGB.length - 1;
            r = STOP_RGB[last][0]; g = STOP_RGB[last][1]; b = STOP_RGB[last][2];
        } else {
            int i = 0;
            while (i < STOPS.length - 2 && t > STOPS[i + 1])
                i++;
            float u = (float) ((t - STOPS[i]) / (STOPS[i + 1] - STOPS[i]));
            r = lerp(STOP_RGB[i][0], STOP_RGB[i + 1][0], u);
            g = lerp(STOP_RGB[i][1], STOP_RGB[i + 1][1], u);
            b = lerp(STOP_RGB[i][2], STOP_RGB[i + 1][2], u);
        }
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Mined floor (gfx/tiles/mine), cave floors, and the wall around them. Void stays unpainted. */
    public static boolean excavatedName(String name) {
        if (name == null)
            return false;
        return name.startsWith("gfx/tiles/mine")
                || name.startsWith("gfx/tiles/deepcave")
                || name.startsWith("gfx/tiles/deeptangle")
                || name.startsWith("gfx/tiles/cave")
                || name.startsWith("gfx/tiles/rocks");
    }

    public static Result build(int[] x, int[] y, double[] q, Ground ground, int step, int alpha) {
        int n = 0;
        for (int i = 0; i < x.length; i++) {
            if (Double.isFinite(q[i]) && q[i] > 0)
                n++;
        }
        int[] sx = new int[n];
        int[] sy = new int[n];
        double[] sq = new double[n];
        int w = 0;
        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(q[i]) || q[i] <= 0)
                continue;
            sx[w] = x[i];
            sy[w] = y[i];
            sq[w] = q[i];
            w++;
        }
        if (n == 0)
            return new Result(0, 0, 0, 0, 1, new int[0], new double[0], new float[0], new int[0],
                    new float[0], new float[0], new int[0], new int[0], new int[0], new int[0], sx, sy);

        int minX = sx[0], maxX = sx[0], minY = sy[0], maxY = sy[0];
        double minQ = sq[0], maxQ = sq[0];
        for (int i = 0; i < n; i++) {
            if (sx[i] < minX) minX = sx[i];
            if (sx[i] > maxX) maxX = sx[i];
            if (sy[i] < minY) minY = sy[i];
            if (sy[i] > maxY) maxY = sy[i];
            if (sq[i] < minQ) minQ = sq[i];
            if (sq[i] > maxQ) maxQ = sq[i];
        }
        int x0 = minX - RADIUS;
        int y0 = minY - RADIUS;
        int width = maxX - minX + 1 + RADIUS * 2;
        int height = maxY - minY + 1 + RADIUS * 2;
        int stride = (width * (long) height > 600_000L) ? 2 : 1;
        int gw = (width + stride - 1) / stride;
        int gh = (height + stride - 1) / stride;

        Map<Long, List<Integer>> cells = spatial(sx, sy, n);
        double[] field = new double[gw * gh];
        float[] cover = new float[gw * gh];
        for (int i = 0; i < field.length; i++)
            field[i] = Double.NaN;
        for (int gy = 0; gy < gh; gy++) {
            int ty = y0 + gy * stride;
            for (int gx = 0; gx < gw; gx++) {
                int tx = x0 + gx * stride;
                if (ground != null && ground.at(tx, ty) != 1)
                    continue;
                double[] idw = idw(tx, ty, sx, sy, sq, cells);
                if (idw == null)
                    continue;
                int idx = gx + gy * gw;
                field[idx] = idw[0];
                float c = (float) (1.0 - idw[1] / RADIUS);
                if (c < 0f) c = 0f;
                cover[idx] = c < 0.35f ? c / 0.35f : 1f;
            }
        }
        int[] argb = new int[gw * gh];
        for (int i = 0; i < field.length; i++) {
            if (Double.isNaN(field[i]))
                continue;
            int a = (int) Math.round(alpha * cover[i]);
            argb[i] = color(field[i], a);
        }

        int useStep = step < 10 ? 10 : (step > 20 ? 20 : step);
        List<Float> lineBuf = new ArrayList<>();
        List<Integer> lineLevel = new ArrayList<>();
        if (maxQ - minQ >= useStep) {
            int first = (int) (Math.ceil(minQ / useStep) * useStep);
            int last = (int) (Math.floor(maxQ / useStep) * useStep);
            for (int level = first; level <= last; level += useStep)
                march(field, gw, gh, x0, y0, stride, level, lineBuf, lineLevel);
        }
        float[] lines = new float[lineBuf.size()];
        for (int i = 0; i < lines.length; i++)
            lines[i] = lineBuf.get(i);
        int[] lineQ = new int[lineLevel.size()];
        for (int i = 0; i < lineQ.length; i++)
            lineQ[i] = lineLevel.get(i);

        float[][] labels = thinLabels(lines, lineQ, 22);
        int[][] peaks = peaks(sx, sy, sq);

        return new Result(x0, y0, gw, gh, stride, argb, field, lines, lineQ,
                labels[0], labels[1], toInt(labels[2]),
                peaks[0], peaks[1], peaks[2], sx, sy);
    }

    /** @return {quality, nearest distance} or null when nothing is in range. */
    private static double[] idw(int tx, int ty, int[] sx, int[] sy, double[] sq, Map<Long, List<Integer>> cells) {
        double sumW = 0, sumWQ = 0, nearest = RADIUS + 1, exact = Double.NaN;
        int cx = Math.floorDiv(tx, RADIUS);
        int cy = Math.floorDiv(ty, RADIUS);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                List<Integer> bucket = cells.get(cellKey(cx + ox, cy + oy));
                if (bucket == null)
                    continue;
                for (int j : bucket) {
                    double dx = sx[j] - tx;
                    double dy = sy[j] - ty;
                    double dist = Math.hypot(dx, dy);
                    if (dist > RADIUS)
                        continue;
                    if (dist < nearest)
                        nearest = dist;
                    if (dist < 0.5) {
                        if (Double.isNaN(exact) || sq[j] > exact)
                            exact = sq[j];
                        continue;
                    }
                    double ww = 1.0 / (dist * dist);
                    sumW += ww;
                    sumWQ += ww * sq[j];
                }
            }
        }
        if (nearest > RADIUS)
            return null;
        double value = !Double.isNaN(exact) ? exact : (sumW > 0 ? sumWQ / sumW : Double.NaN);
        if (Double.isNaN(value))
            return null;
        return new double[] {value, nearest};
    }

    private static void march(double[] field, int gw, int gh, int x0, int y0, int stride, int level,
                              List<Float> lines, List<Integer> levels) {
        for (int gy = 0; gy < gh - 1; gy++) {
            for (int gx = 0; gx < gw - 1; gx++) {
                double v00 = field[gx + gy * gw];
                double v10 = field[gx + 1 + gy * gw];
                double v11 = field[gx + 1 + (gy + 1) * gw];
                double v01 = field[gx + (gy + 1) * gw];
                if (Double.isNaN(v00) || Double.isNaN(v10) || Double.isNaN(v11) || Double.isNaN(v01))
                    continue;
                int mask = (v00 >= level ? 1 : 0) | (v10 >= level ? 2 : 0)
                        | (v11 >= level ? 4 : 0) | (v01 >= level ? 8 : 0);
                if (mask == 0 || mask == 15)
                    continue;
                double x = x0 + gx * stride;
                double y = y0 + gy * stride;
                double tb = cross(v00, v10, level);
                double tr = cross(v10, v11, level);
                double tt = cross(v01, v11, level);
                double tl = cross(v00, v01, level);
                int[] pairs = pairs(mask);
                for (int p = 0; p < pairs.length; p += 2) {
                    double[] a = edgePoint(pairs[p], x, y, stride, tb, tr, tt, tl);
                    double[] b = edgePoint(pairs[p + 1], x, y, stride, tb, tr, tt, tl);
                    lines.add((float) a[0]);
                    lines.add((float) a[1]);
                    lines.add((float) b[0]);
                    lines.add((float) b[1]);
                    levels.add(level);
                }
            }
        }
    }

    private static int[] pairs(int mask) {
        switch (mask) {
            case 1: return new int[] {0, 3};
            case 2: return new int[] {0, 1};
            case 3: return new int[] {3, 1};
            case 4: return new int[] {1, 2};
            case 5: return new int[] {0, 3, 1, 2};
            case 6: return new int[] {0, 2};
            case 7: return new int[] {3, 2};
            case 8: return new int[] {2, 3};
            case 9: return new int[] {0, 2};
            case 10: return new int[] {0, 1, 2, 3};
            case 11: return new int[] {1, 2};
            case 12: return new int[] {3, 1};
            case 13: return new int[] {0, 1};
            case 14: return new int[] {0, 3};
            default: return new int[0];
        }
    }

    private static double[] edgePoint(int edge, double x, double y, int stride,
                                      double tb, double tr, double tt, double tl) {
        switch (edge) {
            case 0: return new double[] {x + tb * stride, y};
            case 1: return new double[] {x + stride, y + tr * stride};
            case 2: return new double[] {x + tt * stride, y + stride};
            default: return new double[] {x, y + tl * stride};
        }
    }

    private static double cross(double a, double b, double level) {
        double d = b - a;
        if (Math.abs(d) < 1e-6)
            return 0.5;
        double t = (level - a) / d;
        if (t < 0) return 0;
        if (t > 1) return 1;
        return t;
    }

    private static float[][] thinLabels(float[] lines, int[] lineQ, int gap) {
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        List<Float> qs = new ArrayList<>();
        boolean[] used = new boolean[lineQ.length];
        for (int i = 0; i < lineQ.length; i++) {
            if (used[i])
                continue;
            float x = (lines[i * 4] + lines[i * 4 + 2]) * 0.5f;
            float y = (lines[i * 4 + 1] + lines[i * 4 + 3]) * 0.5f;
            boolean near = false;
            for (int k = 0; k < xs.size(); k++) {
                if (qs.get(k).intValue() != lineQ[i])
                    continue;
                if (Math.hypot(xs.get(k) - x, ys.get(k) - y) < gap) {
                    near = true;
                    break;
                }
            }
            used[i] = true;
            if (near)
                continue;
            xs.add(x);
            ys.add(y);
            qs.add((float) lineQ[i]);
        }
        float[] lx = new float[xs.size()];
        float[] ly = new float[ys.size()];
        float[] lq = new float[qs.size()];
        for (int i = 0; i < lx.length; i++) {
            lx[i] = xs.get(i);
            ly[i] = ys.get(i);
            lq[i] = qs.get(i);
        }
        return new float[][] {lx, ly, lq};
    }

    private static int[] toInt(float[] src) {
        int[] out = new int[src.length];
        for (int i = 0; i < src.length; i++)
            out[i] = Math.round(src[i]);
        return out;
    }

    private static int[][] peaks(int[] x, int[] y, double[] q) {
        int n = x.length;
        boolean[] peak = new boolean[n];
        for (int i = 0; i < n; i++) {
            int neighbors = 0;
            boolean higher = false, lower = false;
            for (int j = 0; j < n; j++) {
                if (i == j)
                    continue;
                double dist = Math.hypot(x[j] - x[i], y[j] - y[i]);
                if (dist <= 0 || dist > PEAK_RADIUS)
                    continue;
                neighbors++;
                if (q[j] > q[i]) higher = true;
                else if (q[j] < q[i]) lower = true;
            }
            peak[i] = neighbors >= 2 && !higher && lower;
        }
        boolean[] dropped = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!peak[i] || dropped[i])
                continue;
            for (int j = i + 1; j < n; j++) {
                if (!peak[j] || dropped[j])
                    continue;
                if (Math.hypot(x[j] - x[i], y[j] - y[i]) > PEAK_MERGE)
                    continue;
                if (q[j] > q[i] || (q[j] == q[i] && j > i))
                    dropped[i] = true;
                else
                    dropped[j] = true;
            }
        }
        int count = 0;
        for (int i = 0; i < n; i++)
            if (peak[i] && !dropped[i])
                count++;
        int[] px = new int[count];
        int[] py = new int[count];
        int[] pq = new int[count];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (!peak[i] || dropped[i])
                continue;
            px[k] = x[i];
            py[k] = y[i];
            pq[k] = (int) Math.round(q[i]);
            k++;
        }
        return new int[][] {px, py, pq};
    }

    private static Map<Long, List<Integer>> spatial(int[] x, int[] y, int n) {
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long key = cellKey(Math.floorDiv(x[i], RADIUS), Math.floorDiv(y[i], RADIUS));
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return cells;
    }

    private static long cellKey(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }

    private static int lerp(int a, int b, float u) {
        return a + Math.round((b - a) * u);
    }
}
