package nurgling.actions;

import haven.Drawable;
import haven.Inventory;
import haven.Loading;
import haven.WItem;
import haven.Window;
import haven.res.ui.tt.wear.Wear;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.widgets.TableInventoryExtension;

import java.util.WeakHashMap;

/**
 * Synchronous fail-closed gate for eating from an identified feast table.
 * A successful Eat is kept pending until the server changes the table state.
 */
public final class FeastEatGuard {
    public enum Decision { ALLOW, BLOCK_UNSAFE, BLOCK_LOADING, BLOCK_IN_FLIGHT }

    public static final class TableState {
        final boolean feast;
        final boolean loading;
        final boolean unsafe;
        final String fingerprint;

        private TableState(boolean feast, boolean loading, boolean unsafe, String fingerprint) {
            this.feast = feast;
            this.loading = loading;
            this.unsafe = unsafe;
            this.fingerprint = fingerprint;
        }

        static TableState notFeast() { return new TableState(false, false, false, ""); }
        static TableState loading() { return new TableState(true, true, false, ""); }
        static TableState ready(String fingerprint, boolean unsafe) {
            return new TableState(true, false, unsafe, fingerprint);
        }
    }

    private static final WeakHashMap<Object, FeastEatGuard> guards = new WeakHashMap<>();
    private String pendingFingerprint;
    private final boolean enabled;

    FeastEatGuard(boolean enabled) {
        this.enabled = enabled;
    }

    public static synchronized FeastEatGuard forUi(Object ui, boolean enabled) {
        FeastEatGuard guard = guards.get(ui);
        if (guard == null || guard.enabled != enabled) {
            guard = new FeastEatGuard(enabled);
            guards.put(ui, guard);
        }
        return guard;
    }

    public synchronized Decision beforeEat(TableState state) {
        if (!enabled || !state.feast)
            return Decision.ALLOW;
        if (pendingFingerprint != null) {
            if (!state.loading && !pendingFingerprint.equals(state.fingerprint))
                pendingFingerprint = null;
            else
                return Decision.BLOCK_IN_FLIGHT;
        }
        if (state.loading)
            return Decision.BLOCK_LOADING;
        if (state.unsafe)
            return Decision.BLOCK_UNSAFE;
        pendingFingerprint = state.fingerprint;
        return Decision.ALLOW;
    }

    /** Returns no feast context for ordinary inventories, so their Eat remains unaffected. */
    public static TableState inspect(WItem selected) {
        if (selected == null || !(selected.parent instanceof NInventory))
            return TableState.notFeast();
        NInventory food = (NInventory) selected.parent;
        if (!TableInventoryExtension.isTableRes(gobResource(food)))
            return TableState.notFeast();

        Window window = food.getparent(Window.class);
        if (window == null)
            return TableState.loading();
        Inventory tableware = null;
        Inventory smallTableware = null;
        for (Inventory inventory : window.children(Inventory.class)) {
            if (inventory.isz != null && inventory.isz.x == 3 && inventory.isz.y == 3) {
                tableware = inventory;
            } else if (inventory.isz != null && inventory.isz.x * inventory.isz.y == 2) {
                smallTableware = inventory;
            }
        }
        if (tableware == null)
            return TableState.loading();

        StringBuilder fingerprint = new StringBuilder();
        if (!appendInventory(fingerprint, tableware, true))
            return TableState.loading();
        if (smallTableware != null && !appendInventory(fingerprint, smallTableware, true))
            return TableState.loading();
        if (!appendInventory(fingerprint, food, false))
            return TableState.loading();
        return TableState.ready(fingerprint.toString(), hasUnsafeWear(tableware)
                || (smallTableware != null && hasUnsafeWear(smallTableware)));
    }

    private static boolean appendInventory(StringBuilder target, Inventory inventory, boolean requireWear) {
        target.append('|').append(inventory.wdgid()).append(':');
        for (WItem witem : inventory.getTopLevelItems()) {
            if (!(witem.item instanceof NGItem))
                return false;
            NGItem item = (NGItem) witem.item;
            try {
                Wear wear = null;
                if (requireWear) {
                    item.info();
                    wear = item.getInfo(Wear.class);
                    if (wear == null)
                        return false;
                }
                target.append(item.wdgid()).append('/').append(item.num).append('/').append(item.infoseq);
                if (wear != null)
                    target.append('/').append(wear.d).append('/').append(wear.m);
                target.append(';');
            } catch (Loading ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnsafeWear(Inventory tableware) {
        for (WItem witem : tableware.getTopLevelItems()) {
            NGItem item = (NGItem) witem.item;
            Wear wear = item.getInfo(Wear.class);
            if (wear == null || (wear.m > 0 && wear.m - wear.d <= 1))
                return true;
        }
        return false;
    }

    private static String gobResource(NInventory inventory) {
        if (inventory.parentGob == null)
            return null;
        if (inventory.parentGob.ngob != null && inventory.parentGob.ngob.name != null)
            return inventory.parentGob.ngob.name;
        Drawable drawable = inventory.parentGob.getattr(Drawable.class);
        return (drawable == null || drawable.getres() == null) ? null : drawable.getres().name;
    }
}
