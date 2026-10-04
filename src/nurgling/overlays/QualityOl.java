package nurgling.overlays;

import haven.*;
import haven.render.Pipe;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

public class QualityOl extends NObjectTexLabel {
    public static Text.Furnace fnd = new PUtils.BlurFurn(new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 16).aa(true), UI.scale(1), UI.scale(1), Color.BLACK);
    private static TexI qIcon = new TexI(Resource.loadsimg("nurgling/hud/quality"));
    public final double quality;

    public QualityOl(Gob target, double quality) {
        super(target);
        gob = target;
        pos = new Coord3f(0,0, 3);
        this.quality = quality;
        this.img = new TexI(fnd.render(String.valueOf((int) Math.round(quality))).img);
    }


    Gob gob;

    @Override
    public boolean tick(double dt) {
        return false;
    }


    @Override
    public void draw(GOut g, Pipe state)
    {
        Coord sc = projectBillboard(g, state, owner, pos);
        g.aimage(qIcon, sc, 0.5,0.5);
        g.aimage(img, sc.add(UI.scale(20,0)), 0.5,0.5);
    }
}
