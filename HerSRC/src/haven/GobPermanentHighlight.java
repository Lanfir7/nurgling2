package haven;

import haven.render.MixColor;
import haven.render.Pipe;

import java.awt.*;

public class GobPermanentHighlight extends GAttrib implements Gob.SetupMod {

    public static ColorMask highlightColor = new ColorMask(new Color(116, 0, 178, 200));
    public GobPermanentHighlight(Gob g) {
	super(g);
    }
    
    public Pipe.Op gobstate() {
        return highlightColor;
    }
}