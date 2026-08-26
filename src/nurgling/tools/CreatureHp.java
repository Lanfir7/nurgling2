package nurgling.tools;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Base creature HP from the Ring of Brodgar Creatures table.
 * Lookup by gob/icon resource path tokens ({@code gfx/kritter/boar/boar} → boar).
 */
public final class CreatureHp {
    private static final Map<String, Integer> HP = new HashMap<>();

    static {
        hp("adder", 70);
        hp("ants", 50);
        hp("aurochs", 350);
        hp("badger", 250);
        hp("bat", 90);
        hp("bear", 850);
        hp("beaver", 100);
        hp("boar", 450);
        hp("wildboar", 450);
        hp("bogturtle", 1);
        hp("boreworm", 1200);
        hp("cachalot", 50000);
        hp("spermwhale", 50000);
        hp("caveangler", 1200);
        hp("cavelouse", 1000);
        hp("caverat", 120);
        hp("chasmconch", 20000);
        hp("cock", 10);
        hp("eagleowl", 180);
        hp("fox", 110);
        hp("goat", 200);
        hp("goldeneagle", 250);
        hp("eagle", 250);
        hp("greenooze", 60);
        hp("greyseal", 320);
        hp("hedgehog", 40);
        hp("hen", 10);
        hp("polarbear", 1250);
        hp("lynx", 400);
        hp("mallarddrake", 10);
        hp("mallardhen", 10);
        hp("mallard", 10);
        hp("mammoth", 4000);
        hp("mole", 30);
        hp("moose", 800);
        hp("mouflon", 200);
        hp("orca", 20000);
        hp("otter", 100);
        hp("pelican", 130);
        hp("pig", 150);
        hp("quail", 10);
        hp("rabbitbuck", 30);
        hp("rabbitdoe", 30);
        hp("rabbit", 30);
        hp("reddeer", 200);
        hp("reindeer", 200);
        hp("rockdove", 10);
        hp("seagull", 10);
        hp("sheep", 200);
        hp("squirrel", 10);
        hp("stoat", 90);
        hp("swan", 150);
        hp("troll", 1000);
        hp("walrus", 900);
        hp("wildbees", 50);
        hp("wildhorse", 320);
        hp("wildgoat", 300);
        hp("wolf", 500);
        hp("wolverine", 300);
        hp("woodgrousecock", 120);
        hp("woodgrousehen", 10);
    }

    private CreatureHp() {}

    private static void hp(String token, int value) {
        HP.put(token, value);
    }

    public static Integer maxHp(String resName) {
        if(resName == null || resName.isEmpty())
            return(null);
        String[] parts = resName.split("/");
        int from = Math.max(0, parts.length - 2);
        for(int i = parts.length - 1; i >= from; i--) {
            Integer hp = HP.get(parts[i].toLowerCase(Locale.ROOT));
            if(hp != null)
                return(hp);
        }
        return(null);
    }

    public static String label(int dealt, String resName) {
        Integer max = maxHp(resName);
        if(max != null)
            return(dealt + "/" + max);
        if(dealt > 0)
            return(Integer.toString(dealt));
        return(null);
    }
}
