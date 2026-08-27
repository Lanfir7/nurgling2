package nurgling.tools;

import java.awt.Color;
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

    /** Red + yellow are HP. Green is armor soak and does not reduce HP. */
    public static int hpDealt(int red, int yellow, int green) {
        return red + yellow;
    }

    public static int remaining(int dealt, int max) {
        return Math.max(0, max - dealt);
    }

    public static float fraction(int dealt, int max) {
        if(max <= 0)
            return(0f);
        float f = remaining(dealt, max) / (float)max;
        if(f < 0f)
            return(0f);
        if(f > 1f)
            return(1f);
        return(f);
    }

    public static String remainingLabel(int dealt, String resName) {
        Integer max = maxHp(resName);
        if(max != null)
            return(remaining(dealt, max) + "/" + max);
        if(dealt > 0)
            return(Integer.toString(dealt));
        return(null);
    }

    public static Color fillColor(float frac) {
        if(frac > 0.5f) {
            float t = (frac - 0.5f) * 2f;
            return lerp(YELLOW, GREEN, t);
        }
        float t = Math.max(0f, frac) * 2f;
        return lerp(RED, YELLOW, t);
    }

    private static final Color GREEN = new Color(46, 204, 113);
    private static final Color YELLOW = new Color(241, 196, 15);
    private static final Color RED = new Color(231, 76, 60);

    private static Color lerp(Color a, Color b, float t) {
        if(t < 0f) t = 0f;
        if(t > 1f) t = 1f;
        return new Color(
                (int)(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int)(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                230);
    }
}
