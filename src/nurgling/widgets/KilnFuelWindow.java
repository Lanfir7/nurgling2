package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.SListBox;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.NStyle;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.tools.KilnFuelCatalog;

import java.awt.Color;
import java.util.List;

/** Read-only kiln firing table: item, branch count, wiki times. */
public class KilnFuelWindow extends Window {
    private static final int WIDTH = UI.scale(460);
    private static final int HEIGHT = UI.scale(380);
    private static final int ROW_HEIGHT = UI.scale(20);
    private static final Color FUEL = new Color(255, 196, 92);
    private static final Color HEADER_BG = new Color(55, 65, 62, 220);
    private static final int[] COLUMNS = {8, 205, 268, 350};

    public KilnFuelWindow() {
        super(new Coord(WIDTH, HEIGHT), L10n.get("kiln_fuel.title"), true);
        add(new Header(new Coord(WIDTH - UI.scale(16), ROW_HEIGHT)), UI.scale(8, 4));
        add(new EntryList(new Coord(WIDTH - UI.scale(16), HEIGHT - UI.scale(36))), UI.scale(8, 26));
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close")) {
            ui.destroy(this);
        } else {
            super.wdgmsg(msg, args);
        }
    }

    public static void open() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null)
            return;
        for (Widget w = gui.child; w != null; w = w.next) {
            if (w instanceof KilnFuelWindow) {
                w.raise();
                return;
            }
        }
        KilnFuelWindow wnd = new KilnFuelWindow();
        Coord pos = gui.sz.sub(wnd.sz).div(2);
        gui.add(wnd, new Coord(Math.max(0, pos.x), Math.max(0, pos.y)));
        wnd.raise();
    }

    private static int columnX(int column) {
        return UI.scale(COLUMNS[column]);
    }

    private static void drawColumns(GOut g, Color fuelColor, String item, String fuel, String real, String ingame) {
        g.text(item, new Coord(columnX(0), UI.scale(3)));
        g.chcolor(fuelColor);
        g.text(fuel, new Coord(columnX(1), UI.scale(3)));
        g.chcolor();
        g.text(real, new Coord(columnX(2), UI.scale(3)));
        g.text(ingame, new Coord(columnX(3), UI.scale(3)));
    }

    private static class Header extends Widget {
        private Header(Coord sz) {
            super(sz);
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(HEADER_BG);
            g.frect(Coord.z, sz);
            g.chcolor();
            drawColumns(g, FUEL,
                    L10n.get("kiln_fuel.col.item"),
                    L10n.get("kiln_fuel.col.fuel"),
                    L10n.get("kiln_fuel.col.real"),
                    L10n.get("kiln_fuel.col.ingame"));
        }
    }

    private static class EntryList extends SListBox<KilnFuelCatalog.Entry, Widget> {
        private EntryList(Coord sz) {
            super(sz, ROW_HEIGHT);
        }

        @Override
        protected List<KilnFuelCatalog.Entry> items() {
            return KilnFuelCatalog.all();
        }

        @Override
        protected Widget makeitem(KilnFuelCatalog.Entry entry, int idx, Coord sz) {
            return new EntryRow(entry, idx, sz);
        }
    }

    private static class EntryRow extends Widget {
        private final KilnFuelCatalog.Entry entry;
        private final int idx;

        private EntryRow(KilnFuelCatalog.Entry entry, int idx, Coord sz) {
            super(sz);
            this.entry = entry;
            this.idx = idx;
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(idx % 2 == 0 ? NStyle.rowOdd : NStyle.rowEven);
            g.frect(Coord.z, sz);
            g.chcolor();
            drawColumns(g, FUEL, entry.item, Integer.toString(entry.fuelUnits),
                    entry.realTime, entry.inGameTime);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            return entry.item + " — " + entry.fuelUnits;
        }
    }
}
