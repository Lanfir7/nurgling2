package nurgling.widgets.compass;

import haven.Coord;
import haven.GOut;
import haven.Gob;
import haven.Loading;
import haven.Resource;
import haven.Tex;
import haven.Text;
import haven.UI;
import haven.Widget;
import nurgling.NGameUI;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NCompassBar extends Widget {
    private static final double[] DIRECTIONS = {
            0.0,
            Math.PI / 4.0,
            Math.PI / 2.0,
            Math.PI * 3.0 / 4.0,
            Math.PI,
            -Math.PI * 3.0 / 4.0,
            -Math.PI / 2.0,
            -Math.PI / 4.0
    };
    private static final Text.Foundry TEXT = new Text.Foundry(Text.sans, 11).aa(true);
    private static final int CACHE_LIMIT = 128;

    private final NGameUI gui;
    private final NCompassTargetCollector collector;
    private final LinkedHashMap<String, Tex> labels = new LinkedHashMap<String, Tex>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tex> eldest) {
            if (size() <= CACHE_LIMIT)
                return false;
            eldest.getValue().dispose();
            return true;
        }
    };

    public NCompassBar(NGameUI gui) {
        super(new Coord(UI.scale(485), contentHeight()));
        this.gui = gui;
        this.collector = new NCompassTargetCollector(gui);
    }

    public static int contentHeight() {
        return UI.scale(80);
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(15, 24, 28, 190);
        g.frect(Coord.z, sz);
        g.chcolor(92, 119, 126, 220);
        int baseline = UI.scale(20);
        g.line(new Coord(0, baseline), new Coord(sz.x - 1, baseline), UI.scale(1));
        g.chcolor();

        if (gui.map == null || gui.map.camera == null)
            return;
        double cameraAngle = gui.map.camera.angle();
        drawDirections(g, cameraAngle, baseline);
        drawTargets(g, cameraAngle);
    }

    private void drawDirections(GOut g, double cameraAngle, int baseline) {
        for (double bearing : DIRECTIONS) {
            NCompassMath.Projection projection = NCompassMath.project(bearing, cameraAngle, sz.x);
            if (projection.region != NCompassMath.Region.FRONT)
                continue;
            boolean center = Math.abs(projection.x - (sz.x / 2)) <= UI.scale(2);
            g.chcolor(center ? new Color(239, 211, 126) : new Color(174, 193, 195));
            int tick = center ? UI.scale(8) : UI.scale(5);
            g.line(new Coord(projection.x, baseline - tick), new Coord(projection.x, baseline + tick), UI.scale(1));
            g.chcolor();
            Tex label = label(L10n.get(NCompassPresentation.directionKey(bearing)),
                    center ? new Color(255, 229, 145) : Color.WHITE);
            g.aimage(label, new Coord(projection.x, UI.scale(2)), 0.5, 0.0);
        }
        g.chcolor(255, 230, 155, 230);
        g.line(new Coord(sz.x / 2, baseline - UI.scale(11)),
                new Coord(sz.x / 2, baseline + UI.scale(11)), UI.scale(1));
        g.chcolor();
    }

    private void drawTargets(GOut g, double cameraAngle) {
        Gob player = gui.map.player();
        if (player == null)
            return;
        List<NCompassTarget> targets = collector.collect();
        List<NCompassLayout.Input> inputs = new ArrayList<>(targets.size());
        Map<String, NCompassTarget> byId = new HashMap<>();
        for (NCompassTarget target : targets) {
            inputs.add(new NCompassLayout.Input(target.id,
                    player.rc.angle(target.position), target.distance));
            byId.put(target.id, target);
        }
        List<NCompassLayout.Marker> markers = NCompassLayout.arrange(
                inputs, cameraAngle, sz.x, UI.scale(70), 2);
        for (NCompassLayout.Marker marker : markers) {
            NCompassTarget target = byId.get(marker.id);
            if (target == null)
                continue;
            try {
                drawMarker(g, marker, target);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void drawMarker(GOut g, NCompassLayout.Marker marker, NCompassTarget target) {
        int iconY = UI.scale(marker.lane == 0 ? 34 : 56);
        Coord center = new Coord(marker.x, iconY);
        if (marker.region == NCompassMath.Region.REAR_LEFT ||
                marker.region == NCompassMath.Region.REAR_RIGHT) {
            int inward = marker.region == NCompassMath.Region.REAR_LEFT ? UI.scale(7) : -UI.scale(7);
            g.chcolor(255, 211, 111, 255);
            g.line(center, center.add(inward, 0), UI.scale(3));
            g.chcolor();
        }

        if (target.icon != null) {
            Resource.Image image = target.icon.get().layer(Resource.imgc);
            if (image != null)
                g.aimage(image.tex(), center, 0.5, 0.5, UI.scale(new Coord(18, 18)));
        } else {
            g.chcolor(Color.BLACK);
            g.fellipse(center, UI.scale(new Coord(7, 7)));
            g.chcolor(target.color);
            g.fellipse(center, UI.scale(new Coord(5, 5)));
            g.chcolor();
        }

        String text = NCompassPresentation.targetLabel(target.name, target.distance, marker.extra);
        Tex label = label(text, target.kind == NCompassTarget.Kind.PARTY ? target.color : Color.WHITE);
        int labelX = Math.max(label.sz().x / 2, Math.min(sz.x - label.sz().x / 2, marker.x));
        g.aimage(label, new Coord(labelX, iconY + UI.scale(9)), 0.5, 0.0);
    }

    private Tex label(String text, Color color) {
        String key = color.getRGB() + "\n" + text;
        Tex cached = labels.get(key);
        if (cached != null)
            return cached;
        Tex created = TEXT.renderstroked(text, color, Color.BLACK).tex();
        labels.put(key, created);
        return created;
    }

    @Override
    public void dispose() {
        for (Tex label : labels.values())
            label.dispose();
        labels.clear();
        super.dispose();
    }
}
