package nurgling;

import haven.*;
import haven.WoundWnd.*;
import haven.res.ui.tt.attrmod.*;
import java.util.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import static haven.CharWnd.*;
import static haven.PUtils.*;
import nurgling.i18n.L10n;
import nurgling.tools.CraftRecipeLookup;
import nurgling.tools.HarvestState;
import nurgling.tools.RecipeIngredientCache;
import nurgling.tools.WoundTreatments;

public class NWoundBox extends WoundWnd.WoundBox {
    private static final Text.Foundry nameFnd = new Text.Foundry(
	nurgling.conf.FontSettings.getOpenSansSemibold(), 14, Color.WHITE).aa(true);

    private static final java.awt.Font descFont =
	nurgling.conf.FontSettings.getOpenSans().deriveFont(
	    (float)Math.floor(UI.scale(11.0)));

    private static final Text.Foundry effectFnd = new Text.Foundry(descFont).aa(true);

    private static final RichText.Foundry descFnd = new RichText.Foundry(
	RichText.IMAGESRC, RichText.ImageSource.legacy,
	TextAttribute.FONT, descFont).aa(true);

    private static final Coord EFFECT_ICON_SZ = UI.scale(new Coord(11, 11));
    private static final int TREAT_GAP = UI.scale(4);
    private static final int TREAT_PAD = UI.scale(6);

    private final List<Widget> treatWidgets = new ArrayList<>();
    private Widget treatBar;
    private Label treatLabel;
    private String treatKey = "";
    private int treatPad = 0;

    public NWoundBox(int id) {
	super(id);
    }

    @Override
    protected int contentBottomPad() {
	return treatPad;
    }

    @Override
    public void tick(double dt) {
	syncTreatments();
	super.tick(dt);
    }

    @Override
    public void resize(Coord sz) {
	super.resize(sz);
	layoutTreatments();
    }

    @Override
    public void drawbg(GOut g) {
	g.chcolor(NStyle.infoBg);
	g.frect(Coord.z, sz);
	g.chcolor();
    }

    @Override
    public BufferedImage renderinfo(int width) {
	Wound wnd = wound();
	List<ItemInfo> info = wnd.info();
	Coord iconSz = UI.scale(new Coord(76, 76));
	BufferedImage icon = convolvedown(wnd.icon(), iconSz, iconfilter);
	ItemInfo.Name nm = ItemInfo.find(ItemInfo.Name.class, info);
	String name = (nm != null) ? nm.str.text : "";
	Text.Line nameLine = nameFnd.render(name);

	// Scan for first visible row in name for top-alignment with icon
	int nameAdj = 0;
	findName:
	for(int row = 0; row < nameLine.img.getHeight(); row++) {
	    for(int col = 0; col < nameLine.img.getWidth(); col++) {
		if((nameLine.img.getRGB(col, row) & 0xFF000000) != 0) {
		    nameAdj = row;
		    break findName;
		}
	    }
	}

	int titleX = iconSz.x + UI.scale(10);
	int titleAreaW = width - titleX;

	// Collect AttrMod effects — two-pass for tabular alignment
	List<Mod> mods = new ArrayList<>();
	for(ItemInfo inf : info) {
	    if(inf instanceof AttrMod)
		for(Entry e : ((AttrMod)inf).tab)
		    if(e instanceof Mod)
			mods.add((Mod)e);
	}

	int iconGap = UI.scale(5);
	int valGap = UI.scale(5);
	BufferedImage[] eIcons = new BufferedImage[mods.size()];
	BufferedImage[] eNames = new BufferedImage[mods.size()];
	BufferedImage[] eVals  = new BufferedImage[mods.size()];
	int maxNameW = 0;
	int eLineH = 0;

	for(int i = 0; i < mods.size(); i++) {
	    Mod mod = mods.get(i);
	    eNames[i] = effectFnd.render(mod.attr.name()).img;
	    Color valCol = (mod.mod < 0) ? new Color(255, 128, 128) : new Color(128, 255, 128);
	    String sign = (mod.mod < 0) ? "-" : "+";
	    eVals[i] = effectFnd.render(String.format("%s%d", sign, Math.round(Math.abs(mod.mod))), valCol).img;
	    eIcons[i] = mod.attr.icon();
	    if(eIcons[i] != null)
		eIcons[i] = convolvedown(eIcons[i], EFFECT_ICON_SZ, iconfilter);
	    maxNameW = Math.max(maxNameW, eNames[i].getWidth());
	    eLineH = Math.max(eLineH, Math.max(eNames[i].getHeight(), EFFECT_ICON_SZ.y));
	}

	// Render description text (pagina)
	Resource.Pagina pag = wnd.res.get().layer(Resource.pagina);
	String pagText = (pag != null) ? pag.text : "";
	RichText descRt = null;
	if(!pagText.isEmpty())
	    descRt = descFnd.render(resdoc(wnd.res.get(), pagText), width);

	// Compute layout
	int nameBottom = -nameAdj + nameLine.sz().y;
	int nameEffectGap = 6; // ~10px visual from name baseline to effect top

	// Effects below name, right of icon
	int effectsBottom = nameBottom + nameEffectGap;
	effectsBottom += mods.size() * eLineH;

	// Body starts below whichever is taller: icon or name+effects area
	int headerH = Math.max(iconSz.y, effectsBottom + nameAdj);
	int y = headerH + 11;

	if(descRt != null)
	    y += descRt.sz().y;

	BufferedImage result = TexI.mkbuf(new Coord(width, y));
	Graphics2D g = result.createGraphics();
	g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
	    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

	// Draw icon at top-left
	g.drawImage(icon, 0, 0, null);

	// Draw name to the right, top-aligned with icon
	g.drawImage(nameLine.img, titleX, -nameAdj, null);

	// Draw effects with tabular alignment
	int eIconW = (eIcons.length > 0 && eIcons[0] != null) ? eIcons[0].getWidth() : 0;
	int eNameX = titleX + eIconW + iconGap;
	int eValX  = titleX + eIconW + iconGap + maxNameW + valGap;
	int ey = nameBottom + nameEffectGap;
	for(int i = 0; i < mods.size(); i++) {
	    int textH = eNames[i].getHeight();
	    if(eIcons[i] != null) {
		int iconY = ey + (textH - EFFECT_ICON_SZ.y) / 2;
		g.drawImage(eIcons[i], titleX, iconY, null);
	    }
	    g.drawImage(eNames[i], eNameX, ey, null);
	    g.drawImage(eVals[i], eValX, ey, null);
	    ey += eLineH;
	}

	// Draw description below header
	if(descRt != null)
	    g.drawImage(descRt.img, 0, headerH + 11, null);

	g.dispose();
	return result;
    }

    private void syncTreatments() {
	String key = woundKey();
	if(Utils.eq(key, treatKey))
	    return;
	treatKey = key;
	rebuildTreatments(key.isEmpty() ? Collections.emptyList() : WoundTreatments.forWound(key));
    }

    private String woundKey() {
	try {
	    Wound w = wound();
	    if(w == null || w.res == null)
		return "";
	    Resource res = w.res.get();
	    Resource.Tooltip tt = res.layer(Resource.tooltip);
	    if(tt != null && tt.t != null && !tt.t.isEmpty())
		return tt.t;
	    return w.name();
	} catch(Loading l) {
	    return treatKey;
	}
    }

    private void rebuildTreatments(List<String> items) {
	for(Widget w : treatWidgets)
	    w.reqdestroy();
	treatWidgets.clear();
	treatBar = null;
	treatLabel = null;
	if(items.isEmpty()) {
	    treatPad = 0;
	    refreshScrollMax();
	    return;
	}
	treatBar = add(new TreatBar());
	treatWidgets.add(treatBar);
	treatLabel = add(new Label(L10n.get("char.wound.treat"), effectFnd));
	treatWidgets.add(treatLabel);
	for(String item : items) {
	    treatWidgets.add(add(new TreatIcon(item)));
	}
	layoutTreatments();
	refreshScrollMax();
    }

    private void layoutTreatments() {
	if(treatWidgets.isEmpty()) {
	    treatPad = 0;
	    return;
	}
	int innerW = Math.max(UI.scale(32), sz.x - Scrollbar.width);
	int x = TREAT_PAD;
	int y = TREAT_PAD;
	if(treatLabel != null) {
	    treatLabel.c = new Coord(TREAT_PAD, TREAT_PAD);
	    y = treatLabel.sz.y + TREAT_PAD + UI.scale(2);
	    x = TREAT_PAD;
	}
	int rowH = Inventory.sqsz.y;
	for(Widget w : treatWidgets) {
	    if(w == treatBar || w == treatLabel)
		continue;
	    if(x > TREAT_PAD && x + w.sz.x + TREAT_PAD > innerW) {
		x = TREAT_PAD;
		y += rowH + TREAT_GAP;
	    }
	    w.c = new Coord(x, y);
	    x += w.sz.x + TREAT_GAP;
	}
	treatPad = y + rowH + TREAT_PAD;
	int top = Math.max(0, sz.y - treatPad);
	if(treatBar != null) {
	    treatBar.c = new Coord(0, top);
	    treatBar.resize(new Coord(innerW, treatPad));
	}
	for(Widget w : treatWidgets) {
	    if(w == treatBar)
		continue;
	    w.c = new Coord(w.c.x, w.c.y + top);
	}
    }

    private static class TreatBar extends Widget {
	@Override
	public void draw(GOut g) {
	    g.chcolor(NStyle.infoBg);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	}
    }

    private static class TreatIcon extends Widget {
	private final String itemName;
	private Tex tex;
	private String lastTip;
	private Tex tipTex;

	TreatIcon(String itemName) {
	    super(Inventory.sqsz);
	    this.itemName = itemName;
	}

	@Override
	public void tick(double dt) {
	    if(tex == null) {
		try {
		    BufferedImage img = loadIconImage();
		    if(img != null)
			tex = new TexI(convolvedown(img, sz.sub(1, 1), iconfilter));
		} catch(Loading l) {
		}
	    }
	    super.tick(dt);
	}

	private BufferedImage loadIconImage() {
	    for(String path : WoundTreatments.iconResources(itemName)) {
		BufferedImage img = HarvestState.loadIcon(path, false);
		if(img != null)
		    return img;
	    }
	    for(RecipeIngredientCache.RecipeEntry entry : RecipeIngredientCache.findOutputRecipesForItem(itemName)) {
		BufferedImage img = HarvestState.loadIcon(entry.paginaResource, false);
		if(img != null)
		    return img;
	    }
	    return null;
	}

	@Override
	public void draw(GOut g) {
	    g.image(Inventory.invsq, Coord.z);
	    if(tex != null)
		g.aimage(tex, sz.div(2), 0.5, 0.5);
	}

	@Override
	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 1) {
		CraftRecipeLookup.showProducing(this, ev.c, itemName);
		return true;
	    }
	    return super.mousedown(ev);
	}

	@Override
	public Object tooltip(Coord c, Widget prev) {
	    String tip = CraftRecipeLookup.ingredientTooltip(itemName);
	    if(tip == null || tip.isEmpty())
		return null;
	    if(!tip.equals(lastTip) || tipTex == null) {
		lastTip = tip;
		tipTex = RichText.render(tip.replace("$", "$$"), 0).tex();
	    }
	    return tipTex;
	}
    }
}
