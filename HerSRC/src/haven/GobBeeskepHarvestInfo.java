package haven;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class GobBeeskepHarvestInfo extends GobInfo {

	private static final BufferedImage waxImage = PUtils.convolvedown(Resource.local().loadwait("customclient/wax").layer(Resource.imgc).img, UI.scale(26, 26), CharWnd.iconfilter);
	private static final BufferedImage honeyImage = PUtils.convolvedown(Resource.local().loadwait("customclient/honey").layer(Resource.imgc).img, UI.scale(26, 26), CharWnd.iconfilter);
	private static final Map<String, Tex> contentTexCache = new HashMap<>();

    protected GobBeeskepHarvestInfo(Gob owner) {
	super(owner);
    }

    @Override
	protected boolean enabled() {
		return OptWnd.showBeeSkepsHarvestIconsCheckBox.a && !gob.isHidden;
	}

	@Override
	protected Tex render() {
	up(2);
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

		if (d != null && "gfx/terobjs/beehive".equals(resName)) {
			int rbuf = d.sdt.checkrbuf(0);
			String key = null;

			if (rbuf == 7 || rbuf == 15) {
				key = "both";
			} else if (rbuf == 6 || rbuf == 14) {
				key = "wax";
			} else if (rbuf == 3 || rbuf == 11) {
				key = "honey";
			} else {
				return null;
			}

			// Check cache before doing any processing
			Tex cachedTex = contentTexCache.get(key);
			if (cachedTex != null) {
				return cachedTex;
			}

			// Build parts only if needed
			BufferedImage[] parts = null;
			switch (key) {
				case "both":
					parts = new BufferedImage[]{waxImage, honeyImage};
					break;
				case "wax":
					parts = new BufferedImage[]{waxImage};
					break;
				case "honey":
					parts = new BufferedImage[]{honeyImage};
					break;
			}

			// Validate that none of the parts are null
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