package nurgling.overlays;

import nurgling.tools.CreatureHp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fight-scoped damage totals keyed by {@code gob.id}, not Gob identity.
 * Survives the animal leaving view (WeakHashMap overlay registry is dropped)
 * for as long as the fight relation lasts.
 */
public final class NCombatDamageStore {
    private static final Map<Long, int[]> byGobId = new ConcurrentHashMap<>();

    private NCombatDamageStore() {}

    public static void record(long gobid, int type, int amount) {
        if((type < 0) || (type > 2) || (amount == 0))
            return;
        byGobId.compute(gobid, (id, prev) -> {
            int[] next = (prev == null) ? new int[3] : prev.clone();
            next[type] += amount;
            return next;
        });
    }

    public static void replace(long gobid, int red, int yellow, int green) {
        byGobId.put(gobid, new int[]{red, yellow, green});
    }

    public static int[] snapshot(long gobid) {
        int[] cur = byGobId.get(gobid);
        return (cur == null) ? new int[3] : cur.clone();
    }

    public static int total(long gobid) {
        int[] d = snapshot(gobid);
        return CreatureHp.hpDealt(d[0], d[1], d[2]);
    }

    public static boolean contains(long gobid) {
        return byGobId.containsKey(gobid);
    }

    public static void copyInto(long gobid, int[] dest) {
        int[] stored = snapshot(gobid);
        dest[0] = stored[0];
        dest[1] = stored[1];
        dest[2] = stored[2];
    }

    public static void clear(long gobid) {
        byGobId.remove(gobid);
    }

    public static void clearAll() {
        byGobId.clear();
    }
}
