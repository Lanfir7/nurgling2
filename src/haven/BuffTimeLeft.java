package haven;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Formats remaining wall-clock time for compact buff labels. */
public final class BuffTimeLeft {
    private BuffTimeLeft() {}

    public static String formatForGameTime(double end, double now, double gameSecondsPerRealSecond) {
	if(!(gameSecondsPerRealSecond > 0))
	    return(null);
	return(format((end - now) / gameSecondsPerRealSecond));
    }

    public static String format(double seconds) {
	if(!(seconds > 0))
	    return(null);
	long s = (long)Math.ceil(seconds);
	if(s >= 86400) {
	    long n = s / 86400;
	    return(n + " " + russianPlural(n, "день", "дня", "дней"));
	} else if(s >= 3600) {
	    long n = s / 3600;
	    return(n + " " + russianPlural(n, "час", "часа", "часов"));
	} else if(s >= 60) {
	    return((s / 60) + " мин");
	}
	return(s + " сек");
    }

    static BufferedImage renderPlate(String text, int iconWidth) {
	for(int size = 9; size >= 5; size--) {
	    BufferedImage plate = plate(text, size);
	    if(plate.getWidth() <= iconWidth)
		return(plate);
	}
	BufferedImage plate = plate(text, 5);
	if(plate.getWidth() <= iconWidth)
	    return(plate);
	int width = Math.max(1, iconWidth);
	int height = Math.max(1, (int)Math.ceil((double)plate.getHeight() * width / plate.getWidth()));
	BufferedImage fitted = TexI.mkbuf(Coord.of(width, height));
	Graphics2D g = fitted.createGraphics();
	try {
	    g.drawImage(plate, 0, 0, width, height, null);
	} finally {
	    g.dispose();
	}
	return(fitted);
    }

    private static BufferedImage plate(String text, int fontSize) {
	Text.Foundry foundry = new Text.Foundry(Text.dfont, fontSize).aa(true);
	BufferedImage label = Utils.outline2(foundry.render(text, Color.WHITE).img, Color.BLACK);
	int pad = UI.scale(2);
	BufferedImage plate = TexI.mkbuf(Coord.of(label.getWidth() + (pad * 2), label.getHeight() + (pad * 2)));
	Graphics2D g = plate.createGraphics();
	try {
	    g.setColor(new Color(0, 0, 0, 160));
	    g.fillRoundRect(0, 0, plate.getWidth(), plate.getHeight(), UI.scale(4), UI.scale(4));
	    g.drawImage(label, pad, pad, null);
	} finally {
	    g.dispose();
	}
	return(plate);
    }

    private static String russianPlural(long n, String one, String few, String many) {
	long mod100 = n % 100;
	if((mod100 >= 11) && (mod100 <= 14))
	    return(many);
	switch((int)(n % 10)) {
	case 1: return(one);
	case 2: case 3: case 4: return(few);
	default: return(many);
	}
    }
}
