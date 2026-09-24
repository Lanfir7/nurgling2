/* Preprocessed source code */
package haven.res.ui.barterbox;

import haven.*;
import static haven.Inventory.invsq;
import static haven.Inventory.sqsz;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.*;

/* >wdg: haven.res.ui.barterbox.Shopbox */
@haven.FromResource(name = "ui/barterbox", version = 76)
public class Shopbox extends Widget implements ItemInfo.SpriteOwner, GSprite.Owner {
    public static final Text qlbl = Text.render("Quality:");
    public static final Text any = Text.render("Any");
    public static final Tex bg = Resource.classres(Shopbox.class).layer(Resource.imgc, 0).tex();
    /* Kept for callers which use the old resource-widget coordinates. */
    public static final Coord itemc = UI.scale(8, 16), buyc = UI.scale(110, 72),
            pricec = UI.scale(370, 16), qualc = UI.scale(666, 40),
            cbtnc = UI.scale(600, 72), spipec = UI.scale(230, 18),
            bpipec = UI.scale(474, 18), cntc = UI.scale(8, 78);
    public static final int MAXBUY = 500;
    public static final Coord BUYER_SIZE = UI.scale(740, 108);
    public static final Coord SELLER_SIZE = UI.scale(740, 108);
    private static final int[] QUICK_QUANTITIES = {5, 20};
    private static int lastqty = 1;
    private static final Color PANEL = new Color(28, 25, 22, 245);
    private static final Color CARD = new Color(51, 43, 35, 245);
    private static final Color BORDER = new Color(132, 104, 68, 220);
    private static final Color MUTED = new Color(190, 176, 153);
    private static final Text.Foundry titlef = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(14f))).aa(true);
    private static final Text.Foundry bodyf = new Text.Foundry(Text.serif.deriveFont(UI.scale(12f))).aa(true);

    public ResData res;
    public ItemSpec price;
    public Text num;
    public int leftNum;
    public int pnum, pq;
    public GSprite spr;
    public final boolean admin;
    private Text pnumt, pqt;
    private Object[] info = {};
    private Button spipe, bpipe, bbtn, cbtn;
    private Button[] quick = new Button[QUICK_QUANTITIES.length];
    private TextEntry pnume, pqe;
    private QuantityEntry cnte;
    private boolean stockKnown;
    private Text receiveText, payText, quantityText, sellerText, priceAmountText, qualityText;
    private Text stockText, costText, minimumQualityText;
    private RichText purchaseText;
    private RichText offerText, priceNameText;
    private Text lotText, offerQualityText;
    private String stockMessage = "", purchaseMessage = "", offerName = "", paymentName = "";
    private String offerQualityMessage = "", lotMessage = "", costMessage = "", minimumQualityMessage = "";
    private boolean offerSpriteReady, priceSpriteReady, offerIdentityReady, priceIdentityReady;
    private boolean purchaseControlsReady;

    public static Widget mkwidget(UI ui, Object... args) {return(new Shopbox((Integer)args[0] != 0));}

    public Shopbox(boolean admin) {
        super(admin ? SELLER_SIZE : BUYER_SIZE);
        this.admin = admin;
        refreshStaticText();
        cnte = add(new QuantityEntry(UI.scale(94), Integer.toString(lastqty)), cntc);
        cnte.canactivate = true; cnte.dshow = true; cnte.tooltip = BarterText.tip("quantity");
        bbtn = add(new Button(UI.scale(130), BarterText.text("buy"), false), buyc);
        bbtn.tooltip = BarterText.tip("custom_buy");
        for(int i = 0; i < quick.length; i++) {
            quick[i] = add(new Button(UI.scale(40), "", false), UI.scale(246 + (i * 46), 72));
            quick[i].tooltip = BarterText.tip("quick_buy");
        }
        if(admin) addSellerControls();
        updatePurchaseControls();
    }

    private void addSellerControls() {
        spipe = add(new Button(UI.scale(120), BarterText.text("connect_stock"), false), spipec);
        spipe.tint = new Color(255, 180, 150); spipe.tooltip = BarterText.tip("connect_stock");
        bpipe = add(new Button(UI.scale(115), BarterText.text("connect_payment"), false), bpipec);
        bpipe.tint = new Color(170, 235, 170); bpipe.tooltip = BarterText.tip("connect_payment");
        cbtn = add(new Button(UI.scale(120), BarterText.text("change_offer"), false), cbtnc);
        cbtn.tooltip = BarterText.tip("change_offer");
        pnume = add(new TextEntry(UI.scale(60), ""), UI.scale(666, 16));
        pnume.canactivate = true; pnume.dshow = true; pnume.tooltip = BarterText.tip("price_amount");
        pqe = add(new TextEntry(UI.scale(60), ""), qualc);
        pqe.canactivate = true; pqe.dshow = true; pqe.tooltip = BarterText.tip("min_quality");
    }

    private void refreshStaticText() {
        receiveText = bodyf.render(BarterText.text("receive"), new Color(255, 215, 135));
        payText = bodyf.render(BarterText.text("pay"), new Color(194, 235, 185));
        quantityText = bodyf.render(BarterText.text("choose_amount"), MUTED);
        sellerText = bodyf.render(BarterText.text("seller_short"), new Color(255, 215, 135));
        priceAmountText = bodyf.render(BarterText.text("seller_price"), MUTED);
        qualityText = bodyf.render(BarterText.text("seller_quality"), MUTED);
    }

    public abstract class AttrCache<T> {
        private List<ItemInfo> forinfo;
        private T save;
        public T get() {
            try {
                List<ItemInfo> now = info();
                if(now != forinfo) {save = find(now); forinfo = now;}
            } catch(Loading e) {return(null);}
            return(save);
        }
        protected abstract T find(List<ItemInfo> info);
    }

    public final AttrCache<Tex> itemnum = new AttrCache<Tex>() {
        protected Tex find(List<ItemInfo> info) {
            GItem.NumberInfo ninf = ItemInfo.find(GItem.NumberInfo.class, info);
            return((ninf == null) ? null : ninf.overlay());
        }
    };
    public final AttrCache<Text> itemlabel = new AttrCache<Text>() {
        protected Text find(List<ItemInfo> info) {
            ItemInfo.Name name = ItemInfo.find(ItemInfo.Name.class, info);
            if((name == null) || (name.str.text == null) || name.str.text.isEmpty()) return(null);
            haven.res.ui.tt.q.quality.Quality q = ItemInfo.find(haven.res.ui.tt.q.quality.Quality.class, info);
            return(titlef.render(OfferLabel.format(name.str.text, (q == null) ? null : q.q), new Color(255, 215, 135)));
        }
    };
    public final AttrCache<Integer> itemlot = new AttrCache<Integer>() {
        protected Integer find(List<ItemInfo> info) {
            GItem.Amount amount = ItemInfo.find(GItem.Amount.class, info);
            return((amount == null) ? null : amount.itemnum());
        }
    };

    public void draw(GOut g) {
        g.chcolor(PANEL); g.frect(Coord.z, sz); g.chcolor(BORDER); g.rect(Coord.z, sz.sub(Coord.of(1)));
        card(g, UI.scale(4, 0), UI.scale(354, 68));
        card(g, UI.scale(366, 0), UI.scale(370, 68));
        g.chcolor();
        drawOffer(g); drawPrice(g);
        if(admin) drawSeller(g);
        drawPurchase(g);
        g.chcolor(); super.draw(g);
    }

    private void card(GOut g, Coord c, Coord s) {g.chcolor(CARD); g.frect(c, s); g.chcolor(BORDER); g.rect(c, s.sub(Coord.of(1)));}
    private void drawOffer(GOut g) {
        g.image(receiveText.tex(), UI.scale(50, 0));
        drawItem(g, itemc, offerSprite(), itemnum.get(), false);
        ensureOfferText();
        int nameY = nameBlockY(offerText);
        if(offerText != null) g.reclip(new Coord(UI.scale(50), nameY),
                UI.scale(admin ? 174 : 300, 30)).image(offerText.tex(), Coord.z);
        if(offerQualityText != null) g.reclip(new Coord(UI.scale(50), detailY(offerText)), UI.scale(110, 16)).image(offerQualityText.tex(), Coord.z);
        if(lotText != null) g.reclip(UI.scale(165, 48), UI.scale(80, 16)).image(lotText.tex(), Coord.z);
        if(stockText != null) g.reclip(UI.scale(250, 48), UI.scale(104, 16)).image(stockText.tex(), Coord.z);
    }
    private int nameBlockY(RichText name) {
        int height = name == null ? UI.scale(15) : Math.min(name.sz().y, UI.scale(30));
        return Math.max(UI.scale(17), itemc.y + (sqsz.y - height - UI.scale(16)) / 2);
    }
    private int detailY(RichText name) {
        return nameBlockY(name) + (name == null ? UI.scale(15) : Math.min(name.sz().y, UI.scale(30)));
    }
    private void drawPrice(GOut g) {
        g.image(payText.tex(), UI.scale(410, 0));
        if(price == null) {if(costText != null) g.image(costText.tex(), UI.scale(410, 42)); return;}
        drawItem(g, pricec, null, null, true);
        ensurePriceText();
        if(priceNameText != null) g.reclip(new Coord(UI.scale(410), nameBlockY(priceNameText)),
                UI.scale(admin ? 60 : 320, 30)).image(priceNameText.tex(), Coord.z);
        if(!admin && costText != null) g.reclip(new Coord(UI.scale(410), detailY(priceNameText)), UI.scale(150, 16)).image(costText.tex(), Coord.z);
        if(!admin && minimumQualityText != null) g.reclip(UI.scale(565, 48), UI.scale(166, 16)).image(minimumQualityText.tex(), Coord.z);
    }
    private void drawItem(GOut g, Coord c, GSprite offered, Tex overlay, boolean payment) {
        GOut sg = g.reclip(c, invsq.sz()); sg.image(invsq, Coord.z);
        try {
            GSprite sprite = (offered != null) ? offered : ((payment && (price != null)) ? price.spr() : null);
            if(sprite != null) sprite.draw(sg); else sg.image(WItem.missing.layer(Resource.imgc).tex(), Coord.z, sqsz);
        } catch(Loading l) {sg.image(WItem.missing.layer(Resource.imgc).tex(), Coord.z, sqsz);}
        if(overlay != null) sg.aimage(overlay, sqsz, 1, 1);
    }
    private void drawPurchase(GOut g) {
        if(purchaseText != null) {
            int height = Math.min(purchaseText.sz().y, bbtn.sz.y);
            g.reclip(new Coord(UI.scale(348), bbtn.c.y + (bbtn.sz.y - height) / 2),
                    new Coord(UI.scale(240), height)).image(purchaseText.tex(), Coord.z);
        }
    }
    private void drawSeller(GOut g) {
        g.reclip(UI.scale(610, 19), UI.scale(52, 16)).image(priceAmountText.tex(), Coord.z);
        g.reclip(UI.scale(610, 43), UI.scale(52, 16)).image(qualityText.tex(), Coord.z);
    }
    private void ensureOfferText() {
        if(offerText != null) return;
        try {
            ItemInfo.Name name = ItemInfo.find(ItemInfo.Name.class, info());
            if((name == null) || (name.str.text == null) || name.str.text.isEmpty()) return;
            offerName = name.str.text;
            haven.res.ui.tt.q.quality.Quality quality = ItemInfo.find(haven.res.ui.tt.q.quality.Quality.class, info());
            offerQualityMessage = (quality == null) ? BarterText.text("quality_unknown") : BarterText.quality(quality.q);
            offerQualityText = bodyf.render(offerQualityMessage, MUTED);
            offerIdentityReady = true;
            offerText = RichText.render(RichText.Parser.quote(offerName), UI.scale(admin ? 174 : 300),
                    TextAttribute.FAMILY, Text.serif.getFamily(), TextAttribute.SIZE, UI.scale(13f),
                    TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, TextAttribute.FOREGROUND, new Color(255, 215, 135));
            reflowForNames();
        } catch(Loading l) {}
    }
    private void ensurePriceText() {
        if((priceNameText != null) || (price == null)) return;
        try {
            paymentName = price.name();
            priceIdentityReady = true;
            priceNameText = RichText.render(RichText.Parser.quote(paymentName), UI.scale(admin ? 60 : 320),
                    TextAttribute.FAMILY, Text.serif.getFamily(), TextAttribute.SIZE, UI.scale(13f),
                    TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, TextAttribute.FOREGROUND, new Color(220, 239, 210));
            reflowForNames();
        }
        catch(Loading l) {}
    }
    private GSprite offerSprite() {
        if((spr == null) && (res != null)) try {
            spr = GSprite.create(this, res.res.get(), res.sdt.clone());
        } catch(Loading l) {}
        offerSpriteReady = (spr != null);
        return(spr);
    }
    private void reflowForNames() {
        /* Compact barter rows deliberately reserve two name lines and do not grow. */
    }
    private boolean ensureResolved() {
        boolean oldOfferSprite = offerSpriteReady, oldOfferIdentity = offerIdentityReady;
        boolean oldPriceSprite = priceSpriteReady, oldPriceIdentity = priceIdentityReady;
        if(!offerSpriteReady) offerSprite();
        if(!offerIdentityReady) ensureOfferText();
        if(!priceIdentityReady) ensurePriceText();
        boolean resolvedPriceSprite = false;
        if((price != null) && !priceSpriteReady) try {price.spr(); resolvedPriceSprite = true;} catch(Loading l) {}
        priceSpriteReady = BarterPurchase.priceSpriteReady(price != null, priceSpriteReady, resolvedPriceSprite);
        return((oldOfferSprite != offerSpriteReady) || (oldOfferIdentity != offerIdentityReady) || (oldPriceSprite != priceSpriteReady) || (oldPriceIdentity != priceIdentityReady));
    }
    private boolean isTradeReady() {
        return((res != null) && (price != null) && (pnum > 0) && stockKnown && (leftNum > 0) && offerSpriteReady && priceSpriteReady && offerIdentityReady && priceIdentityReady);
    }
    private String readinessReason() {
        if(!stockKnown) return(BarterText.text("stock_loading"));
        if(leftNum < 1) return(BarterText.text("sold_out"));
        if((res == null) || !offerSpriteReady || !offerIdentityReady) return(BarterText.text("offer_loading"));
        if((price == null) || !priceSpriteReady || !priceIdentityReady || (pnum < 1)) return(BarterText.text("payment_loading"));
        return("");
    }
    public void tick(double dt) {
        super.tick(dt);
        if(ensureResolved() || purchaseControlsReady != isTradeReady()) updatePurchaseControls();
    }

    private List<ItemInfo> cinfo;
    public List<ItemInfo> info() {
        if(ui == null) return(Collections.emptyList());
        if(cinfo == null) cinfo = ItemInfo.buildinfo(this, info);
        return(cinfo);
    }
    public class IconTip implements Indir<Tex>, ItemInfo.InfoTip {
        private final Tex tex; private IconTip(BufferedImage img) {tex = new TexI(img);}
        public List<ItemInfo> info() {return(Shopbox.this.info());} public Tex get() {return(tex);}
    }
    private Object longtip; private Tex pricetip;
    private static BufferedImage withResourcePath(BufferedImage tip, Resource resource) {
        BufferedImage path = RichText.render("$col[128,128,128]{$size[9]{" +
                RichText.Parser.quote(resource.name) + "}}", 0).img;
        return ItemInfo.catimgs(UI.scale(3), tip, path);
    }
    public Object tooltip(Coord c, Widget prev) {
        if(c.isect(itemc, sqsz) && (res != null)) try {
            if(longtip == null) {
                BufferedImage ti = ItemInfo.longtip(info()); Resource.Pagina pg = res.res.get().layer(Resource.pagina);
                if(pg != null) ti = ItemInfo.catimgs(0, ti, RichText.render("\n" + pg.text, UI.scale(200)).img);
                longtip = new IconTip(withResourcePath(ti, res.res.get()));
            }
            return(longtip);
        } catch(Loading l) {return("...");}
        if(c.isect(pricec, sqsz) && (price != null)) try {
            if(pricetip == null) pricetip = new TexI(withResourcePath(ItemInfo.longtip(price.info()), price.resource()));
            return(pricetip);
        } catch(Loading l) {return("...");}
        if(c.isect(new Coord(UI.scale(50), nameBlockY(offerText)), new Coord(UI.scale(admin ? 174 : 300), detailY(offerText) - nameBlockY(offerText))) && !offerName.isEmpty()) return(offerName);
        if(c.isect(new Coord(UI.scale(50), detailY(offerText)), UI.scale(110, 16))) return(offerQualityMessage);
        if(c.isect(UI.scale(162, 48), UI.scale(85, 16))) return(lotMessage);
        if(c.isect(UI.scale(248, 48), UI.scale(105, 16))) return(stockMessage);
        if(c.isect(new Coord(UI.scale(410), nameBlockY(priceNameText)), new Coord(UI.scale(admin ? 60 : 320), detailY(priceNameText) - nameBlockY(priceNameText))) && !paymentName.isEmpty()) return(paymentName);
        if(!admin && c.isect(new Coord(UI.scale(410), detailY(priceNameText)), UI.scale(150, 16))) return(costMessage);
        if(!admin && c.isect(UI.scale(562, 48), UI.scale(170, 16))) return(minimumQualityMessage);
        if(c.isect(UI.scale(348, 72), UI.scale(240, 32))) return(purchaseMessage);
        if(admin && c.isect(UI.scale(610, 19), UI.scale(52, 16))) return(BarterText.tip("price_amount"));
        if(admin && c.isect(UI.scale(610, 43), UI.scale(52, 16))) return(BarterText.tip("min_quality"));
        return(super.tooltip(c, prev));
    }

    public <C> C context(Class<C> cl) {return(OwnerContext.uictx.context(cl, ui));}
    @Deprecated public Glob glob() {return(ui.sess.glob);}
    public Resource resource() {return(res.res.get());}
    public GSprite sprite() {if(spr == null) throw(new Loading("Still waiting for sprite to be constructed")); return(spr);}
    public Resource getres() {return(res.res.get());}
    private Random rnd;
    public Random mkrandoom() {return((rnd == null) ? (rnd = new Random()) : rnd);}
    private static Integer parsenum(TextEntry e) {try {return(e.text().equals("") ? 0 : Integer.parseInt(e.text()));} catch(NumberFormatException exc) {return(null);}}
    public boolean mousedown(MouseDownEvent ev) {
        if((ev.b == 3) && ev.c.isect(pricec, sqsz) && (price != null)) {wdgmsg("pclear"); return(true);}
        return(super.mousedown(ev));
    }
    private int lotSize() {
        Integer lot = itemlot.get();
        return((lot == null) || (lot < 1) ? 1 : lot);
    }
    private QuantityState quantityState() {return(BarterPurchase.quantityState(parsenum(cnte), stockKnown, leftNum, MAXBUY, pnum, lotSize()));}
    private void buy(int n) {
        n = BarterPurchase.emittedBuyCount(n, stockKnown, leftNum, MAXBUY, isTradeReady(), true, pnum);
        if(n < 1) return;
        lastqty = n;
        for(int i = 0; i < n; i++) wdgmsg("buy");
    }
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(sender == bbtn || sender == cnte) {Integer n = parsenum(cnte); if(n != null) buy(n);}
        else if(sender == quick[0]) buy(quickCount(QUICK_QUANTITIES[0])); else if(sender == quick[1]) buy(quickCount(QUICK_QUANTITIES[1]));
        else if(sender == spipe) wdgmsg("spipe"); else if(sender == bpipe) wdgmsg("bpipe"); else if(sender == cbtn) wdgmsg("change");
        else if((sender == pnume) || (sender == pqe)) wdgmsg("price", parsenum(pnume), parsenum(pqe));
        else super.wdgmsg(sender, msg, args);
    }
    private int quickCount(int requested) {return(stockKnown ? Math.min(requested, leftNum) : requested);}
    private void updatePurchaseControls() {
        QuantityState state = quantityState();
        boolean ready = isTradeReady();
        purchaseControlsReady = ready;
        stockText = bodyf.render(stockKnown ? ((leftNum > 0) ? BarterText.stock(leftNum) : BarterText.text("sold_out")) : BarterText.text("stock_loading"), (stockKnown && leftNum < 1) ? new Color(235, 125, 110) : MUTED);
        stockMessage = stockKnown ? ((leftNum > 0) ? BarterText.stock(leftNum) : BarterText.text("sold_out")) : BarterText.text("stock_loading");
        Integer lot = itemlot.get();
        lotText = (lot == null || lot <= 1) ? null : bodyf.render(BarterText.lotSize(lot), MUTED);
        lotMessage = (lot == null || lot <= 1) ? "" : BarterText.lotSize(lot);
        if(price == null) {costMessage = BarterText.text("payment_loading"); costText = bodyf.render(costMessage, MUTED);}
        cnte.show((res != null) && (price != null) && (pnum > 0));
        purchaseMessage = ready ? state.message : readinessReason();
        String compactStatus = (ready && state.valid) ? BarterText.compactTotal(state.quantity, pnum, lotSize()) : purchaseMessage;
        purchaseText = RichText.render(RichText.Parser.quote(compactStatus), UI.scale(240),
                TextAttribute.FAMILY, Text.serif.getFamily(), TextAttribute.SIZE, UI.scale(11f),
                TextAttribute.FOREGROUND, (ready && state.valid) ? MUTED : new Color(235, 125, 110));
        bbtn.disable(!ready || !state.valid); bbtn.change(BarterText.text("buy"));
        bbtn.tooltip = purchaseMessage;
        cnte.tooltip = (!ready || !state.valid) ? purchaseMessage : BarterText.tip("quantity");
        int[] requested = QUICK_QUANTITIES;
        for(int i = 0; i < quick.length; i++) {
            int actual = BarterPurchase.emittedBuyCount(requested[i], stockKnown, leftNum, MAXBUY, ready, true, pnum);
            quick[i].show(true); quick[i].disable(actual < 1);
            quick[i].change(BarterText.quickLabel(requested[i]));
            quick[i].tooltip = (actual < 1) ? readinessReason() : BarterText.quickTip(actual, pnum);
        }
    }
    public void uimsg(String name, Object... args) {
        if(name == "res") {
            res = null; spr = null; offerText = null; offerQualityText = null; offerName = ""; offerQualityMessage = "";
            offerSpriteReady = false; offerIdentityReady = false;
            stockKnown = false; leftNum = 0; num = null;
            info = new Object[0]; cinfo = null; longtip = null;
            if(args.length > 0) {ResData data = new ResData(ui.sess.getresv(args[0]), Message.nil); if(args.length > 1) data.sdt = new MessageBuf((byte[])args[1]); res = data;}
            reflowForNames();
        } else if(name == "tt") {info = args; cinfo = null; longtip = null; offerText = null; offerQualityText = null; offerName = ""; offerQualityMessage = ""; offerIdentityReady = false; reflowForNames();
        } else if(name == "n") {leftNum = Utils.iv(args[0]); stockKnown = true; num = Text.render(BarterText.stock(leftNum));
        } else if(name == "price") {
            int a = 0;
            if(args[a] == null) {a++; price = null;} else {
                Indir<Resource> pres = ui.sess.getresv(args[a++]); Message sdt = Message.nil;
                if(args[a] instanceof byte[]) sdt = new MessageBuf((byte[])args[a++]); Object[] pinfo = null;
                if(args[a] instanceof Object[]) {pinfo = new Object[0][]; while(args[a] instanceof Object[]) pinfo = Utils.extend(pinfo, args[a++]);}
                price = new ItemSpec(uictx.curry(ui), new ResData(pres, sdt), pinfo);
            }
            pricetip = null; priceNameText = null; paymentName = ""; priceSpriteReady = false; priceIdentityReady = false; pnum = Utils.iv(args[a++]); pq = Utils.iv(args[a++]);
            reflowForNames();
            costMessage = (price == null) ? BarterText.text("payment_loading") : BarterText.costUnit(pnum);
            minimumQualityMessage = (price == null) ? "" : BarterText.minimumQuality(pq);
            costText = bodyf.render(costMessage, MUTED);
            minimumQualityText = (price == null) ? null : bodyf.render(minimumQualityMessage, MUTED);
            if(admin) {pnume.settext((pnum > 0) ? Integer.toString(pnum) : ""); pnume.commit(); pqe.settext((pq > 0) ? Integer.toString(pq) : ""); pqe.commit();}
            else {pnumt = (pnum > 0) ? Text.render("×" + pnum) : null; pqt = (pq > 0) ? Text.render(pq + "+") : any;}
        } else {super.uimsg(name, args); return;}
        updatePurchaseControls();
    }
    private class QuantityEntry extends TextEntry {QuantityEntry(int w, String text) {super(w, text);} protected void changed() {updatePurchaseControls();}}
    public static class ShopItem {public GSprite spr; public String name; public ShopItem(GSprite res, String name) {this.spr = res; this.name = name;}}
    public ShopItem getPrice() {
        if(price == null) return(null);
        return(new ShopItem(price.spr(), price.name()));
    }
    public ShopItem getOffer() {
        if(res == null) return(null);
        GSprite offer = offerSprite();
        if(offer == null) return(null);
        ItemInfo.Name nm = ItemInfo.find(ItemInfo.Name.class, info());
        return((nm == null) ? null : new ShopItem(offer, nm.str.text));
    }
}

class OfferLabel {static String format(String name, Double quality) {return((quality == null) ? name : String.format(Locale.ROOT, "%s (Q %.1f)", name, quality));}}
class OfferLayout {
    static Coord identityOrigin(Coord itemOrigin, Coord itemSize, int gap) {return(new Coord(itemOrigin.x + itemSize.x + gap, itemOrigin.y));}
    static int actionY(int rowHeight, int buttonHeight, int inset) {return(rowHeight - buttonHeight - inset);}
    static int purchaseCountTop(int actionY, int gap, int countHeight) {return(actionY - gap - countHeight);}
}
