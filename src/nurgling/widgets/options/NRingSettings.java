package nurgling.widgets.options;

import haven.Label;
import haven.*;
import nurgling.NConfig;
import nurgling.conf.NAreaRad;
import nurgling.conf.NAreaRadStyle;
import nurgling.i18n.L10n;
import nurgling.widgets.NColorWidget;
import nurgling.widgets.nsettings.Panel;

import java.awt.Color;
import java.util.ArrayList;

public class NRingSettings extends Panel {

    private HSlider bandSlider, lineSlider, alphaSlider;
    private Label bandLabel, lineLabel, alphaLabel;
    private NColorWidget animalFill, animalEdge, beehiveFill, beehiveEdge;
    private CheckBox beehiveVis;
    private TextEntry beehiveRadEntry;
    private final ArrayList<ElementSettings> animalRows = new ArrayList<>();

    public NRingSettings() {
        super();
        Widget content = add(new Widget(Coord.z) {
            @Override
            public void pack() {
                resize(contentsz());
            }
        }, Coord.z);

        Widget prev = content.add(new Label(L10n.get("rings.style_title")), Coord.z);

        bandLabel = content.add(new Label(L10n.get("rings.band_height") + " " + NAreaRadStyle.bandHeight()),
                prev.pos("bl").adds(0, 8));
        prev = bandSlider = content.add(new HSlider(UI.scale(200), 1, 30, NAreaRadStyle.bandHeight()) {
            public void changed() {
                NConfig.set(NConfig.Key.areaRadBandHeight, val);
                bandLabel.settext(L10n.get("rings.band_height") + " " + val);
            }
        }, bandLabel.pos("bl").adds(0, 4));

        lineLabel = content.add(new Label(L10n.get("rings.line_width") + " " + NAreaRadStyle.lineWidth()),
                prev.pos("bl").adds(0, 8));
        prev = lineSlider = content.add(new HSlider(UI.scale(200), 1, 10, NAreaRadStyle.lineWidth()) {
            public void changed() {
                NConfig.set(NConfig.Key.areaRadLineWidth, val);
                lineLabel.settext(L10n.get("rings.line_width") + " " + val);
            }
        }, lineLabel.pos("bl").adds(0, 4));

        alphaLabel = content.add(new Label(L10n.get("rings.fill_alpha") + " " + NAreaRadStyle.fillAlpha()),
                prev.pos("bl").adds(0, 8));
        prev = alphaSlider = content.add(new HSlider(UI.scale(200), 0, 255, NAreaRadStyle.fillAlpha()) {
            public void changed() {
                NConfig.set(NConfig.Key.areaRadFillAlpha, val);
                alphaLabel.settext(L10n.get("rings.fill_alpha") + " " + val);
            }
        }, alphaLabel.pos("bl").adds(0, 4));

        prev = content.add(new Label(L10n.get("rings.animal_colors")), prev.pos("bl").adds(0, 12));
        prev = animalFill = content.add(new NColorWidget(L10n.get("rings.fill")), prev.pos("bl").adds(0, 4));
        animalFill.color = NConfig.getColor(NConfig.Key.areaRadAnimalFill, NAreaRadStyle.DEF_ANIMAL_FILL);
        prev = animalEdge = content.add(new NColorWidget(L10n.get("rings.edge")), prev.pos("bl").adds(0, 4));
        animalEdge.color = NConfig.getColor(NConfig.Key.areaRadAnimalEdge, NAreaRadStyle.DEF_ANIMAL_EDGE);

        prev = content.add(new Label(L10n.get("rings.beehive_colors")), prev.pos("bl").adds(0, 12));
        Object hiveVis = NConfig.get(NConfig.Key.showBeehiveRadius);
        beehiveVis = content.add(new CheckBox(L10n.get("rings.beehive")) {
            {
                a = (hiveVis instanceof Boolean) && (Boolean) hiveVis;
            }
            @Override
            public void changed(boolean val) {
                super.changed(val);
                NConfig.set(NConfig.Key.showBeehiveRadius, val);
            }
        }, prev.pos("bl").adds(0, 4));
        prev = beehiveVis;

        beehiveRadEntry = content.add(new TextEntry(UI.scale(50), String.valueOf(NAreaRadStyle.beehiveRadius())) {
            @Override
            public void done(ReadLine buf) {
                super.done(buf);
                try {
                    int val = Integer.parseInt(buf.line());
                    val = NAreaRadStyle.numberOr(val, NAreaRadStyle.DEF_BEEHIVE_RADIUS, 1, 500);
                    NConfig.set(NConfig.Key.beehiveRadius, val);
                    settext(String.valueOf(val));
                } catch (NumberFormatException ignored) {}
            }
        }, beehiveVis.pos("ur").adds(10, 0));

        prev = beehiveFill = content.add(new NColorWidget(L10n.get("rings.fill")), prev.pos("bl").adds(0, 6));
        beehiveFill.color = NConfig.getColor(NConfig.Key.areaRadBeehiveFill, NAreaRadStyle.DEF_BEEHIVE_FILL);
        prev = beehiveEdge = content.add(new NColorWidget(L10n.get("rings.edge")), prev.pos("bl").adds(0, 4));
        beehiveEdge.color = NConfig.getColor(NConfig.Key.areaRadBeehiveEdge, NAreaRadStyle.DEF_BEEHIVE_EDGE);

        prev = content.add(new Label(L10n.get("rings.settings_title")), prev.pos("bl").adds(0, 14));
        ArrayList<NAreaRad> radProps = ((ArrayList<NAreaRad>) NConfig.get(NConfig.Key.animalrad));
        if (radProps != null) {
            for (NAreaRad prop : radProps) {
                ElementSettings row = new ElementSettings(prop, UI.scale(520), UI.scale(22));
                prev = content.add(row, prev.pos("bl").adds(0, 5));
                animalRows.add(row);
            }
        }

        content.pack();
        resize(content.sz);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        syncColor(animalFill, NConfig.Key.areaRadAnimalFill, NAreaRadStyle.DEF_ANIMAL_FILL);
        syncColor(animalEdge, NConfig.Key.areaRadAnimalEdge, NAreaRadStyle.DEF_ANIMAL_EDGE);
        syncColor(beehiveFill, NConfig.Key.areaRadBeehiveFill, NAreaRadStyle.DEF_BEEHIVE_FILL);
        syncColor(beehiveEdge, NConfig.Key.areaRadBeehiveEdge, NAreaRadStyle.DEF_BEEHIVE_EDGE);
    }

    private static void syncColor(NColorWidget w, NConfig.Key key, Color fallback) {
        if (w == null || w.color == null)
            return;
        Color cur = NConfig.getColor(key, fallback);
        if (w.color.getRGB() != cur.getRGB())
            NConfig.setColor(key, w.color);
    }

    @Override
    public void save() {
        NConfig.set(NConfig.Key.areaRadBandHeight, bandSlider.val);
        NConfig.set(NConfig.Key.areaRadLineWidth, lineSlider.val);
        NConfig.set(NConfig.Key.areaRadFillAlpha, alphaSlider.val);
        if (animalFill.color != null)
            NConfig.setColor(NConfig.Key.areaRadAnimalFill, animalFill.color);
        if (animalEdge.color != null)
            NConfig.setColor(NConfig.Key.areaRadAnimalEdge, animalEdge.color);
        if (beehiveFill.color != null)
            NConfig.setColor(NConfig.Key.areaRadBeehiveFill, beehiveFill.color);
        if (beehiveEdge.color != null)
            NConfig.setColor(NConfig.Key.areaRadBeehiveEdge, beehiveEdge.color);
        NConfig.set(NConfig.Key.showBeehiveRadius, beehiveVis.a);
        try {
            int val = Integer.parseInt(beehiveRadEntry.text());
            NConfig.set(NConfig.Key.beehiveRadius, NAreaRadStyle.numberOr(val, NAreaRadStyle.DEF_BEEHIVE_RADIUS, 1, 500));
        } catch (NumberFormatException ignored) {}
        for (ElementSettings row : animalRows) {
            try {
                row.rad.radius = Integer.parseInt(row.radEntry.text());
            } catch (Exception ignored) {}
        }
        NConfig.needUpdate();
    }

    static String displayName(String path) {
        int slash = path.lastIndexOf('/');
        String id = slash >= 0 ? path.substring(slash + 1) : path;
        String key = "rings.animal." + id;
        return L10n.hasKey(key) ? L10n.get(key) : id;
    }

    public class ElementSettings extends Widget {
        final NAreaRad rad;
        final int itemHeight;

        CheckBox visBox;
        Label nameLabel;
        TextEntry radEntry;

        public ElementSettings(NAreaRad rad, int width, int height) {
            super(new Coord(width, height));
            this.rad = rad;
            this.itemHeight = height;

            visBox = add(new CheckBox("") {
                {
                    a = rad.vis;
                }
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    rad.vis = val;
                    NConfig.needUpdate();
                }
            }, new Coord(0, (itemHeight - UI.scale(16)) / 2));

            nameLabel = add(new Label(displayName(rad.name)), new Coord(UI.scale(24), (itemHeight - UI.scale(16)) / 2));

            radEntry = add(new TextEntry(UI.scale(80), String.valueOf(rad.radius)) {
                @Override
                public void done(ReadLine buf) {
                    super.done(buf);
                    try {
                        rad.radius = Integer.parseInt(buf.line());
                        NConfig.needUpdate();
                    } catch (Exception ignored) { }
                }
            }, new Coord(UI.scale(170), (itemHeight - UI.scale(16)) / 2));

            resize(new Coord(width, itemHeight));
        }

        @Override
        public void resize(Coord sz) {
            super.resize(sz);
            int cy = (itemHeight - UI.scale(16)) / 2;
            if (visBox != null)
                visBox.move(new Coord(0, cy));
            if (nameLabel != null)
                nameLabel.move(new Coord(UI.scale(24), cy));
            if (radEntry != null)
                radEntry.move(new Coord(UI.scale(170), cy));
        }
    }
}
