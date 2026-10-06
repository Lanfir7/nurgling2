package nurgling.widgets;

import haven.Coord;
import nurgling.ExtraInvGroupTransfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Flow layout for same-type pouches inside one window. */
public final class MergedBagLayout {
    private MergedBagLayout() {}

    public static final class Box {
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        public Box(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    public static boolean shouldMerge(String name, int count) {
        return count >= 2 && ExtraInvGroupTransfer.isExternalBag(name);
    }

    public static List<Box> place(List<Coord> sizes, int gap, int maxWidth, int pad) {
        List<Box> out = new ArrayList<Box>();
        if (sizes == null || sizes.isEmpty())
            return out;
        int limit = Math.max(1, maxWidth);
        int inset = Math.max(0, pad);
        int spacing = Math.max(0, gap);
        int x = inset;
        int y = inset;
        int rowH = 0;
        for (Coord sz : sizes) {
            int w = sz == null ? 1 : Math.max(1, sz.x);
            int h = sz == null ? 1 : Math.max(1, sz.y);
            if (x > inset && x + w + inset > limit) {
                x = inset;
                y += rowH + spacing;
                rowH = 0;
            }
            out.add(new Box(x, y, w, h));
            x += w + spacing;
            rowH = Math.max(rowH, h);
        }
        return out;
    }

    public static Coord bounds(List<Box> boxes, int pad) {
        int inset = Math.max(0, pad);
        int w = inset;
        int h = inset;
        if (boxes != null) {
            for (Box box : boxes) {
                w = Math.max(w, box.x + box.w + inset);
                h = Math.max(h, box.y + box.h + inset);
            }
        }
        return Coord.of(Math.max(1, w), Math.max(1, h));
    }

    /** Divider segments that sit in the gap between pouch frames. */
    public static List<Box> seams(List<Box> boxes, int gap, int thickness) {
        List<Box> seams = new ArrayList<Box>();
        if (boxes == null || boxes.size() < 2)
            return seams;
        int t = Math.max(1, thickness);
        for (Box a : boxes) {
            Box right = null;
            for (Box b : boxes) {
                if (b.y == a.y && b.x > a.x && (right == null || b.x < right.x))
                    right = b;
            }
            if (right == null)
                continue;
            int gapW = right.x - (a.x + a.w);
            if (gapW <= 0)
                continue;
            int x = a.x + a.w + Math.max(0, (gapW - t) / 2);
            int h = Math.max(a.h, right.h);
            seams.add(new Box(x, a.y, t, h));
        }
        List<Integer> rows = new ArrayList<Integer>();
        for (Box box : boxes) {
            if (!rows.contains(box.y))
                rows.add(box.y);
        }
        Collections.sort(rows);
        for (int i = 0; i < rows.size() - 1; i++) {
            int row = rows.get(i);
            int next = rows.get(i + 1);
            int bottom = 0;
            int left = Integer.MAX_VALUE;
            int rightEdge = 0;
            for (Box box : boxes) {
                if (box.y != row && box.y != next)
                    continue;
                left = Math.min(left, box.x);
                rightEdge = Math.max(rightEdge, box.x + box.w);
                if (box.y == row)
                    bottom = Math.max(bottom, box.y + box.h);
            }
            int gapH = next - bottom;
            if (gapH <= 0 || rightEdge <= left)
                continue;
            int y = bottom + Math.max(0, (gapH - t) / 2);
            seams.add(new Box(left, y, rightEdge - left, t));
        }
        return seams;
    }
}
