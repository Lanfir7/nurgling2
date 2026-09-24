package nurgling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Items Free Inventory may take from belt pouches in addition to the backpack. */
public final class FreeInventoryItems {
    private static final ThreadLocal<Boolean> INCLUDE_BELT_POUCHES =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FreeInventoryItems() {}

    public static void includeBeltPouches(boolean include) {
        INCLUDE_BELT_POUCHES.set(include);
    }

    public static boolean includeBeltPouches() {
        return Boolean.TRUE.equals(INCLUDE_BELT_POUCHES.get());
    }

    public static <T> ArrayList<T> withBeltPouches(ArrayList<T> inventory, List<T> beltPouchContents) {
        if (!includeBeltPouches() || beltPouchContents == null || beltPouchContents.isEmpty()) {
            return inventory;
        }
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        for (T item : inventory) {
            seen.add(item);
        }
        for (T item : beltPouchContents) {
            if (item != null && seen.add(item)) {
                inventory.add(item);
            }
        }
        return inventory;
    }
}
