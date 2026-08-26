package nurgling.conf;

import nurgling.NConfig;

import java.awt.Color;

/** Shared appearance for {@code NAreaRad} overlays (animals, beehives, troughs, mound beds). */
public final class NAreaRadStyle {

    public static final int DEF_BAND = 10;
    public static final int DEF_LINE = 4;
    public static final int DEF_ALPHA = 128;
    public static final int DEF_BEEHIVE_RADIUS = 150;

    public static final Color DEF_ANIMAL_FILL = new Color(192, 0, 0);
    public static final Color DEF_ANIMAL_EDGE = new Color(255, 224, 96);
    public static final Color DEF_BEEHIVE_FILL = new Color(0, 163, 192);
    public static final Color DEF_BEEHIVE_EDGE = new Color(0, 192, 0);

    private NAreaRadStyle() {}

    public static Color withAlpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        if (c == null)
            return new Color(0, 0, 0, a);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static int numberOr(Object o, int def, int min, int max) {
        int v = def;
        if (o instanceof Number)
            v = ((Number) o).intValue();
        return Math.max(min, Math.min(max, v));
    }

    public static int bandHeight() {
        return numberOr(NConfig.get(NConfig.Key.areaRadBandHeight), DEF_BAND, 1, 30);
    }

    public static int lineWidth() {
        return numberOr(NConfig.get(NConfig.Key.areaRadLineWidth), DEF_LINE, 1, 10);
    }

    public static int fillAlpha() {
        return numberOr(NConfig.get(NConfig.Key.areaRadFillAlpha), DEF_ALPHA, 0, 255);
    }

    public static int beehiveRadius() {
        return numberOr(NConfig.get(NConfig.Key.beehiveRadius), DEF_BEEHIVE_RADIUS, 1, 500);
    }

    public static Color animalFill() {
        return withAlpha(NConfig.getColor(NConfig.Key.areaRadAnimalFill, DEF_ANIMAL_FILL), fillAlpha());
    }

    public static Color animalEdge() {
        return NConfig.getColor(NConfig.Key.areaRadAnimalEdge, DEF_ANIMAL_EDGE);
    }

    public static Color beehiveFill() {
        return withAlpha(NConfig.getColor(NConfig.Key.areaRadBeehiveFill, DEF_BEEHIVE_FILL), fillAlpha());
    }

    public static Color beehiveEdge() {
        return NConfig.getColor(NConfig.Key.areaRadBeehiveEdge, DEF_BEEHIVE_EDGE);
    }
}
