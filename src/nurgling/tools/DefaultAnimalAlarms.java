package nurgling.tools;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default IconSettings alarm wav for animals that have a matching file in AlarmSounds.
 * Matches both gob paths ({@code gfx/kritter/bear/bear}) and map-icon paths
 * ({@code gfx/invobjs/kritter/bear}).
 */
public final class DefaultAnimalAlarms {
    private static final Map<String, String> TOKEN_TO_WAV = new HashMap<>();

    static {
        token("adder", "ND_Snake.wav");
        token("snake", "ND_Snake.wav");
        token("badger", "ND_Badger.wav");
        token("bear", "ND_Bear.wav");
        token("polarbear", "ND_Bear.wav");
        token("boar", "ND_Boar.wav");
        token("wildboar", "ND_Boar.wav");
        token("boreworm", "ND_Ambush.wav");
        token("spermwhale", "ND_Cachalot.wav");
        token("cachalot", "ND_Cachalot.wav");
        token("caveangler", "ND_CaveAngler.wav");
        token("eagle", "ND_Eagle.wav");
        token("goldeneagle", "ND_Eagle.wav");
        token("eagleowl", "ND_EagleOwl.wav");
        token("greyseal", "ND_GreySeal.wav");
        token("lynx", "ND_Lynx.wav");
        token("mammoth", "ND_Mammoth.wav");
        token("moose", "ND_Moose.wav");
        token("nidbane", "ND_Nidbane.wav");
        token("orca", "ND_Orca.wav");
        token("troll", "ND_Troll.wav");
        token("walrus", "ND_Walrus.wav");
        token("wolf", "ND_Wolf.wav");
        token("wolverine", "ND_Wolverine.wav");
    }

    private DefaultAnimalAlarms() {}

    private static void token(String name, String wav) {
        TOKEN_TO_WAV.put(name, wav);
    }

    public static String soundFileFor(String resName) {
        if(resName == null || resName.isEmpty())
            return(null);
        String[] parts = resName.split("/");
        int from = Math.max(0, parts.length - 2);
        for(int i = parts.length - 1; i >= from; i--) {
            String wav = TOKEN_TO_WAV.get(parts[i].toLowerCase(Locale.ROOT));
            if(wav != null)
                return(wav);
        }
        return(null);
    }

    public static boolean hasSound(String resName) {
        return(soundFileFor(resName) != null);
    }

    public enum Play { NOW, LATER, NEVER }

    public static final class State {
        private Runnable notification;
        private boolean visualActive = true;

        public State(Runnable notification) {
            this.notification = notification;
        }

        public Play poll(String pose, String iconResName) {
            if(notification == null)
                return(null);
            Play play = playForPose(pose, iconResName);
            if(play == Play.LATER)
                return(play);
            if(play == Play.NEVER) {
                notification = null;
                visualActive = false;
            } else if(notification != null) {
                Runnable current = notification;
                notification = null;
                current.run();
            }
            return(play);
        }

        public void expireVisual() {
            visualActive = false;
        }

        public boolean isVisualActive() {
            return(visualActive);
        }

        public boolean isPending() {
            return(notification != null);
        }

        /** Drop pending notify sound without playing; visual pulse may remain. */
        public void dropSound() {
            notification = null;
        }
    }

    public static boolean isCorpsePose(String pose) {
        if(pose == null || pose.isEmpty())
            return(false);
        String p = pose.toLowerCase(Locale.ROOT);
        return(p.contains("knock") || p.contains("dead"));
    }

    /** Skip alarms on known corpses; play immediately when pose is unknown. */
    public static Play playForPose(String pose, String iconResName) {
        if(isCorpsePose(pose))
            return(Play.NEVER);
        if(!hasSound(iconResName))
            return(Play.NOW);
        if(pose == null || pose.isEmpty())
            return(Play.NOW);
        return(Play.NOW);
    }
}
