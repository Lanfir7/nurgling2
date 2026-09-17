package haven.res.ui.tt.cn;/* Preprocessed source code */

import haven.*;
import nurgling.NConfig;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.iteminfo.ItemOverlayRaster;

import java.awt.*;

/* >tt: CustName */
@FromResource(name = "ui/tt/cn", version = 4)
public class CustomName extends ItemInfo.Name implements GItem.OverlayInfo<Tex> {
    public float count = -1;
    
    // Cached settings
    private static ItemQualityOverlaySettings cachedSettings = null;
    private static long lastSettingsCheck = 0;
    private static final long SETTINGS_CHECK_INTERVAL = 200;
    private static long settingsVersion = 0;
    private static boolean forceRefresh = false;
    
    private Tex cachedOverlay = null;
    private long lastSettingsVersion = -1;
    private float lastCount = -1;

    public CustomName(Owner owner, String str) {
        super(owner, str);
        if (str.contains(" kg ")) {
            count = Float.parseFloat(str.substring(0, str.indexOf(" kg ")));
        }
        else if (str.contains(" l ")) {
            count = Float.parseFloat(str.substring(0, str.indexOf(" l ")));
        }
    }
    
    public static void invalidateCache() {
        forceRefresh = true;
        settingsVersion++;
    }
    
    private static ItemQualityOverlaySettings getSettings() {
        long now = System.currentTimeMillis();
        if (forceRefresh || cachedSettings == null || now - lastSettingsCheck > SETTINGS_CHECK_INTERVAL) {
            ItemQualityOverlaySettings newSettings = 
                (ItemQualityOverlaySettings) NConfig.get(NConfig.Key.volumeOverlay);
            if (newSettings == null) {
                newSettings = new ItemQualityOverlaySettings();
                newSettings.corner = ItemQualityOverlaySettings.Corner.TOP_LEFT;
                newSettings.defaultColor = new Color(65, 255, 115);
                newSettings.showBackground = true;
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

    @Override
    public Tex overlay() {
        if(count > 0) {
            ItemQualityOverlaySettings settings = getSettings();
            if (settings.hidden) {
                return null;
            }
            
            long currentVersion = settingsVersion;
            
            // Check cache
            if (cachedOverlay != null && lastSettingsVersion == currentVersion && Math.abs(lastCount - count) < 0.001f) {
                return cachedOverlay;
            }
            
            cachedOverlay = ItemOverlayRaster.tex(String.format("%.2f", count), settings.defaultColor, settings, Font.PLAIN);
            
            lastSettingsVersion = currentVersion;
            lastCount = count;
            return cachedOverlay;
        }
        return null;
    }

    @Override
    public void drawoverlay(GOut g, Tex data) {
        if (data != null) {
            ItemQualityOverlaySettings settings = getSettings();
            int pad = settings.showOutline ? settings.outlineWidth : 0;
            Coord pos;
            
            switch (settings.corner) {
                case TOP_LEFT:
                    pos = new Coord(-pad, -pad);
                    g.aimage(data, pos, 0, 0);
                    break;
                case TOP_RIGHT:
                    pos = new Coord(g.sz().x + pad, -pad);
                    g.aimage(data, pos, 1, 0);
                    break;
                case BOTTOM_LEFT:
                    pos = new Coord(-pad, g.sz().y + pad);
                    g.aimage(data, pos, 0, 1);
                    break;
                case BOTTOM_RIGHT:
                default:
                    pos = new Coord(g.sz().x + pad, g.sz().y + pad);
                    g.aimage(data, pos, 1, 1);
                    break;
            }
        }
    }
    
    @Override
    public boolean tick(double dt) {
        // Check if settings changed
        if (lastSettingsVersion != settingsVersion) {
            cachedOverlay = null;
            return false;
        }
        return true;
    }
}
