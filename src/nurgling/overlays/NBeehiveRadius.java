package nurgling.overlays;

import haven.Gob;
import haven.render.Render;
import nurgling.NConfig;
import nurgling.conf.NAreaRadStyle;

public class NBeehiveRadius extends NAreaRad {
    private boolean oldState = false;
    private int lastRadius = -1;

    public NBeehiveRadius(Gob owner) {
        super(owner, shown() ? NAreaRadStyle.beehiveRadius() : 0f, Palette.BEEHIVE);
        oldState = shown();
        lastRadius = NAreaRadStyle.beehiveRadius();
    }

    private static boolean shown() {
        Object v = NConfig.get(NConfig.Key.showBeehiveRadius);
        return (v instanceof Boolean) && (Boolean) v;
    }

    @Override
    public void gtick(Render g) {
        boolean currentState = shown();
        int rad = NAreaRadStyle.beehiveRadius();
        if (oldState != currentState || (currentState && lastRadius != rad)) {
            oldState = currentState;
            lastRadius = rad;
            setR(g, currentState ? rad : 0);
        }
        if (oldState)
            super.gtick(g);
    }
}
