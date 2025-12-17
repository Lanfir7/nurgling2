package haven;

import haven.render.MixColor;
import haven.render.Pipe;

import java.awt.*;

public class GobStateHighlight extends GAttrib implements Gob.SetupMod {
    public Color color;
    public MixColor mixColor;

    public GobStateHighlight(Gob g, Color color) {
	super(g);
    this.color = color;
    }
    
    public Pipe.Op gobstate() {
        if (color != null) {
            mixColor = new MixColor(color);
            color = null;
        }
        return mixColor;
    }
}