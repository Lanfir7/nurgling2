package nurgling;

import haven.Audio;
import haven.LayerMeter;
import haven.UI;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Wiki: wild horse throws at 0% pony power. Warn at 10% so the rider can dismount.
 */
public final class PonyPowerAlert {
    public static final double THRESHOLD = 0.10;
    static final String SOUND_RESOURCE = "/nurgling/sounds/horse.wav";

    private static double last = -1;

    private PonyPowerAlert() {}

    public static void reset() {
        last = -1;
    }

    public static boolean isPonyPowerMeter(String name) {
        if (name == null || name.isEmpty())
            return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith("/hast") || n.endsWith("/häst") || n.endsWith("häst") || "hast".equals(n);
    }

    public static boolean shouldAlert(double current) {
        if (last < 0) {
            last = current;
            return false;
        }
        boolean fire = last > THRESHOLD && current <= THRESHOLD;
        last = current;
        return fire;
    }

    public static void onUpdate(String meterName, List<LayerMeter.Meter> meters) {
        if (!isPonyPowerMeter(meterName))
            return;
        if (meters == null || meters.isEmpty()) {
            reset();
            return;
        }
        if (shouldAlert(meters.get(0).a))
            play();
    }

    static void play() {
        NUI ui = UI.getInstance();
        if (ui == null)
            return;
        InputStream in = PonyPowerAlert.class.getResourceAsStream(SOUND_RESOURCE);
        if (in == null)
            return;
        try {
            ui.sfx(Audio.PCMClip.fromwav(new BufferedInputStream(in)));
        } catch (IOException ignored) {
        }
    }
}
