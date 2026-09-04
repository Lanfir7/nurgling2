package nurgling.widgets.craftatlas;

/** Deterministic responsive geometry kept separate from Haven widgets for testing. */
public final class CraftAtlasLayout {
    public static final class Rect {
        public final int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    public final Rect header, sidebar, list, details;
    public final boolean detailsAsPage;

    private CraftAtlasLayout(Rect header, Rect sidebar, Rect list, Rect details, boolean detailsAsPage) {
        this.header = header; this.sidebar = sidebar; this.list = list; this.details = details;
        this.detailsAsPage = detailsAsPage;
    }

    public static CraftAtlasLayout compute(int width, int height, double scale) {
        int headerH = scaled(56, scale), gap = scaled(8, scale), sidebarW = scaled(184, scale);
        int bodyY = headerH, bodyH = Math.max(0, height - headerH);
        boolean narrow = width < scaled(1000, scale);
        Rect header = new Rect(0, 0, width, headerH);
        if(narrow) {
            int listW = Math.max(0, width - sidebarW - gap);
            return new CraftAtlasLayout(header, new Rect(0, bodyY, sidebarW, bodyH),
                    new Rect(sidebarW + gap, bodyY, listW, bodyH), new Rect(0, bodyY, 0, bodyH), true);
        }
        int listW = scaled(330, scale);
        int detailsX = sidebarW + gap + listW + gap;
        return new CraftAtlasLayout(header, new Rect(0, bodyY, sidebarW, bodyH),
                new Rect(sidebarW + gap, bodyY, listW, bodyH),
                new Rect(detailsX, bodyY, Math.max(0, width - detailsX), bodyH), false);
    }

    private static int scaled(int value, double scale) { return (int)Math.round(value * scale); }

    /** Inclusive visible row range, clamped so the last row is always reachable. */
    public static int[] visibleRows(int scrollPixels, int viewportHeight, int rowHeight, int count) {
        if(count <= 0 || rowHeight <= 0) return new int[] {0, -1};
        int maxScroll = Math.max(0, count * rowHeight - viewportHeight);
        int scroll = Math.max(0, Math.min(scrollPixels, maxScroll));
        int first = Math.min(count - 1, scroll / rowHeight);
        int last = Math.min(count - 1, (scroll + Math.max(0, viewportHeight - 1)) / rowHeight);
        return new int[] {first, last};
    }
}
