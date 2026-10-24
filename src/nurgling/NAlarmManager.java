package nurgling;

import haven.Audio;
import haven.Resource;
import haven.UI;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class NAlarmManager {
    static final long duration = 1000;

    private static final class Last {
        final String resource;
        final long tick;

        Last(String resource, long tick) {
            this.resource = resource;
            this.tick = tick;
        }
    }

    /** Duplicate sounds are throttled independently for each session. */
    private static final Map<UI, Last> last = new WeakHashMap<>();

    /** Compatibility entry point for bot and other callers with a bound thread UI. */
    public static void play(String resName) {
        play(resName, name -> Audio.fromres(Resource.local().loadwait(name)), NAlarmManager::sfx);
    }

    static void play(String resName, Function<String, Audio.CS> load,
                     BiConsumer<UI, Audio.CS> output) {
        play(NUtils.getUI(), resName, load, output);
    }

    public static void play(UI ui, String resName) {
        play(ui, resName, name -> Audio.fromres(Resource.local().loadwait(name)), NAlarmManager::sfx);
    }

    // The injected loader and output let the throttle be tested without loading resources or opening a device.
    static void play(UI ui, String resName, Function<String, Audio.CS> load,
                     BiConsumer<UI, Audio.CS> output) {
        if (ui == null || resName == null)
            return;
        long tick = ui instanceof NUI ? ((NUI) ui).tickId : System.currentTimeMillis();
        Last previous;
        Last claim;
        synchronized (last) {
            previous = last.get(ui);
            if (previous != null && previous.resource.equals(resName) &&
                    tick >= previous.tick && tick - previous.tick <= duration)
                return;
            last.put(ui, claim = new Last(resName, tick));
        }
        try {
            output.accept(ui, load.apply(resName));
        } catch (RuntimeException | Error failure) {
            synchronized (last) {
                if (last.get(ui) == claim) {
                    if (previous == null)
                        last.remove(ui);
                    else
                        last.put(ui, previous);
                }
            }
            throw failure;
        }
    }

    /** Alarms bypass this UI's mute at its interface volume, then pass through the shared master volume. */
    public static void sfx(UI ui, Audio.CS clip) {
        if (ui == null || ui.audio == null || clip == null)
            return;
        ui.audio.sys.mixer.add(new Audio.VolAdjust(clip, ui.audio.aui.volume));
    }
}
