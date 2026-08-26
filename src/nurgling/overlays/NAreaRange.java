package nurgling.overlays;

import haven.Gob;
import haven.render.Render;

public class NAreaRange extends NAreaRad {
    boolean oldState = false;
    int lastRadius = -1;
    nurgling.conf.NAreaRad prop;
    public NAreaRange(Owner owner, nurgling.conf.NAreaRad prop) {
        super((Gob) owner, prop.radius);
        this.prop = prop;
        this.oldState = prop.vis;
        this.lastRadius = prop.radius;
    }

    @Override
    public void gtick(Render g) {
        boolean vis = prop.vis;
        int rad = prop.radius;
        if (oldState != vis || (vis && lastRadius != rad)) {
            oldState = vis;
            lastRadius = rad;
            setR(g, vis ? (float) rad : 0);
        }
        if (oldState)
            super.gtick(g);
    }

    @Override
    public boolean tick(double dt) {
        return !prop.vis || super.tick(dt);
    }
}
