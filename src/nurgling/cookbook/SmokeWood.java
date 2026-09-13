package nurgling.cookbook;

import haven.ItemInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One wood from a food's "Smoked with ..." tooltip line.
 *
 * <p>The game sends one {@code haven.res.ui.tt.smoked.Smoke} info per wood. That class ships from the
 * resource server, not with the client, so its public {@code name} and {@code val} fields are read
 * by reflection. {@code val} is the wood's share of the smoke and is null when the game shows none.
 */
public class SmokeWood {
    public final String name;
    /** Share of the smoke in percent; 100 when the game sends no share. */
    public final double percentage;

    private static final Comparator<SmokeWood> BY_NAME_THEN_PERCENT = new Comparator<SmokeWood>() {
        @Override
        public int compare(SmokeWood a, SmokeWood b) {
            int byName = a.name.compareTo(b.name);
            if (byName != 0) {
                return byName;
            }
            return Double.compare(a.percentage, b.percentage);
        }
    };

    private static volatile boolean warned = false;

    public SmokeWood(String name, double percentage) {
        this.name = name;
        this.percentage = percentage;
    }

    /** The smoking woods among an item's infos, sorted by name then percent. */
    public static List<SmokeWood> from(Collection<? extends ItemInfo> info) {
        return extract(info);
    }

    /**
     * Headless extraction: any objects whose runtime class is the smoked tooltip
     * ({@code simpleName == "Smoke"} and the binary name contains {@code "smoked"}).
     * Null list or null entries yield an empty contribution, never an NPE.
     */
    public static List<SmokeWood> extract(Iterable<?> info) {
        List<SmokeWood> woods = new ArrayList<SmokeWood>();
        if (info == null) {
            return woods;
        }
        for (Object inf : info) {
            if (inf == null) {
                continue;
            }
            Class<?> cl = inf.getClass();
            if (!isSmokedTooltip(cl)) {
                continue;
            }
            try {
                String woodName = readName(cl, inf);
                if (woodName == null || woodName.isEmpty()) {
                    continue;
                }
                woods.add(new SmokeWood(woodName, readPercentage(cl, inf)));
            } catch (ReflectiveOperationException e) {
                warnOnce(cl, e);
            } catch (ClassCastException e) {
                warnOnce(cl, e);
            }
        }
        Collections.sort(woods, BY_NAME_THEN_PERCENT);
        return woods;
    }

    /**
     * The woods as they enter the recipe hash: name then percentage, per wood.
     *
     * <p>A single wood with no share reads "Apple tree100.0", the same text the hash carried back when
     * every wood was recorded at 100%, so those recipes keep their hash. Changing this format re-keys
     * every smoked recipe in every village database.
     */
    public static String signature(List<SmokeWood> woods) {
        if (woods == null) {
            return "";
        }
        List<SmokeWood> ordered = new ArrayList<SmokeWood>();
        for (SmokeWood wood : woods) {
            if (wood != null && wood.name != null) {
                ordered.add(wood);
            }
        }
        Collections.sort(ordered, BY_NAME_THEN_PERCENT);
        StringBuilder sb = new StringBuilder();
        for (SmokeWood wood : ordered) {
            sb.append(wood.name).append(wood.percentage);
        }
        return sb.toString();
    }

    static void resetSessionWarning() {
        warned = false;
    }

    private static boolean isSmokedTooltip(Class<?> cl) {
        return "Smoke".equals(cl.getSimpleName()) && cl.getName().contains("smoked");
    }

    private static String readName(Class<?> cl, Object inf) throws ReflectiveOperationException {
        Object raw = cl.getField("name").get(inf);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            throw new ClassCastException("name");
        }
        return (String) raw;
    }

    /**
     * {@code val} is the server fraction (0.5 → 50%). {@code percentage} is already 0–100.
     * Missing or unreadable percent defaults to 100 without treating the class as incompatible.
     */
    private static double readPercentage(Class<?> cl, Object inf) {
        Double fromVal = readNumberField(cl, inf, "val");
        if (fromVal != null) {
            return fromVal.doubleValue() * 100.0;
        }
        Double fromPct = readNumberField(cl, inf, "percentage");
        if (fromPct != null) {
            return fromPct.doubleValue();
        }
        return 100.0;
    }

    private static Double readNumberField(Class<?> cl, Object inf, String fieldName) {
        try {
            Field field = cl.getField(fieldName);
            Object raw = field.get(inf);
            if (raw == null) {
                return null;
            }
            if (raw instanceof Number) {
                return Double.valueOf(((Number) raw).doubleValue());
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void warnOnce(Class<?> cl, Exception e) {
        if (!warned) {
            warned = true;
            System.out.println("[Cookbook] cannot read smoking wood from " + cl.getName() + ": " + e);
        }
    }
}
