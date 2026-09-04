package nurgling.widgets.craftatlas;

/** Deterministic responsive geometry kept separate from Haven widgets for testing. */
public final class CraftAtlasLayout {
    public static final class Rect {
        public final int x, y, w, h;
        Rect(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    public final Rect header, sidebar, list, details, footer;
    public final boolean detailsAsPage;

    private CraftAtlasLayout(Rect header, Rect sidebar, Rect list, Rect details, Rect footer, boolean detailsAsPage) {
        this.header = header; this.sidebar = sidebar; this.list = list; this.details = details;
        this.footer = footer;
        this.detailsAsPage = detailsAsPage;
    }

    public static CraftAtlasLayout compute(int width, int height, double scale) {
        int headerH = scaled(56, scale), footerH = scaled(56, scale), gap = scaled(8, scale);
        int sidebarW = scaled(184, scale);
        int bodyY = headerH, bodyH = Math.max(0, height - headerH);
        boolean narrow = width < scaled(1000, scale);
        Rect header = new Rect(0, 0, width, headerH);
        if(narrow) {
            int listW = Math.max(0, width - sidebarW - gap);
            return new CraftAtlasLayout(header, new Rect(0, bodyY, sidebarW, bodyH),
                    new Rect(sidebarW + gap, bodyY, listW, bodyH),
                    new Rect(0, bodyY, 0, Math.max(0, bodyH - footerH - gap)),
                    new Rect(0, Math.max(bodyY, height - footerH), width, footerH), true);
        }
        int listW = scaled(360, scale);
        int detailsX = sidebarW + gap + listW + gap;
        int detailsW = Math.max(0, width - detailsX);
        return new CraftAtlasLayout(header, new Rect(0, bodyY, sidebarW, bodyH),
                new Rect(sidebarW + gap, bodyY, listW, bodyH),
                new Rect(detailsX, bodyY, detailsW, Math.max(0, bodyH - footerH - gap)),
                new Rect(detailsX, Math.max(bodyY, height - footerH), detailsW, footerH), false);
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

    /** Left-to-right footer geometry: favorite, quantity, collect, open craft. */
    public static Rect[] footerControls(Rect footer, int favoriteW, int quantityW,
                                        int collectW, int craftW, int gap, int margin) {
        int[] widths = {favoriteW, quantityW, collectW, craftW};
        int[] minimum = {Math.min(favoriteW, 28), Math.min(quantityW, 34),
                Math.min(collectW, 120), Math.min(craftW, 140)};
        int available = Math.max(4, footer.w - margin * 2 - gap * 3);
        int overflow = widths[0] + widths[1] + widths[2] + widths[3] - available;
        int[] shrinkOrder = {2, 3, 1, 0};
        for(int index : shrinkOrder) {
            int reduce = Math.min(Math.max(0, overflow), widths[index] - minimum[index]);
            widths[index] -= reduce;
            overflow -= reduce;
        }
        for(int index : shrinkOrder) {
            int reduce = Math.min(Math.max(0, overflow), widths[index] - 1);
            widths[index] -= reduce;
            overflow -= reduce;
        }
        int y = footer.y;
        int favoriteX = footer.x + margin;
        int quantityX = favoriteX + widths[0] + gap;
        int collectX = quantityX + widths[1] + gap;
        int craftX = collectX + widths[2] + gap;
        return new Rect[] {
                new Rect(favoriteX, y, widths[0], footer.h),
                new Rect(quantityX, y, widths[1], footer.h),
                new Rect(collectX, y, widths[2], footer.h),
                new Rect(craftX, y, widths[3], footer.h)
        };
    }
}
