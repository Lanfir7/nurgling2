package nurgling.widgets.nsettings;

import java.util.Arrays;

/** Immutable horizontal tab geometry with a selected-tab visibility guarantee. */
public final class HotkeyTabLayout {
    public static final class Rect {
        public final int x;
        public final int width;

        private Rect(int x, int width) {
            this.x = x;
            this.width = width;
        }

        public int right() { return x + width; }
        public int left() { return x; }
    }

    public static final class VisibleRange {
        public final int start;
        public final int end;
        public final int first;
        public final int last;
        public final int from;
        public final int to;
        public final int endExclusive;

        private VisibleRange(int start, int end) {
            this.start = start;
            this.end = end;
            this.first = start;
            this.last = end - 1;
            this.from = start;
            this.to = end;
            this.endExclusive = end;
        }

        public int start() { return start; }
        public int end() { return end; }
        public boolean contains(int index) { return index >= start && index < end; }
    }

    private final Rect[] rects;
    private final VisibleRange visibleRange;
    private final int contentWidth;
    private final int viewportWidth;
    private final int offset;

    private HotkeyTabLayout(Rect[] rects, VisibleRange visibleRange, int contentWidth,
                            int viewportWidth, int offset) {
        this.rects = rects;
        this.visibleRange = visibleRange;
        this.contentWidth = contentWidth;
        this.viewportWidth = viewportWidth;
        this.offset = offset;
    }

    public static HotkeyTabLayout calculate(int[] widths, int viewportWidth,
                                            int selectedIndex, int gap) {
        if(widths == null)
            throw new NullPointerException("widths");
        if(viewportWidth < 0 || gap < 0)
            throw new IllegalArgumentException("viewport and gap must be non-negative");
        int[] copy = Arrays.copyOf(widths, widths.length);
        int content = 0;
        for(int width : copy) {
            if(width < 0)
                throw new IllegalArgumentException("negative tab width");
            if(content > Integer.MAX_VALUE - width)
                throw new IllegalArgumentException("tab width overflow");
            content += width;
        }
        if(copy.length > 1) {
            int gaps = copy.length - 1;
            if(gap != 0 && gaps > (Integer.MAX_VALUE - content) / gap)
                throw new IllegalArgumentException("tab width overflow");
            content += gaps * gap;
        }
        int selected = copy.length == 0 ? -1 : Math.max(0, Math.min(selectedIndex, copy.length - 1));
        int rawSelectedLeft = 0;
        for(int i = 0; i < selected; i++)
            rawSelectedLeft += copy[i] + gap;
        int rawSelectedRight = selected < 0 ? 0 : rawSelectedLeft + copy[selected];
        int minOffset = Math.min(0, viewportWidth - content);
        int desired = 0;
        if(selected >= 0) {
            if(rawSelectedLeft < 0)
                desired = -rawSelectedLeft;
            if(rawSelectedRight + desired > viewportWidth)
                desired = viewportWidth - rawSelectedRight;
        }
        int offset = Math.max(minOffset, Math.min(0, desired));
        Rect[] rects = new Rect[copy.length];
        int x = offset;
        int start = copy.length;
        int end = 0;
        for(int i = 0; i < copy.length; i++) {
            rects[i] = new Rect(x, copy[i]);
            if(x >= 0 && x + copy[i] <= viewportWidth) {
                start = Math.min(start, i);
                end = i + 1;
            }
            x += copy[i] + gap;
        }
        if(start == copy.length)
            start = end = 0;
        return new HotkeyTabLayout(rects, new VisibleRange(start, end), content,
                viewportWidth, offset);
    }

    public Rect rect(int index) {
        if(index < 0 || index >= rects.length)
            throw new IndexOutOfBoundsException("tab index: " + index);
        return rects[index];
    }

    public VisibleRange visibleRange() { return visibleRange; }
    public int contentWidth() { return contentWidth; }
    public int viewportWidth() { return viewportWidth; }
    public int offset() { return offset; }
    public boolean canScrollLeft() { return offset < 0; }
    public boolean canScrollRight() { return offset > Math.min(0, viewportWidth - contentWidth); }
}
