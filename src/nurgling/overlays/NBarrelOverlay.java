package nurgling.overlays;

import haven.*;
import nurgling.NConfig;
import nurgling.conf.FontSettings;
import nurgling.widgets.nsettings.Fonts;

import java.awt.*;

public class NBarrelOverlay extends NObjectTexLabel
{
    String text = null;


    private static Text.Furnace active_title;

    private static Text.Furnace titleFurnace() {
        if (active_title == null)
            active_title = new PUtils.BlurFurn(((FontSettings) NConfig.get(NConfig.Key.fonts)).getFoundary(Fonts.FontType.BARRELS).aa(true), 2, 1, new Color(36, 25, 25));
        return active_title;
    }

    Gob gob;
    public NBarrelOverlay(Owner owner)
    {
        super(owner);
        gob = (Gob) owner;
        pos = new Coord3f(0,0,9);
    }

    @Override
    public boolean tick(double dt)
    {
        // Check if persistent barrel labels are enabled
        Boolean persistentLabels = (Boolean) NConfig.get(NConfig.Key.persistentBarrelLabels);
        if (persistentLabels != null) {
            forced = persistentLabels;
        }
        
        String ntext = null;
        for (Gob.Overlay ol : gob.ols) {
            if (ol.spr == null || ol.spr.res == null)
                continue;
            String content = contentName(ol.spr.res.name);
            if (content != null)
                ntext = content;
        }
        if (ntext == null) {
            text = null;
            img = null;
            label = null;
        } else if (!ntext.equals(text)) {
            text = ntext;
            img = null;
            label = new TexI(titleFurnace().render(text).img);
        }
        return super.tick(dt);
    }

    /**
     * Barrel contents are overlays named {@code .../barrel-<liquid>}. Other overlays on the
     * same gob (carry poles, signs, …) must not become the floating caption.
     */
    static String contentName(String resName) {
        if (resName == null)
            return null;
        int slash = resName.lastIndexOf('/');
        String leaf = slash >= 0 ? resName.substring(slash + 1) : resName;
        if (!leaf.startsWith("barrel-") || leaf.length() == "barrel-".length())
            return null;
        return leaf.substring("barrel-".length());
    }
}
