package nurgling.widgets.nsettings;

import haven.Coord;
import haven.GOut;
import haven.Widget;

/**
 * Visibility contract for list rows and nested widgets such as TextEntry.
 * Matches Scrollport's skip-fully-outside-children rule, using a half-open
 * vertical range {@code [viewTop, viewBottom)}.
 */
final class ListChildClip {
    private ListChildClip() {
    }

    static boolean shouldDraw(int listHeight, int y, int height) {
        return overlaps(0, listHeight, y, height);
    }

    static boolean overlaps(int viewTop, int viewBottom, int y, int height) {
        return height > 0 && viewBottom > viewTop && y < viewBottom && y + height > viewTop;
    }

    static void drawVisibleChildren(Widget host, GOut g, boolean strict) {
        int clipTop = g.ul.y - g.tx.y;
        int clipBottom = g.br.y - g.tx.y;
        Widget next;
        for(Widget wdg = host.child; wdg != null; wdg = next) {
            next = wdg.next;
            if(!wdg.visible)
                continue;
            Coord cc = host.xlate(wdg.c, true);
            if(!overlaps(clipTop, clipBottom, cc.y, wdg.sz.y))
                continue;
            GOut g2 = strict ? g.reclip(cc, wdg.sz) : g.reclipl(cc, wdg.sz);
            wdg.draw(g2);
        }
    }
}
