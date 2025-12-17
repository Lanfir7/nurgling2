package haven;

import haven.render.MixColor;
import haven.render.Pipe;

import java.awt.*;

public class GobPartyHighlight extends GAttrib implements Gob.SetupMod {
    public Color color;
    public MixColor mixColor;
    
    public GobPartyHighlight(Gob g, Color color) {
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