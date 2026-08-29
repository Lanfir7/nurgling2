package nurgling.widgets;

import haven.GameUI;
import haven.Widget;

/**
 * Puts a map-toolbar search window in front of siblings, including after
 * {@code Window.mousedown} raises the map at the end of the current click.
 */
public final class MapSearchFront {
    private MapSearchFront() {}

    public static void showInFront(Widget wnd) {
        if(wnd == null)
            return;
        wnd.show();
        bringToFront(wnd);
        scheduleDeferredFront(wnd);
    }

    static void bringToFront(Widget wnd) {
        wnd.raise();
        focus(wnd);
    }

    static void focus(Widget wnd) {
        for(Widget w = wnd.parent; w != null; w = w.parent) {
            if(w instanceof GameUI) {
                w.setfocus(wnd);
                return;
            }
        }
        Widget p = wnd.parent;
        if(p != null && (p.focusctl || p.parent != null))
            p.setfocus(wnd);
    }

    static void scheduleDeferredFront(Widget wnd) {
        wnd.new Anim() {
            @Override
            public boolean tick(double dt) {
                if(wnd.visible() && wnd.parent != null)
                    bringToFront(wnd);
                return true;
            }
        };
    }
}
