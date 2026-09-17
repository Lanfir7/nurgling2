package haven.res.ui.tt.expire;/* Preprocessed source code */
import haven.*;
import nurgling.NConfig;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.iteminfo.ItemOverlayRaster;

import java.awt.*;

/* >tt: haven.res.ui.tt.expire.Expiring */
@haven.FromResource(name = "ui/tt/expire", version = 6)
public class Expiring extends ItemInfo implements GItem.MeterInfo, GItem.OverlayInfo<Tex> {
    public final double stime, etime;
    public final Glob glob;
    
    private Tex cachedOverlay = null;
    private long lastSettingsVersion = -1;
    private int lastPercent = -1;

    public Expiring(Owner owner, double stime, double etime) {
        super(owner);
        this.stime = stime;
        this.etime = etime;
        this.glob = owner.context(Glob.class);
    }

    public static Expiring mkinfo(Owner owner, Object... args) {
        double stime = ((Number) args[1]).doubleValue();
        double etime = ((Number) args[2]).doubleValue();
        return (new Expiring(owner, stime, etime));
    }

    public double meter() {
        return (Utils.clip((glob.globtime() - stime) / (etime - stime), 0.0, 1.0));
    }
    
    private static ItemQualityOverlaySettings getSettings() {
        // Reuse Drying's cached settings since they use the same config
        ItemQualityOverlaySettings settings = 
            (ItemQualityOverlaySettings) NConfig.get(NConfig.Key.progressOverlay);
        if (settings == null) {
            settings = new ItemQualityOverlaySettings();
            settings.corner = ItemQualityOverlaySettings.Corner.BOTTOM_LEFT;
            settings.defaultColor = new Color(234, 164, 101);
            settings.showBackground = true;
        }
        return settings;
    }

    public Tex overlay() {
        ItemQualityOverlaySettings settings = getSettings();
        if (settings.hidden) {
            return null;
        }
        
        int currentPercent = (int)(meter() * 100);
        return ItemOverlayRaster.tex(currentPercent + "%", settings.defaultColor, settings, Font.PLAIN);
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
        int currentPercent = (int)(meter() * 100);
        if (lastPercent != currentPercent) {
            lastPercent = currentPercent;
            return false; // Need to update overlay
        }
        return true; // No update needed
    }
}
