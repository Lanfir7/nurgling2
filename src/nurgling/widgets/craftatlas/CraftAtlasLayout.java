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
        return compute(width, height, scale, false);
    }

    /** Viewport for content that scrolls below a fixed panel header. */
    public static Rect scrollBody(int width, int height, int headerHeight) {
        int top = Math.max(0, Math.min(height, headerHeight));
        return new Rect(0, top, Math.max(0, width), Math.max(0, height - top));
    }

    /** Places a frameless favorite control after the title while keeping it inside the details pane. */
    public static Rect favoriteAfterTitle(Rect details, int titleX, int titleWidth,
                                          int size, int gap, int top) {
        int safeSize = Math.max(1, Math.min(size, Math.max(1, details.w)));
        int preferred = details.x + Math.max(0, titleX) + Math.max(0, titleWidth) + Math.max(0, gap);
        int maximum = details.x + Math.max(0, details.w - safeSize - Math.max(0, gap));
        return new Rect(Math.max(details.x, Math.min(preferred, maximum)),
                details.y + Math.max(0, top), safeSize, safeSize);
    }

    public static CraftAtlasLayout compute(int width, int height, double scale, boolean metricTable) {
        return compute(width, height, scale, metricTable, -1);
    }

    public static CraftAtlasLayout compute(int width, int height, double scale, boolean metricTable,
                                           int requestedListWidth) {
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
        int remaining = Math.max(0, width - sidebarW - gap * 2);
        int preferredList = requestedListWidth > 0 ? requestedListWidth : scaled(metricTable ? 600 : 360, scale);
        int minimumDetails = scaled(320, scale);
        int minimumList = scaled(280, scale);
        int maximumList = Math.max(minimumList, remaining - minimumDetails);
        int listW = Math.max(minimumList, Math.min(preferredList, maximumList));
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

    /** Search field followed by a responsive row of header filter buttons. */
    public static Rect[] headerControls(int width, int searchX, int filterCount,
                                        int preferredFilterWidth, int minimumFilterWidth,
                                        int gap, int minimumSearchWidth) {
        int count = Math.max(0, filterCount);
        int safeGap = Math.max(0, gap);
        int available = Math.max(0, width - Math.max(0, searchX) - safeGap * count);
        int filterWidth = count == 0 ? 0 : Math.max(1, Math.min(preferredFilterWidth,
                Math.max(minimumFilterWidth, (available - minimumSearchWidth) / count)));
        if(count > 0 && filterWidth * count > available)
            filterWidth = Math.max(1, available / count);
        int searchWidth = Math.max(0, available - filterWidth * count);
        Rect[] result = new Rect[count + 1];
        result[0] = new Rect(Math.max(0, searchX), 0, searchWidth, 0);
        int x = result[0].x + searchWidth;
        for(int i = 0; i < count; i++) {
            x += safeGap;
            result[i + 1] = new Rect(x, 0, filterWidth, 0);
            x += filterWidth;
        }
        return result;
    }

    /** Left-to-right footer geometry: quantity, collect, open craft. */
    public static Rect[] footerControls(Rect footer, int quantityW,
                                        int collectW, int craftW, int gap, int margin) {
        int[] widths = {quantityW, collectW, craftW};
        int[] minimum = {Math.min(quantityW, 34), Math.min(collectW, 120), Math.min(craftW, 140)};
        int available = Math.max(3, footer.w - margin * 2 - gap * 2);
        int overflow = widths[0] + widths[1] + widths[2] - available;
        int[] shrinkOrder = {1, 2, 0};
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
        int quantityX = footer.x + margin;
        int collectX = quantityX + widths[0] + gap;
        int craftX = collectX + widths[1] + gap;
        return new Rect[] {
                new Rect(quantityX, y, widths[0], footer.h),
                new Rect(collectX, y, widths[1], footer.h),
                new Rect(craftX, y, widths[2], footer.h)
        };
    }
}
