package haven;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class GobCheeseRackInfo extends GobInfo {

	private static final Map<String, Tex> stageTexCache = new HashMap<>();

    protected GobCheeseRackInfo(Gob owner) {
	super(owner);
    }

    @Override
	protected boolean enabled() {
		return OptWnd.showCheeseRacksTierTextCheckBox.a && !gob.isHidden;
	}

	@Override
	protected Tex render() {
		return null;
	}

	@Override
	public void ctick(double dt) {
		content();
	}

	@Override
    public void dispose() {
	super.dispose();
    }

	private void content() {
		for (Gob.Overlay ol : gob.ols) {
			if (!ol.getSprResName().startsWith("gfx/fx/eq")) {continue;}
			Sprite spr = Reflect.getFieldValue(ol.spr, "espr", Sprite.class);
			if (spr == null) {continue;}
			String name = spr.res.name;
			String text = null;
			if (name.startsWith("gfx/terobjs/items/cheesetray-")) {
				if (name.endsWith("curd"))
					text = "Curd";
				else
					text = "T" + name.substring(name.lastIndexOf("-") + 2);
			}
			if(text != null) {
				if (!stageTexCache.containsKey(text)) {
					Tex stageTex = combine(PUtils.strokeImg(Text.std.renderstroked(text, Color.white, Color.black).img));
					stageTexCache.put(text, stageTex);
					spr.setTex2d(stageTex);
				} else {
					spr.setTex2d(stageTexCache.get(text));
				}
			} else {
				spr.setTex2d(null);
			}
		}
	}

	private static Tex combine(BufferedImage... parts) {
		return new TexI(ItemInfo.catimgsh(UI.scale(3), 0, null, parts));
	}
}