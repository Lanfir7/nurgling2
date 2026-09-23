/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import nurgling.NConfig;
import nurgling.conf.FontSettings;
import nurgling.widgets.nsettings.Fonts;

import java.awt.*;
import java.awt.Graphics;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.AttributedString;
import java.util.*;
import java.util.function.*;

public class Text implements Disposable {
    public static final Font serif = new Font("Serif", Font.PLAIN, 10);
    public static final Font sans  = new Font("Sans", Font.PLAIN, 10);
    public static final Font mono  = new Font("Monospaced", Font.PLAIN, 10);
	public static Font fraktur;
    public static final Font dfont = sans;
    public static final Foundry std;
    public final BufferedImage img;
    public final String text;
    /** Furnace that produced this bitmap; used to re-rasterize at a widget scale. */
    public Furnace fnd;
    /** Fill colour when the furnace is a Foundry; null means the furnace's default. */
    public Color col;
    private Tex tex;
    public static final Color black = Color.BLACK;
    public static final Color white = Color.WHITE;
	
    static {
	std = ((FontSettings)NConfig.get(NConfig.Key.fonts)).getFoundary(Fonts.FontType.DEFAULT);
	fraktur = ((FontSettings)NConfig.get(NConfig.Key.fonts)).getFoundary(Fonts.FontType.UI).font;
    }
	
    /** Decorative interface glyphs. Drawn from a face that contains them, not the text font. */
    static boolean isSymbol(int cp) {
	switch(cp) {
	    case 0x2605: /* ★ */
	    case 0x2606: /* ☆ */
	    case 0x272A: /* ✪ */
	    case 0x2713: /* ✓ */
	    case 0x2715: /* ✕ */
	    case 0x2717: /* ✗ */
	    case 0x25CF: /* ● */
	    case 0x2022: /* • */
	    case 0x25A0: /* ■ */
	    case 0x25CB: /* ○ */
	    case 0x25C6: /* ◆ */
	    case 0x25B2: /* ▲ */
	    case 0x25B6: /* ▶ */
	    case 0x25BA: /* ► */
	    case 0x25BC: /* ▼ */
	    case 0x203A: /* › */
	    case 0x2190: /* ← */
	    case 0x2191: /* ↑ */
	    case 0x2192: /* → */
	    case 0x2193: /* ↓ */
	    case 0x21E7: /* ⇧ */
	    case 0x221E: /* ∞ */
		return(true);
	    default:
		return(false);
	}
    }

    static boolean containsSymbol(String text) {
	for(int i = 0; i < text.length(); ) {
	    int cp = text.codePointAt(i);
	    if(isSymbol(cp))
		return(true);
	    i += Character.charCount(cp);
	}
	return(false);
    }

    private static Font symbolFace;

    static Font symbolFace() {
	Font face = symbolFace;
	if(face != null)
	    return(face);
	Font symbol = new Font("Segoe UI Symbol", Font.PLAIN, 12);
	if(symbol.canDisplay(0x2605) && symbol.canDisplay(0x2713)
		&& "Segoe UI Symbol".equals(symbol.getFamily()))
	    face = symbol;
	else
	    face = new Font("SansSerif", Font.PLAIN, 12);
	symbolFace = face;
	return(face);
    }

    static void pinSymbols(AttributedString text, String raw, Font body) {
	if((raw == null) || (body == null) || !containsSymbol(raw))
	    return;
	Font symbols = symbolFace().deriveFont(body.getStyle(), body.getSize2D());
	int i = 0;
	while(i < raw.length()) {
	    int cp = raw.codePointAt(i);
	    int n = Character.charCount(cp);
	    if(isSymbol(cp))
		text.addAttribute(TextAttribute.FONT, symbols, i, i + n);
	    i += n;
	}
    }

    public static abstract class Slug extends Text {
	public Slug(String text, BufferedImage img) {
	    super(text, img);
	}

	public abstract int baseline();
	public abstract int advance(int pos);
	public abstract int charat(int x);
    }

    public static class Line extends Slug {
	private final FontMetrics m;

	private Line(String text, BufferedImage img, FontMetrics m) {
	    super(text, img);
	    this.m = m;
	}

	@Deprecated
	public Coord base() {
	    return(new Coord(0, m.getLeading() + m.getAscent()));
	}

	public int baseline() {
	    return(m.getLeading() + m.getAscent());
	}

	public int advance(int pos) {
	    return(m.stringWidth(text.substring(0, pos)));
	}

	public int charat(int x) {
	    int l = 0, r = text.length() + 1;
	    while(true) {
		int p = (l + r) / 2;
		int a = advance(p);
		if((a < x) && (l < p)) {
		    l = p;
		} else if((a > x) && (r > p)) {
		    r = p;
		} else {
		    return(p);
		}
	    }
	}
    }

    public static int[] findspaces(String text) {
	java.util.List<Integer> l = new ArrayList<Integer>();
	for(int i = 0; i < text.length(); i++) {
	    char c = text.charAt(i);
	    if(Character.isWhitespace(c))
		l.add(i);
	}
	int[] ret = new int[l.size()];
	for(int i = 0; i < ret.length; i++)
	    ret[i] = l.get(i);
	return(ret);
    }
        
    public static abstract class Furnace {
	public abstract Text render(String text);

	public Text renderf(String fmt, Object... args) {
	    return(render(String.format(fmt, args)));
	}

	/** Quantize a widget scale so rasterized glyphs stay stable while the slider moves. */
	public static int scalekey(double s) {
	    return(Math.max(1, (int)Math.round(s * 100.0)));
	}

	/**
	 * A furnace that draws the same glyphs at {@code s} times this one's
	 * pixel size. The default keeps the bitmap and lets the caller stretch
	 * it; foundries that know their font override this and re-rasterize.
	 */
	public Furnace scaled(double s) {
	    return(this);
	}
    }

    public static abstract class Forge extends Furnace {
	public abstract Slug render(String text);
	public abstract int height();
	public abstract Coord strsize(String text);

	@Override
	public Forge scaled(double s) {
	    return(this);
	}
    }

    public static class Foundry extends Forge {
	public FontMetrics m;
	public final Font font;
	public final Color defcol;
		public boolean aa = true;
		private RichText.Foundry wfnd = null;
		private FontMetrics starMetrics;
		
	public Foundry(Font f, Color defcol) {
	    font = f;
	    this.defcol = defcol;
	    BufferedImage junk = TexI.mkbuf(new Coord(10, 10));
	    java.awt.Graphics tmpl = junk.getGraphics();
	    tmpl.setFont(f);
	    m = tmpl.getFontMetrics();
	}
		
	public Foundry(Font f) {
	    this(f, Color.WHITE);
	}
	
	public Foundry(Font font, int psz, Color defcol) {
	    this(font.deriveFont(UI.scale((float)psz)), defcol);
	}

	public Foundry(Font font, int psz) {
	    this(font.deriveFont(UI.scale((float)psz)));
	}

	public Foundry aa(boolean aa) {
	    this.aa = aa;
	    return(this);
	}

	private Foundry scaledcache;
	private int scaledkey = 100;

	@Override
	public Foundry scaled(double s) {
	    int key = scalekey(s);
	    if(key == 100)
		return(this);
	    if((scaledcache != null) && (scaledkey == key))
		return(scaledcache);
	    float psz = Math.max(1f, Math.round(font.getSize2D() * (key / 100f)));
	    Foundry f = new Foundry(font.deriveFont(psz), defcol);
	    f.aa = aa;
	    scaledcache = f;
	    scaledkey = key;
	    return(f);
	}

	public int height() {
	    /* XXX? The only font which seems to have leading > 0 is
	     * the Moderne Fraktur font, for which the leading is
	     * necessary to get the full ascent of some glyphs.
	     * According to all specifications, this doesn't exactly
	     * seem right, so I'm not sure if it's that font that is
	     * buggy, or if this is actually as it should, but as it
	     * doesn't seem to affect any other fonts, perhaps it
	     * doesn't matter? */
	    return(m.getHeight());
	}

	public Coord strsize(String text) {
	    if(containsSymbol(text))
		return(new Coord(pinnedWidth(text), height()));
	    return(new Coord(m.stringWidth(text), height()));
	}

	private static Font starFace() {
	    return(symbolFace());
	}

	private FontMetrics starMetrics() {
	    if(starMetrics != null)
		return(starMetrics);
	    Font stars = starFace().deriveFont(font.getStyle(), font.getSize2D());
	    BufferedImage junk = TexI.mkbuf(new Coord(10, 10));
	    Graphics measure = junk.getGraphics();
	    measure.setFont(stars);
	    starMetrics = measure.getFontMetrics();
	    measure.dispose();
	    return(starMetrics);
	}

	private int[] starAdvances(String text) {
	    FontMetrics sm = starMetrics();
	    int[] adv = new int[text.length() + 1];
	    int x = 0;
	    int i = 0;
	    while(i < text.length()) {
		int cp = text.codePointAt(i);
		int n = Character.charCount(cp);
		x += (isSymbol(cp) ? sm : m).stringWidth(text.substring(i, i + n));
		for(int k = 1; k <= n; k++)
		    adv[i + k] = x;
		i += n;
	    }
	    return(adv);
	}

	private int pinnedWidth(String text) {
	    int[] adv = starAdvances(text);
	    int width = adv[adv.length - 1];
	    return((width < 1) ? 1 : width);
	}

	private static final class StarLine extends Line {
	    private final int[] advanceAt;

	    private StarLine(String text, BufferedImage img, FontMetrics metrics, int[] advanceAt) {
		super(text, img, metrics);
		this.advanceAt = advanceAt;
	    }

	    public int advance(int pos) {
		if(pos <= 0)
		    return(0);
		if(pos >= advanceAt.length)
		    return(advanceAt[advanceAt.length - 1]);
		return(advanceAt[pos]);
	    }
	}

	private Line renderWithStarFont(String text, Color c) {
	    int[] adv = starAdvances(text);
	    int width = adv[text.length()];
	    if(width < 1)
		width = 1;
	    BufferedImage img = TexI.mkbuf(new Coord(width, height()));
	    Graphics g = img.createGraphics();
	    if(aa)
		Utils.AA(g);
	    g.setColor(c);
	    g.setFont(font);
	    FontMetrics body = g.getFontMetrics();
	    int base = body.getLeading() + body.getAscent();
	    Font stars = starMetrics().getFont();
	    int x = 0;
	    int i = 0;
	    while(i < text.length()) {
		int cp = text.codePointAt(i);
		boolean star = isSymbol(cp);
		int j = i + Character.charCount(cp);
		while(j < text.length()) {
		    int next = text.codePointAt(j);
		    if(isSymbol(next) != star)
			break;
		    j += Character.charCount(next);
		}
		g.setFont(star ? stars : font);
		String run = text.substring(i, j);
		g.drawString(run, x, base);
		x += g.getFontMetrics().stringWidth(run);
		i = j;
	    }
	    g.dispose();
	    Line ln = new StarLine(text, img, m, adv);
	    ln.fnd = this;
	    ln.col = c;
	    return(ln);
	}
                
	public Text renderwrap(String text, Color c, int width) {
	    if(wfnd == null)
		wfnd = new RichText.Foundry(font, defcol);
	    wfnd.aa = aa;
	    text = RichText.Parser.quote(text);
	    if(c != null)
		text = String.format("$col[%d,%d,%d,%d]{%s}", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha(), text);
	    return(wfnd.render(text, width));
	}
                
	public Text renderwrap(String text, int width) {
	    return(renderwrap(text, null, width));
	}
                
	public Line render(String text, Color c) {
	    if(containsSymbol(text))
		return(renderWithStarFont(text, c));
	    Coord sz = strsize(text);
	    if(sz.x < 1)
		sz = sz.add(1, 0);
	    BufferedImage img = TexI.mkbuf(sz);
	    Graphics g = img.createGraphics();
	    if(aa)
		Utils.AA(g);
	    g.setFont(font);
	    g.setColor(c);
	    FontMetrics m = g.getFontMetrics();
	    /* See height() comment. */
	    g.drawString(text, 0, m.getLeading() + m.getAscent());
	    g.dispose();
	    Line ln = new Line(text, img, m);
	    ln.fnd = this;
	    ln.col = c;
	    return(ln);
	}
		
	public Line render(String text) {
	    return(render(text, defcol));
	}

	/* Draws the glyphs four times in the stroke colour, offset by a pixel in each
	 * direction, then once on top in the fill colour. Gives a hard 1px outline
	 * rather than the soft halo BlurFurn produces, which is what the combat HUD
	 * needs to stay legible over arbitrary terrain. */
	public Line renderstroked(String text, Color c, Color stroke) {
	    Coord sz = strsize(text);
	    if(sz.x < 1)
		sz = sz.add(1, 0);
	    sz = sz.add(2, 2);
	    BufferedImage img = TexI.mkbuf(sz);
	    Graphics g = img.createGraphics();
	    if(aa)
		Utils.AA(g);
	    g.setFont(font);
	    FontMetrics m = g.getFontMetrics();
	    int base = m.getLeading() + m.getAscent();
	    g.setColor(stroke);
	    g.drawString(text, 0, base);
	    g.drawString(text, 2, base);
	    g.drawString(text, 1, base - 1);
	    g.drawString(text, 1, base + 1);
	    g.setColor(c);
	    g.drawString(text, 1, base);
	    g.dispose();
	    Line ln = new Line(text, img, m);
	    ln.fnd = this;
	    ln.col = c;
	    return(ln);
	}

	public Line renderstroked(String text, Color c) {
	    return(renderstroked(text, c, Utils.contrast(c)));
	}

	public Line ellipsize(String text, int w, String e) {
	    Line full = render(text);
	    if(full.sz().x <= w)
		return(full);
	    int len = full.charat(w - strsize(e).x);
	    return(render(text.substring(0, len) + e));
	}

	public Line ellipsize(String text, int w) {
	    return(ellipsize(text, w, "\u2026"));
	}

	public static Font fontpxsz(Font font, int pxsz) {
	    int h = 12, l = 1;
	    while(new Foundry(font.deriveFont((float)h)).height() < pxsz) {
		l = h;
		h *= 2;
	    }
	    while(h > l + 1) {
		int m = (l + h) / 2;
		int th = new Foundry(font.deriveFont((float)m)).height();
		if(th < pxsz) {
		    l = m;
		} else if(th > pxsz) {
		    h = m;
		} else {
		    return(font.deriveFont((float)m));
		}
	    }
	    return(font.deriveFont((float)l));
	}
    }

    public static abstract class OffsetForge extends Forge {
	public final Forge back;

	public OffsetForge(Forge back) {
	    this.back = back;
	}

	protected abstract BufferedImage proc(Slug text);
	protected abstract Coord tloff();
	protected abstract Coord broff();

	public class OSlug extends Slug {
	    public final Slug bk;

	    private OSlug(Slug bk, BufferedImage img) {
		super(bk.text, img);
		this.bk = bk;
	    }

	    public int baseline() {return(bk.baseline() + tloff().y);}
	    public int advance(int pos) {return(bk.advance(pos) + tloff().x);}
	    public int charat(int x) {return(bk.charat(x) - tloff().x);}
	}

	public Slug render(String text) {
	    Slug bk = back.render(text);
	    OSlug ret = new OSlug(bk, proc(bk));
	    ret.fnd = this;
	    ret.col = bk.col;
	    return(ret);
	}

	public int height() {
	    return(back.height() + tloff().y + broff().y);
	}

	public Coord strsize(String text) {
	    return(back.strsize(text).add(tloff()).add(broff()));
	}

	public static OffsetForge of(Forge back, Coord tloff, Coord broff, Function<? super Slug, ? extends BufferedImage> prod) {
	    return(new OffsetForge(back) {
		    public BufferedImage proc(Slug text) {return(prod.apply(text));}
		    public Coord tloff() {return(tloff);}
		    public Coord broff() {return(broff);}
		});
	}
    }

    public static abstract class UText<T> implements Indir<Text> {
	public final Furnace fnd;
	private Text cur = null;
	private T cv = null;

	public UText(Furnace fnd) {this.fnd = fnd;}

	protected Text render(String text) {return(fnd.render(text));}
	protected String text(T value) {return(String.valueOf(value));}
	protected abstract T value();

	public Text get() {
	    T value = value();
	    if(!Utils.eq(value, cv))
		cur = render(text(cv = value));
	    return(cur);
	}

	public Indir<Tex> tex() {
	    return(new Indir<Tex>() {
		    public Tex get() {
			return(UText.this.get().tex());
		    }
		});
	}

	public static UText forfield(Furnace fnd, final Object obj, String fn) {
	    final java.lang.reflect.Field f;
	    try {
		f = obj.getClass().getField(fn);
	    } catch(NoSuchFieldException e) {
		throw(new RuntimeException(e));
	    }
	    return(new UText<Object>(fnd) {
		    public Object value() {
			try {
			    return(f.get(obj));
			} catch(IllegalAccessException e) {
			    throw(new RuntimeException(e));
			}
		    }
		});
	}

	public static UText forfield(Object obj, String fn) {
	    return(forfield(std, obj, fn));
	}

	public static <T> UText<T> of(Furnace fnd, Supplier<? extends T> val, Function<? super T, String> fmt) {
	    return(new UText<T>(fnd) {
		    public T value() {return(val.get());}
		    public String text(T value) {return(fmt.apply(value));}
		});
	}

	public static <T> UText<T> of(Furnace fnd, Supplier<T> val) {
	    return(of(fnd, val, String::valueOf));
	}
    }

    protected Text(String text, BufferedImage img) {
	this.text = text;
	this.img = img;
    }
	
    public Coord sz() {
	return(Utils.imgsz(img));
    }
	
    public static Line render(String text, Color c) {
	return(std.render(text, c));
    }
	
    public static Line renderf(Color c, String text, Object... args) {
	return(std.render(String.format(text, args), c));
    }
	
    public static Line render(String text) {
	return(render(text, Color.WHITE));
    }
	
    /**
     * Re-rasterize these glyphs at {@code scale} instead of stretching the
     * existing bitmap. Layout still uses {@link #sz()} of the original.
     */
    public Text rescaled(double scale) {
	if((fnd == null) || (text == null))
	    return(this);
	Furnace sf = fnd.scaled(scale);
	if(sf == fnd)
	    return(this);
	if((col != null) && (sf instanceof Foundry))
	    return(((Foundry)sf).render(text, col));
	return(sf.render(text));
    }

    public Tex tex() {
	if(tex == null)
	    tex = (fnd != null) ? new ScaledTex(this) : new TexI(img);
	return(tex);
    }

    /**
     * A bitmap that can be re-rasterized at a widget scale instead of
     * stretching a frozen image through Scale2D.
     */
    @FunctionalInterface
    public interface ScaleRaster {
	BufferedImage render(double scale);
    }

    /** Live texture: layout size stays at scale 1, glyphs redraw at {@code g.tfscale}. */
    public static Tex live(ScaleRaster raster) {
	BufferedImage img = raster.render(1.0);
	return(new ScaleRasterTex(Utils.imgsz(img), img, raster));
    }

    public static Tex live(Coord layout, BufferedImage base, ScaleRaster raster) {
	return(new ScaleRasterTex(layout, base, raster));
    }

    public void dispose() {
	if(tex != null)
	    tex.dispose();
    }

    /**
     * A text texture that keeps its layout size but, when drawn under a
     * widget scale, samples a freshly rasterized bitmap whose font size
     * matches the screen pixels. Dest size stays the unscaled layout size
     * so Scale2D maps it 1:1 onto those pixels instead of stretching glyphs.
     */
    public static class ScaleRasterTex implements Tex {
	private final Coord layout;
	private final ScaleRaster raster;
	private BufferedImage baseImg;
	private TexI base;
	private TexI hi;
	private int hikey = 100;

	ScaleRasterTex(Coord layout, BufferedImage baseImg, ScaleRaster raster) {
	    this.layout = layout;
	    this.baseImg = baseImg;
	    this.raster = raster;
	}

	public Coord sz() {
	    return(layout);
	}

	private TexI base() {
	    if(base == null)
		base = new TexI(baseImg != null ? baseImg : raster.render(1.0));
	    return(base);
	}

	private TexI at(GOut g) {
	    int key = Furnace.scalekey(g.tfscale);
	    if(key == 100)
		return(base());
	    if((hi != null) && (hikey == key))
		return(hi);
	    if(hi != null) {
		hi.dispose();
		hi = null;
	    }
	    hi = new TexI(raster.render(key / 100.0));
	    hikey = key;
	    return(hi);
	}

	public void crender(GOut g, Coord c, Coord dsz, Coord cul, Coord cbr) {
	    int key = Furnace.scalekey(g.tfscale);
	    if((key == 100) || (g.tfbase == null)) {
		TexI t = at(g);
		if(key != 100)
		    t.filter(haven.render.Texture.Filter.LINEAR);
		t.crender(g, c, dsz, cul, cbr);
		return;
	    }
	    TexI t = at(g);
	    haven.render.Pipe saved = g.state().copy();
	    haven.render.BaseColor colst = g.curstate(haven.render.BaseColor.slot);
	    g.state().copy(g.tfbase);
	    if(colst != null)
		g.usestate(colst);
	    else
		g.chcolor();
	    Coord sul = g.tfpixel(c);
	    Coord ssz = t.sz();
	    if(!dsz.equals(layout) && (layout.x > 0) && (layout.y > 0)) {
		ssz = new Coord(Math.max(1, Math.round(dsz.x * (key / 100.0f))),
				Math.max(1, Math.round(dsz.y * (key / 100.0f))));
	    }
	    t.crender(g, sul, ssz, g.tfpixel(cul), g.tfpixel(cbr));
	    g.state().copy(saved);
	}

	public void render(GOut g, float[] gc, float[] tc) {
	    TexI t = at(g);
	    Coord osz = layout;
	    if((t == base()) || (osz.x < 1) || (osz.y < 1) || t.sz().equals(osz)) {
		t.render(g, gc, tc);
		return;
	    }
	    float sx = (float)t.sz().x / osz.x, sy = (float)t.sz().y / osz.y;
	    float[] ntc = new float[tc.length];
	    for(int i = 0; i < tc.length; i += 2) {
		ntc[i] = tc[i] * sx;
		ntc[i + 1] = tc[i + 1] * sy;
	    }
	    t.render(g, gc, ntc);
	}

	public void dispose() {
	    if(base != null) {
		base.dispose();
		base = null;
	    }
	    if(hi != null) {
		hi.dispose();
		hi = null;
	    }
	}
    }

    /** HUD labels that already go through {@link Text#tex()}. */
    public static class ScaledTex extends ScaleRasterTex {
	ScaledTex(Text src) {
	    super(src.sz(), src.img, s -> src.rescaled(s).img);
	}
    }
    
    public static void main(String[] args) throws Exception {
	String cmd = args[0].intern();
	if(cmd == "render") {
	    PosixArgs opt = PosixArgs.getopt(args, 1, "aw:f:s:c:");
	    boolean aa = false;
	    String font = "SansSerif";
	    int width = 100;
	    float size = 10;
	    Color col = Color.WHITE;
	    for(char c : opt.parsed()) {
		if(c == 'a') {
		    aa = true;
		} else if(c == 'f') {
		    font = opt.arg;
		} else if(c == 'w') {
		    width = Integer.parseInt(opt.arg);
		} else if(c == 's') {
		    size = Float.parseFloat(opt.arg);
		} else if(c == 'c') {
		    col = Color.decode(opt.arg);
		}
	    }
	    Foundry f = new Foundry(new Font(font, Font.PLAIN, 10).deriveFont(size), col);
	    f.aa = aa;
	    Text t = f.renderwrap(opt.rest[0], width);
	    try(java.io.OutputStream out = java.nio.file.Files.newOutputStream(Utils.path(opt.rest[1]))) {
		javax.imageio.ImageIO.write(t.img, "PNG", out);
	    }
	}
    }

    public static final Foundry num12boldFnd = new Foundry(sans.deriveFont(Font.BOLD), 12).aa(true);

    public static Line renderstroked(String text, Color c, Color s, Foundry fnd) {
	return(fnd.renderstroked(text, c, s));
    }

    public static Line renderstroked(String text, Color c, Color s) {
	return(renderstroked(text, c, s, std));
    }

    public static Line renderstroked(String text, Color c, Foundry fnd) {
	return(renderstroked(text, c, Utils.contrast(c), fnd));
    }

    public static Line renderstroked(String text, Color c) {
	return(renderstroked(text, c, Utils.contrast(c)));
    }

    public static Line renderstroked(String text, Foundry fnd) {
	return(renderstroked(text, Color.WHITE, Color.BLACK, fnd));
    }

    public static Line renderstroked(String text) {
	return(renderstroked(text, Color.WHITE, Color.BLACK));
    }
}
