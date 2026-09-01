package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.Indir;
import haven.Label;
import haven.Loading;
import haven.Resource;
import haven.SListBox;
import haven.Text;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.tools.Forageables;

import java.awt.Color;
import java.util.List;
import java.util.function.LongSupplier;

/** Local foraging reference colored by the character's current PER x EXP score. */
public class ForageHelperWindow extends Window {
    private static final int WIDTH = UI.scale(790);
    private static final int HEIGHT = UI.scale(520);
    private static final int ROW_HEIGHT = UI.scale(22);
    private static final Color GREEN = new Color(45, 105, 55, 145);
    private static final Color YELLOW = new Color(145, 112, 35, 145);
    private static final Color RED = new Color(125, 45, 45, 145);

    private final LongSupplier scoreSupplier;
    private final Runnable onClose;
    private final Label scoreLabel;
    private long displayedScore = Long.MIN_VALUE;
    private boolean closing;

    public ForageHelperWindow(LongSupplier scoreSupplier, Runnable onClose) {
        super(new Coord(WIDTH, HEIGHT), "Forage Helper", true);
        this.scoreSupplier = scoreSupplier;
        this.onClose = onClose;

        scoreLabel = add(new Label(""), UI.scale(8, 8));
        add(new Header(new Coord(WIDTH - UI.scale(16), ROW_HEIGHT)), UI.scale(8, 34));
        add(new EntryList(new Coord(WIDTH - UI.scale(16), HEIGHT - UI.scale(66))), UI.scale(8, 56));
        updateScore();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        updateScore();
    }

    private long score() {
        return Math.max(0, scoreSupplier.getAsLong());
    }

    private void updateScore() {
        long score = score();
        if(score != displayedScore) {
            displayedScore = score;
            scoreLabel.settext(String.format("PER × EXP: %,d", score));
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(msg.equals("close")) {
            closeWindow();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    private void closeWindow() {
        if(closing)
            return;
        closing = true;
        if(onClose != null)
            onClose.run();
        reqdestroy();
    }

    private static void drawColumns(GOut g, String... values) {
        for(int i = 0; i < values.length && i < ForageHelperTableLayout.columnCount(); i++)
            g.text(values[i], new Coord(ForageHelperTableLayout.columnX(i), UI.scale(3)));
    }

    private static class Header extends Widget {
        private Header(Coord sz) {
            super(sz);
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(55, 65, 62, 220);
            g.frect(Coord.z, sz);
            g.chcolor();
            drawColumns(g, ForageHelperTableLayout.columnHeaders().toArray(new String[0]));
        }
    }

    private class EntryList extends SListBox<Forageables.Entry, Widget> {
        private EntryList(Coord sz) {
            super(sz, ROW_HEIGHT);
        }

        @Override
        protected List<Forageables.Entry> items() {
            return Forageables.all();
        }

        @Override
        protected Widget makeitem(Forageables.Entry entry, int idx, Coord sz) {
            return new EntryRow(entry, sz);
        }
    }

    private class EntryRow extends Widget {
        private final Forageables.Entry entry;
        private final Indir<Resource> icon;

        private EntryRow(Forageables.Entry entry, Coord sz) {
            super(sz);
            this.entry = entry;
            this.icon = entry.icon.isEmpty() ? null : Resource.remote().load(entry.icon);
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(rowColor(Forageables.visibility(score(), entry.first, entry.all)));
            g.frect(Coord.z, sz);
            g.chcolor();
            drawIcon(g);
            drawColumn(g, 0, shorten(entry.name, 21));
            drawColumn(g, 1, Integer.toString(entry.first));
            drawColumn(g, 2, Integer.toString(entry.base));
            drawColumn(g, 3, Integer.toString(entry.all));
            drawSeason(g, 4, entry.spring);
            drawSeason(g, 5, entry.summer);
            drawSeason(g, 6, entry.autumn);
            drawSeason(g, 7, entry.winter);
            drawColumn(g, 8, shorten(entry.terrainText(), 27));
        }

        private void drawIcon(GOut g) {
            if(icon == null)
                return;
            try {
                g.image(icon.get().flayer(Resource.imgc).tex(), UI.scale(4, 1), UI.scale(20, 20));
            } catch(Loading ignored) {
            }
        }

        private void drawColumn(GOut g, int column, String value) {
            g.text(value, new Coord(ForageHelperTableLayout.columnX(column), UI.scale(3)));
        }

        private void drawSeason(GOut g, int column, String value) {
            Text.Line symbol = ForageSeasonPresentation.render(value);
            g.image(symbol.tex(), new Coord(ForageHelperTableLayout.columnX(column), UI.scale(3)));
            symbol.dispose();
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            StringBuilder tip = new StringBuilder(entry.name);
            if(!entry.terrains.isEmpty())
                tip.append("\nTerrain: ").append(entry.terrainText());
            return tip.toString();
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && ForageHelperTableLayout.isTerrainColumn(ev.c.x, sz.x) && !entry.terrains.isEmpty()) {
                MapToolsWindow.openTerrainSearch(entry.terrains);
                return true;
            }
            return super.mousedown(ev);
        }
    }

    private static Color rowColor(Forageables.Visibility visibility) {
        switch(visibility) {
            case GREEN:
                return GREEN;
            case YELLOW:
                return YELLOW;
            default:
                return RED;
        }
    }

    private static String shorten(String value, int maxLength) {
        if(value == null || value.length() <= maxLength)
            return value == null ? "" : value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
