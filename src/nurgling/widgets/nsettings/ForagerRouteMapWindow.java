package nurgling.widgets.nsettings;

import haven.Coord;
import haven.MapFile;
import haven.UI;
import haven.Window;
import nurgling.NWindowDeco;

/** Large, MapWnd-style resizable editor for the forager route map. */
public class ForagerRouteMapWindow extends Window {
    /** Same unscaled floor MapWnd uses outside compact mode. */
    public static final Coord MIN_INNER = Coord.of(350, 255);
    public static final Coord DEFAULT_INNER = Coord.of(700, 520);

    public final ForagerRouteMap map;
    public Runnable onClosed;

    public ForagerRouteMapWindow(MapFile file, String title) {
        this(file, title, UI.scale(DEFAULT_INNER));
    }

    public ForagerRouteMapWindow(MapFile file, String title, Coord inner) {
        super(clampInnerSize(inner, UI.scale(MIN_INNER)), title, true);
        map = add(new ForagerRouteMap(csz(), file));
        resize(csz());
    }

    public static Coord clampInnerSize(Coord requested) {
        return clampInnerSize(requested, MIN_INNER);
    }

    public static Coord clampInnerSize(Coord requested, Coord min) {
        if (requested == null)
            requested = Coord.z;
        if (min == null)
            min = Coord.z;
        return requested.max(min);
    }

    public static Coord mapSizeForInner(Coord inner) {
        return inner == null ? Coord.z : inner;
    }

    @Override
    protected Deco makedeco() {
        return new NWindowDeco(true).dragsize(true);
    }

    @Override
    public void resize(Coord sz) {
        sz = clampInnerSize(sz, UI.scale(MIN_INNER));
        super.resize(sz);
        if (map != null)
            map.resize(mapSizeForInner(csz()));
    }

    @Override
    public void reqclose() {
        reqdestroy();
    }

    @Override
    public void destroy() {
        Runnable cb = onClosed;
        onClosed = null;
        if (cb != null)
            cb.run();
        super.destroy();
    }
}
