package nurgling.widgets;

import haven.Button;
import haven.GOut;
import haven.HSlider;
import haven.Text;
import haven.UI;
import haven.Widget;
import nurgling.i18n.L10n;

import java.awt.Color;

/** Legend for the Quarryartz quality layer on the map window. */
public class QuarryartzTopoLegend extends Widget {
    private static final int[] BANDS = {160, 200, 230, 260, 300};
    private final Text title;
    private final Text resource;
    private final Text note;
    private final Text alphaLbl;
    private final Text[] bands = new Text[BANDS.length];
    private final HSlider alpha;
    private final Button step;

    public QuarryartzTopoLegend() {
        int w = UI.scale(188);
        title = Text.render(L10n.get("maptools.quarryartz_topo_title"));
        resource = Text.render(L10n.get("maptools.quarryartz_topo_resource"));
        note = Text.render(L10n.get("maptools.quarryartz_topo_note"), new Color(190, 180, 150));
        alphaLbl = Text.render(L10n.get("maptools.quarryartz_topo_alpha"));
        String[] names = {
                L10n.get("maptools.quarryartz_topo_low"),
                L10n.get("maptools.quarryartz_topo_poor"),
                L10n.get("maptools.quarryartz_topo_mid"),
                L10n.get("maptools.quarryartz_topo_high"),
                L10n.get("maptools.quarryartz_topo_top")
        };
        for (int i = 0; i < BANDS.length; i++)
            bands[i] = Text.render(names[i]);
        alpha = add(new HSlider(UI.scale(100), 40, 200, NMiniMap.quarryartzTopoAlpha()) {
            public void changed() {
                NMiniMap.quarryartzTopoAlpha(val);
            }
        }, new haven.Coord(UI.scale(78), UI.scale(112)));
        step = add(new Button(UI.scale(64), L10n.get("maptools.quarryartz_topo_step", NMiniMap.quarryartzTopoStep())) {
            public void click() {
                int next = NMiniMap.quarryartzTopoStep() == 10 ? 20 : 10;
                NMiniMap.quarryartzTopoStep(next);
                change(L10n.get("maptools.quarryartz_topo_step", next));
            }
        }, new haven.Coord(UI.scale(8), UI.scale(132)));
        resize(w, UI.scale(176));
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(14, 16, 18, 220);
        g.frect(haven.Coord.z, sz);
        g.chcolor(120, 104, 64, 255);
        g.rect(haven.Coord.z, sz.sub(1, 1));
        g.chcolor();
        g.image(title.tex(), new haven.Coord(UI.scale(8), UI.scale(4)));
        g.image(resource.tex(), new haven.Coord(UI.scale(8), UI.scale(18)));
        int y = UI.scale(36);
        for (int i = 0; i < BANDS.length; i++) {
            int argb = QuarryartzTopo.color(BANDS[i], 255);
            g.chcolor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, 255);
            g.frect(new haven.Coord(UI.scale(8), y), new haven.Coord(UI.scale(12), UI.scale(10)));
            g.chcolor();
            g.image(bands[i].tex(), new haven.Coord(UI.scale(24), y - UI.scale(1)));
            y += UI.scale(14);
        }
        g.image(alphaLbl.tex(), new haven.Coord(UI.scale(8), UI.scale(112)));
        g.image(note.tex(), new haven.Coord(UI.scale(8), UI.scale(158)));
        super.draw(g);
    }
}
