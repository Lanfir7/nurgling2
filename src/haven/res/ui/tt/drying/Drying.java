package haven.res.ui.tt.drying;/* Preprocessed source code */
import haven.*;
import nurgling.NConfig;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.iteminfo.ItemOverlayRaster;
import nurgling.tools.TanningRemaining;

import java.awt.*;

/* >tt: haven.res.ui.tt.drying.Drying */
@haven.FromResource(name = "ui/tt/drying", version = 3)
public class Drying extends ItemInfo implements GItem.MeterInfo, GItem.OverlayInfo<Tex>  {
    public final double done;

    // Cached settings
    private static ItemQualityOverlaySettings cachedSettings = null;
    private static long lastSettingsCheck = 0;
    private static final long SETTINGS_CHECK_INTERVAL = 200;
    private static long settingsVersion = 0;
    private static boolean forceRefresh = false;
    
    private Tex cachedOverlay = null;
    private long lastSettingsVersion = -1;
    private String lastText = null;

    public Drying(Owner owner, double done) {
        super(owner);
        this.done = done;
    }

    public double meter() {
        return(done);
    }

    public static ItemInfo mkinfo(Owner owner, Object... args) {
        double done = ((Number)args[1]).doubleValue() / 100.0;
        return(new Drying(owner, done));
    }
    
    public static void invalidateCache() {
        forceRefresh = true;
        settingsVersion++;
    }
    
    private static ItemQualityOverlaySettings getSettings() {
        long now = System.currentTimeMillis();
        if (forceRefresh || cachedSettings == null || now - lastSettingsCheck > SETTINGS_CHECK_INTERVAL) {
            ItemQualityOverlaySettings newSettings = 
                (ItemQualityOverlaySettings) NConfig.get(NConfig.Key.progressOverlay);
            if (newSettings == null) {
                newSettings = new ItemQualityOverlaySettings();
                newSettings.corner = ItemQualityOverlaySettings.Corner.BOTTOM_LEFT;
                newSettings.defaultColor = new Color(234, 164, 101);
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

    public Tex overlay() {
        ItemQualityOverlaySettings settings = getSettings();
        if (settings.hidden) {
            return null;
        }
        
        String text = TanningRemaining.overlayText(meter(), windowCap());
        long currentVersion = settingsVersion;
        
        // Check cache
        if (cachedOverlay != null && lastSettingsVersion == currentVersion && text.equals(lastText)) {
            return cachedOverlay;
        }
        cachedOverlay = ItemOverlayRaster.tex(text, settings.defaultColor, settings, Font.PLAIN);
        
        lastSettingsVersion = currentVersion;
        lastText = text;
        return cachedOverlay;
    }

    private String windowCap() {
        if (!(owner instanceof GItem)) {
            return null;
        }
        WItem wi = ((GItem) owner).wi;
        if (wi == null) {
            return null;
        }
        haven.Window wnd = wi.getparent(haven.Window.class);
        return wnd != null ? wnd.cap : null;
    }

    public void drawoverlay(GOut g, Tex ol) {
        if (ol != null) {
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
        String text = TanningRemaining.overlayText(meter(), windowCap());
        if (lastSettingsVersion != settingsVersion) {
            cachedOverlay = null;
            return false;
        }
        if (!text.equals(lastText)) {
            lastText = text;
            cachedOverlay = null;
            return false;
        }
        return true;
    }
}
