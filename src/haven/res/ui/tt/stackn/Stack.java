package haven.res.ui.tt.stackn;/* Preprocessed source code */
import haven.*;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.iteminfo.ItemOverlayRaster;

/* >tt: Stack */
@FromResource(name = "ui/tt/stackn", version = 3)
public class Stack extends ItemInfo.Name implements GItem.OverlayInfo<Tex> {
	public static boolean show = true;
	
	// Cached settings for performance
	private static ItemQualityOverlaySettings cachedSettings = null;
	private static long lastSettingsCheck = 0;
	private static final long SETTINGS_CHECK_INTERVAL = 200;
	private static long settingsVersion = 0;
	private static boolean forceRefresh = false;
	
	public Stack(Owner owner, String str) {
		super(owner, str);
	}

	public double quality = 0;
	private double lastQuality = -1;
	private long lastSettingsVersion = -1;
	private Tex cachedOverlay = null;
	
	public static void invalidateCache() {
		forceRefresh = true;
		settingsVersion++;
	}
	
	private static ItemQualityOverlaySettings getSettings() {
		long now = System.currentTimeMillis();
		if (forceRefresh || cachedSettings == null || now - lastSettingsCheck > SETTINGS_CHECK_INTERVAL) {
			ItemQualityOverlaySettings newSettings = (ItemQualityOverlaySettings) NConfig.get(NConfig.Key.stackQualityOverlay);
			if (newSettings == null) {
				newSettings = new ItemQualityOverlaySettings();
				newSettings.corner = ItemQualityOverlaySettings.Corner.TOP_LEFT;
			}
			if (cachedSettings != newSettings || forceRefresh) {
				cachedSettings = newSettings;
				settingsVersion++;
				forceRefresh = false;
			}
			lastSettingsCheck = now;
		}
		return cachedSettings;
	}

	public Tex overlay() {
		int count = 0;
		double q = 0;
		NGItem item = (NGItem)owner;
		if(item.contents != null) {
			for (Widget ch : item.contents.children()) {
				if (ch instanceof NGItem) {
					if(((NGItem)ch).quality == null)
						return null;
					q+=((NGItem)ch).quality;
					count++;
				}
			}
		}
		if(count>0) {
			q = q / count;
			quality = q;
			
			// Check if we need to rebuild the overlay
			long currentVersion = settingsVersion;
			if (cachedOverlay != null && Double.compare(lastQuality, q) == 0 && lastSettingsVersion == currentVersion) {
				return cachedOverlay;
			}
			
			ItemQualityOverlaySettings settings = getSettings();
			cachedOverlay = ItemOverlayRaster.tex(ItemOverlayRaster.qualityText(q, settings), settings.getColorForQuality(q), settings);
			
			lastQuality = q;
			lastSettingsVersion = currentVersion;
			return cachedOverlay;
		}
		return null;
	}

	public void drawoverlay(GOut g, Tex ol) {
		if(show && ol!=null) {
			ItemQualityOverlaySettings settings = getSettings();
			int pad = settings.showOutline ? settings.outlineWidth : 0;
			Coord pos;
			
			switch (settings.corner) {
				case TOP_LEFT:
					pos = new Coord(-pad, -pad);
					g.aimage(ol, pos, 0, 0);
					break;
				case TOP_RIGHT:
					pos = new Coord(g.sz().x + pad, -pad);
					g.aimage(ol, pos, 1, 0);
					break;
				case BOTTOM_LEFT:
					pos = new Coord(-pad, g.sz().y + pad);
					g.aimage(ol, pos, 0, 1);
					break;
				case BOTTOM_RIGHT:
				default:
					pos = new Coord(g.sz().x + pad, g.sz().y + pad);
					g.aimage(ol, pos, 1, 1);
					break;
			}
		}
	}

	@Override
	public boolean tick(double dt) {
		double q = 0;
		int count = 0;
		NGItem item = (NGItem)owner;
		if(item.contents != null) {
			for (Widget ch : item.contents.children()) {
				if (ch instanceof NGItem) {
					if(((NGItem)ch).quality == null)
						break;
					q+=((NGItem)ch).quality;
					count++;
				}
			}
		}
		
		// Also check if settings changed
		long currentVersion = settingsVersion;
		if (lastSettingsVersion != currentVersion) {
			cachedOverlay = null;
			return false; // Force redraw
		}

		return Double.compare(quality,q/count) == 0;
	}
}
