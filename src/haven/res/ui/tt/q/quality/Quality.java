/* Preprocessed source code */
package haven.res.ui.tt.q.quality;

/* $use: ui/tt/q/qbuff */
import haven.*;
import haven.res.ui.tt.q.qbuff.*;

import java.awt.*;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.iteminfo.ItemOverlayRaster;

/* >tt: Quality */
@haven.FromResource(name = "ui/tt/q/quality", version = 28)
public class Quality extends QBuff implements GItem.OverlayInfo<Tex> {
    public static boolean show = Utils.getprefb("qtoggle", false);
    NGItem ownitem = null;
    boolean withContent = false;
    
    // Cached settings for performance
    private static ItemQualityOverlaySettings cachedSettings = null;
    private static long lastSettingsCheck = 0;
    private static final long SETTINGS_CHECK_INTERVAL = 200; // Check every 200ms
    private static long settingsVersion = 0; // Increment when settings change
    private static boolean forceRefresh = false; // Flag to force refresh all overlays
    
    public Quality(Owner owner, double q) {
        super(owner, Resource.classres(Quality.class).layer(Resource.imgc, 0).scaled(), "Quality", q);
        if (owner instanceof NGItem) {
            ownitem = (NGItem) owner;
            ownitem.quality = (float) q;
        }
    }

    @Override
    public int order() {
        return 101;
    }

    public static ItemInfo mkinfo(Owner owner, Object... args) {
        return(new Quality(owner, ((Number)args[1]).doubleValue()));
    }
    
    private static ItemQualityOverlaySettings getSettings() {
        long now = System.currentTimeMillis();
        if (cachedSettings == null || forceRefresh || now - lastSettingsCheck > SETTINGS_CHECK_INTERVAL) {
            Object settings = NConfig.get(NConfig.Key.itemQualityOverlay);
            ItemQualityOverlaySettings newSettings;
            if (settings instanceof ItemQualityOverlaySettings) {
                newSettings = (ItemQualityOverlaySettings) settings;
            } else {
                newSettings = new ItemQualityOverlaySettings();
            }
            // Check if settings reference changed (new settings object from save) or force refresh
            if (cachedSettings != newSettings || forceRefresh) {
                settingsVersion++;
                cachedSettings = newSettings;
                forceRefresh = false;
            }
            lastSettingsCheck = now;
        }
        return cachedSettings;
    }
    
    /**
     * Call this method to force all quality overlays to refresh with new settings
     */
    public static void invalidateCache() {
        forceRefresh = true;
        settingsVersion++;
    }
    
    public static long getSettingsVersion() {
        return settingsVersion;
    }
    
    public Tex overlay() {
        ItemQualityOverlaySettings settings = getSettings();
        double quality;
        Color color;
        if (ownitem != null && !ownitem.content().isEmpty()) {
            withContent = true;
            quality = ownitem.content().get(0).quality();
            color = settings.contentColor;
        } else {
            withContent = false;
            quality = q;
            color = settings.getColorForQuality(q);
        }
        return ItemOverlayRaster.tex(ItemOverlayRaster.qualityText(quality, settings), color, settings);
    }

    public void drawoverlay(GOut g, Tex ol) {
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

    private double lastQuality = -1.0;
    private boolean lastWithContent = false;
    private long lastSettingsVersion = -1;
    
    @Override
    public boolean tick(double dt) {
        // Check if quality or content state changed
        boolean currentWithContent = ownitem != null && !ownitem.content().isEmpty();
        double currentQuality = currentWithContent ? ownitem.content().get(0).quality() : q;
        
        // Force settings check to update version if needed
        getSettings();
        long currentVersion = settingsVersion;
        
        // Check if settings changed (by version number)
        boolean settingsChanged = lastSettingsVersion != currentVersion;
        
        if (lastQuality != currentQuality || lastWithContent != currentWithContent || settingsChanged) {
            lastQuality = currentQuality;
            lastWithContent = currentWithContent;
            lastSettingsVersion = currentVersion;
            return false; // Need to update overlay
        }
        return true; // No update needed
    }
}
