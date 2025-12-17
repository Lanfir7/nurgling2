package haven;

import haven.render.MixColor;
import haven.render.Pipe;

import java.awt.*;

public class GobCombatHighlight extends GAttrib implements Gob.SetupMod {
    public static MixColor COMBAT_FOE_MIXCOLOR = new MixColor(OptWnd.combatFoeColorOptionWidget.currentColor.getRed(), OptWnd.combatFoeColorOptionWidget.currentColor.getGreen(),
                                                OptWnd.combatFoeColorOptionWidget.currentColor.getBlue(), OptWnd.combatFoeColorOptionWidget.currentColor.getAlpha());

    public GobCombatHighlight(Gob g) {
	super(g);
    }
    
    public void start() {
    }
    
    public Pipe.Op gobstate() {
        return COMBAT_FOE_MIXCOLOR;
    }
}