package haven;

import haven.resutil.Curiosity;

import java.awt.*;

public class MiniStudy extends GameUI.Hidewnd {
    InventoryProxy study;
    StudyInfo info;


    MiniStudy() {
	super(Coord.z, "Mini-Study");
    }


    public void setStudy(Inventory inventory) {
	if(study != null) {
	    study.reqdestroy();
	    info.reqdestroy();
	}
	study = add(new InventoryProxy(inventory));
	info = add(new StudyInfo(new Coord(study.sz.x, UI.scale(66)), inventory), 0, study.c.y + study.sz.y + UI.scale(5));
	pack();
    }

    private static class StudyInfo extends Widget {
	public Widget study;
	public int texp, tw, tenc, tlph;
	private final Text.UText<?> texpt = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (texp);}

	    public String text(Integer v) {return (Utils.thformat(v));}
	};
	private final Text.UText<?> twt = new Text.UText<String>(Text.std) {
	    public String value() {return (tw + "/" + ui.sess.glob.getcattr("int").comp);}
	};
	private final Text.UText<?> tenct = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (tenc);}

	    public String text(Integer v) {return (Integer.toString(tenc));}
	};
	private final Text.UText<?> tlpht = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (tlph);}
	
	    public String text(Integer v) {return (Utils.thformat(v));}
	};

	private StudyInfo(Coord sz, Widget study) {
	    super(sz);
	    this.study = study;
		Widget plbl, pval;
		plbl = add(new Label("Attention:"), UI.scale(2, -1));
		pval = adda(new CharWnd.RLabel<Pair<Integer, Integer>>(() -> new Pair<>(tw, (ui == null) ? 0 : ui.sess.glob.getcattr("int").comp),
						n -> String.format("%,d/%,d", n.a, n.b),
						new Color(255, 192, 255, 255)),
				plbl.pos("ur").adds(0, 0).x(sz.x - UI.scale(2)), 1.0, 0.0);
		plbl = add(new Label("Experience cost:"), pval.pos("bl").adds(0, -1).xs(2));
		pval = adda(new CharWnd.RLabel<Integer>(() -> tenc, Utils::thformat, new Color(255, 255, 192, 255)), plbl.pos("ur").adds(0, 0).x(sz.x - UI.scale(2)), 1.0, 0.0);
		plbl = add(new Label("Learning points:"), pval.pos("bl").adds(0, -1).xs(2));
		pval = adda(new CharWnd.RLabel<Integer>(() -> texp, Utils::thformat, new Color(192, 192, 255, 255)), plbl.pos("ur").adds(0, 0).x(sz.x - UI.scale(2)), 1.0, 0.0);
		plbl = add(new Label("LP/Hour:"), pval.pos("bl").adds(0, -1).xs(2));
		pval = adda(new CharWnd.RLabel<Integer>(() -> tlph, Utils::thformat, new Color(192, 255, 255, 255)), plbl.pos("ur").adds(0, 0).x(sz.x - UI.scale(2)), 1.0, 0.0);
	}

	private void upd() {
		int texp = 0, tw = 0, tenc = 0,  tlph = 0;
		for(GItem item : study.children(GItem.class)) {
			try {
				Curiosity ci = ItemInfo.find(Curiosity.class, item.info());
				if(ci != null) {
					texp += ci.exp;
					tw += ci.mw;
					tenc += ci.enc;
					tlph += ci.lph;
				}
			} catch(Loading l) {
			}
		}
		this.texp = texp; this.tw = tw; this.tenc = tenc; this.tlph = tlph;
	}

	public void tick(double dt) {
		upd();
		super.tick(dt);
	}
    }

	@Override
	public void wdgmsg(Widget sender, String msg, Object... args) {
		if((sender == this) && msg.equals("close")) {
			Utils.setprefc("wndc-miniStudy", this.c);
		}
		super.wdgmsg(sender, msg, args);
	}
}
