package nurgling.overlays;

import nurgling.tools.CreatureHp;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fight-scoped damage totals keyed by session ({@code Glob}) and {@code gob.id},
 * not Gob identity. Survives the animal leaving view (WeakHashMap overlay
 * registry is dropped) for as long as the fight relation lasts.
 * Weak outer keys so a closed session does not leak totals.
 */
public final class NCombatDamageStore {
    private static final Map<Object, ConcurrentHashMap<Long, int[]>> bySession =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NCombatDamageStore() {}

    private static ConcurrentHashMap<Long, int[]> table(Object session) {
        if(session == null)
            return null;
        synchronized (bySession) {
            return bySession.computeIfAbsent(session, s -> new ConcurrentHashMap<>());
        }
    }

    private static ConcurrentHashMap<Long, int[]> existingTable(Object session) {
        if(session == null)
            return null;
        synchronized (bySession) {
            return bySession.get(session);
        }
    }

    public static void record(Object session, long gobid, int type, int amount) {
        if((type < 0) || (type > 2) || (amount == 0))
            return;
        ConcurrentHashMap<Long, int[]> tab = table(session);
        if(tab == null)
            return;
        tab.compute(gobid, (id, prev) -> {
            int[] next = (prev == null) ? new int[3] : prev.clone();
            next[type] += amount;
            return next;
        });
    }

    public static void replace(Object session, long gobid, int red, int yellow, int green) {
        ConcurrentHashMap<Long, int[]> tab = table(session);
        if(tab == null)
            return;
        tab.put(gobid, new int[]{red, yellow, green});
    }

    public static int[] snapshot(Object session, long gobid) {
        ConcurrentHashMap<Long, int[]> tab = existingTable(session);
        if(tab == null)
            return new int[3];
        int[] cur = tab.get(gobid);
        return (cur == null) ? new int[3] : cur.clone();
    }

    public static int total(Object session, long gobid) {
        int[] d = snapshot(session, gobid);
        return CreatureHp.hpDealt(d[0], d[1], d[2]);
    }

    public static boolean contains(Object session, long gobid) {
        ConcurrentHashMap<Long, int[]> tab = existingTable(session);
        return (tab != null) && tab.containsKey(gobid);
    }

    public static void copyInto(Object session, long gobid, int[] dest) {
        int[] stored = snapshot(session, gobid);
        dest[0] = stored[0];
        dest[1] = stored[1];
        dest[2] = stored[2];
    }

    public static void clear(Object session, long gobid) {
        ConcurrentHashMap<Long, int[]> tab = existingTable(session);
        if(tab != null)
            tab.remove(gobid);
    }

    public static void clearSession(Object session) {
        if(session == null)
            return;
        synchronized (bySession) {
            ConcurrentHashMap<Long, int[]> tab = bySession.get(session);
            if(tab != null)
                tab.clear();
        }
    }

    public static void clearAll() {
        synchronized (bySession) {
            bySession.clear();
        }
    }
}
