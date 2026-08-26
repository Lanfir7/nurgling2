package nurgling.gattrr;

import haven.*;
import haven.render.*;

import java.awt.Color;

/**
 * Tints farm beehives by harvest state: empty / honey / wax / both.
 * Marker bits match {@code HoneyAndWaxCollector}: honey = 32, wax = 4
 * (35 honey, 6 wax, 39 both).
 */
public class NBeehiveColor extends GAttrib implements Gob.SetupMod {

    static final int HONEY_BIT = 32;
    static final int WAX_BIT = 4;

    enum Kind {
        EMPTY,
        HONEY,
        WAX,
        BOTH
    }

    private static final Color COLOR_EMPTY = new Color(0, 100, 0, 128);
    private static final Color COLOR_HONEY = new Color(180, 140, 0, 140);
    private static final Color COLOR_WAX = new Color(200, 200, 220, 140);
    private static final Color COLOR_BOTH = new Color(160, 80, 0, 140);

    private Pipe.Op fx;
    private Kind lastKind = null;

    public NBeehiveColor(Gob g) {
        super(g);
        this.fx = new MixColor(COLOR_EMPTY);
    }

    static Kind kindOf(int marker) {
        boolean honey = (marker & HONEY_BIT) != 0;
        boolean wax = (marker & WAX_BIT) != 0;
        if (honey && wax) {
            return Kind.BOTH;
        }
        if (honey) {
            return Kind.HONEY;
        }
        if (wax) {
            return Kind.WAX;
        }
        return Kind.EMPTY;
    }

    private void updateColor() {
        try {
            if (gob == null || gob.ngob == null) {
                return;
            }
            Kind kind = kindOf((int) gob.ngob.getModelAttribute());
            if (kind != lastKind) {
                lastKind = kind;
                this.fx = new MixColor(colorOf(kind));
            }
        } catch (Exception e) {
        }
    }

    private static Color colorOf(Kind kind) {
        switch (kind) {
            case HONEY:
                return COLOR_HONEY;
            case WAX:
                return COLOR_WAX;
            case BOTH:
                return COLOR_BOTH;
            case EMPTY:
            default:
                return COLOR_EMPTY;
        }
    }

    @Override
    public Pipe.Op gobstate() {
        try {
            updateColor();
        } catch (Exception e) {
        }
        return fx;
    }
}
