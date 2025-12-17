/* Preprocessed source code */
package haven.res.ui.tt.level;

import haven.*;
import java.awt.Color;

/* >tt: Level */
@haven.FromResource(name = "ui/tt/level", version = 21)
public class Level extends ItemInfo implements GItem.OverlayInfo<Double> {
    public static final Color defcolor = Color.WHITE;
    public static final int h = UI.scale(3);
    public static final int m = UI.scale(1);
    public final double max, cur;
    public final Color color;

    public Level(Owner owner, double max, double cur, Color color) {
	super(owner);
	this.max = max;
	this.cur = cur;
	this.color = color;
    }

    public static void drawmeter(GOut g, double l, Color color, Color ocolor) {
	Coord sz = g.sz();
	if (!OptWnd.verticalContainerIndicatorsCheckBox.a) {
		int w = (int) (sz.x * l);
		g.chcolor(ocolor);
		g.frect(new Coord(0, sz.y - UI.scale(6)), new Coord(sz.x - UI.scale(1), UI.scale(5)));
		g.chcolor(color);
		g.frect(new Coord(UI.scale(1), sz.y - UI.scale(5)), new Coord(w - UI.scale(3), UI.scale(3)));
	} else {
		int h = (int) (sz.y * l);
		g.chcolor(ocolor);
		g.frect(new Coord(UI.scale(1), 0), new Coord(UI.scale(5), sz.y - UI.scale(1)));
		g.chcolor(color);
		g.frect(new Coord(UI.scale(2), sz.y - h + UI.scale(1)), new Coord(UI.scale(3), h - UI.scale(3)));
	}
	g.chcolor();
    }

    public Double overlay() {
	return(cur / max);
    }

    public void drawoverlay(GOut g, Double l) {
	drawmeter(g, l, color, Color.BLACK);
    }

    public static ItemInfo mkinfo(Owner owner, Object... args) {
	double max = ((Number)args[1]).doubleValue();
	double cur = ((Number)args[2]).doubleValue();
	Color color = (args.length > 3) ? (Color)args[3] : null;
	if(color == null)
	    color = defcolor;
	return(new Level(owner, max, cur, color));
    }
}
