package haven;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GobFoodWaterInfo extends GobInfo {

	public static BufferedImage lowFoodImage = PUtils.convolvedown(Resource.local().loadwait("customclient/lowFood").layer(Resource.imgc).img, UI.scale(34, 34), CharWnd.iconfilter);
	public static BufferedImage lowWaterImage = PUtils.convolvedown(Resource.local().loadwait("customclient/lowWater").layer(Resource.imgc).img, UI.scale(34, 34), CharWnd.iconfilter);
	private static final Map<String, Tex> contentTexCache = new HashMap<>();

    protected GobFoodWaterInfo(Gob owner) {
	super(owner);
    }

    @Override
	protected boolean enabled() {
		return OptWnd.showLowFoodWaterIconsCheckBox.a && !gob.isHidden;
	}

	@Override
	protected Tex render() {
	up(6);
	if(gob == null || gob.getres() == null) { return null;}
		if (icons() != null)
			return icons();
		return null;
	}

	@Override
    public void dispose() {
	super.dispose();
    }

	private Tex icons() {
		Drawable dr = gob.getattr(Drawable.class);
		ResDrawable d = (dr instanceof ResDrawable) ? (ResDrawable) dr : null;
		String resName = gob.getres().name;
		if (d != null) {
			int rbuf = d.sdt.checkrbuf(0);
			String key = null;
			if (resName.equals("gfx/terobjs/chickencoop")) {
				if (rbuf == 0) {
					key = "both";
				} else if (rbuf == 1) {
					key = "food";
				} else if (rbuf == 2) {
					key = "water";
				} else {
					return null;
				}
			} else if (resName.equals("gfx/terobjs/rabbithutch")) {
				if (rbuf == 65 || rbuf == 66 || rbuf == 73 || rbuf == 74) {
					key = "both";
				} else if (rbuf == 69 || rbuf == 70 || rbuf == 77 || rbuf == 78) {
					key = "food";
				} else if (rbuf == 121 || rbuf == 122 || rbuf == 89 || rbuf == 90) {
					key = "water";
				} else {
					return null;
				}
			} else {
				return null;
			}
			// Check cache first
			Tex cachedTex = contentTexCache.get(key);
			if (cachedTex != null) {
				return cachedTex;
			}
			// Build parts
			BufferedImage[] parts = null;
			switch (key) {
				case "both":
					parts = new BufferedImage[]{lowFoodImage, lowWaterImage};
					break;
				case "food":
					parts = new BufferedImage[]{lowFoodImage};
					break;
				case "water":
					parts = new BufferedImage[]{lowWaterImage};
					break;
			}
			// Validate parts
			for (BufferedImage part : parts) {
				if (part == null) return null;
			}
			Tex contentTex = new TexI(ItemInfo.catimgs(1, parts));
			contentTexCache.put(key, contentTex);
			return contentTex;
		}
		return null;
	}

}