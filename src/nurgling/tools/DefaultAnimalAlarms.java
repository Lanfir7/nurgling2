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
}
