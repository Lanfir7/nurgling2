package nurgling.actions;

import haven.*;
import haven.res.ui.stackinv.ItemStack;
import nurgling.*;
import nurgling.areas.NContext;
import nurgling.db.dao.StorageItemDao;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.ItemVariants;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BooleanSupplier;

import static haven.OCache.posres;

/** Fetch countable, exact-name inputs from known village containers. Never samples piles or touches the hand. */
public final class TakeFromStorageIndex implements Action {
    public enum Outcome { CONFIRMED, EXHAUSTED, UNSUPPORTED, ERROR }
    private final Source source;
    private final String name;
    private final int targetUnits;
    private final BooleanSupplier selectionValid;
    private Outcome outcome = Outcome.ERROR;

    public Outcome outcome() { return outcome; }

    public TakeFromStorageIndex(Source source, String name, int targetUnits) {
        this(source, name, targetUnits, () -> true);
    }

    public TakeFromStorageIndex(Source source, String name, int targetUnits, BooleanSupplier selectionValid) {
        this.source = source;
        this.name = name;
        this.targetUnits = targetUnits;
        this.selectionValid = selectionValid;
    }

    public static boolean eligibleName(String name) {
        return name != null && !name.isBlank() && !ItemVariants.isKey(name)
                && !ItemVariants.has(name) && !"Milk".equals(name) && !"Generic Milk".equals(name)
                && !ItemVariants.MILKS.contains(name);
    }

    public static final class Source {
        private final Map<String, LinkedHashMap<String, StorageItemDao.LocatedItem>> rows = new HashMap<>();
        private final Set<String> depleted = new HashSet<>();
        private final Set<String> unsupported = new HashSet<>();
        private final Set<Long> village;
        private final boolean available;

        private Source(Set<Long> village, boolean available) {
            this.village = Set.copyOf(village);
            this.available = available;
        }

        public static Source load(Collection<String> names, Set<Long> village) {
            if (village == null || village.isEmpty() || NCore.databaseManager == null)
                return new Source(Collections.emptySet(), false);
            Source result = new Source(village, true);
            if (!result.reload(names)) return new Source(village, false);
            return result;
        }

        private boolean reload(Collection<String> names) {
            if (!available) return false;
            List<String> exact = new ArrayList<>();
            for (String name : names) if (eligibleName(name)) exact.add(name);
            try {
                List<StorageItemDao.LocatedItem> found = NCore.databaseManager.getStorageItemService()
                        .loadLocatedCountableByExactNames(exact);
                for (String name : exact) rows.remove(name);
                addRows(found, exact);
                return true;
            } catch (SQLException ex) {
                return false;
            }
        }

        static Source fromRows(Set<Long> village, Collection<String> names,
                               Collection<StorageItemDao.LocatedItem> found) {
            Source source = new Source(village, true);
            source.addRows(found, names);
            return source;
        }

        private void addRows(Collection<StorageItemDao.LocatedItem> found, Collection<String> exact) {
            for (StorageItemDao.LocatedItem row : found)
                if (row != null && exact.contains(row.name) && row.container != null
                        && row.coord != null && village.contains(row.gridId))
                    rows.computeIfAbsent(row.name, ignored -> new LinkedHashMap<>())
                            .putIfAbsent(row.container, row);
        }

        public boolean available() { return available; }

        public boolean has(String name) {
            Map<String, StorageItemDao.LocatedItem> named = rows.get(name);
            if (named == null) return false;
            for (String hash : named.keySet()) if (!depleted.contains(name + '\0' + hash)) return true;
            return false;
        }

        StorageItemDao.LocatedItem next(String name) {
            Map<String, StorageItemDao.LocatedItem> named = rows.get(name);
            if (named == null) return null;
            for (StorageItemDao.LocatedItem row : named.values())
                if (!depleted.contains(name + '\0' + row.container)) return row;
            return null;
        }

        void recordVisit(String name, String hash, boolean exhausted) {
            if (exhausted) depleted.add(name + '\0' + hash);
        }

        void recordUnsupported(String name) { unsupported.add(name); }
        boolean hasUnsupported(String name) { return unsupported.contains(name); }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || source == null || !source.available() || !eligibleName(name)
                || targetUnits < 1 || gui.vhand != null || gui.map == null || gui.map.placing != null)
            return Results.FAIL();
        return runWith(new Access() {
            @Override public boolean ready() {
                return selectionValid.getAsBoolean() && gui.vhand == null && gui.map.placing == null;
            }
            @Override public int count() { return countExactUnits(gui.getInventory(), name); }
            @Override public Visit visit(StorageItemDao.LocatedItem row, int want) throws InterruptedException {
                return TakeFromStorageIndex.this.visit(gui, row, want);
            }
        });
    }

    interface Access {
        boolean ready();
        int count();
        Visit visit(StorageItemDao.LocatedItem row, int want) throws InterruptedException;
    }

    Results runWith(Access access) throws InterruptedException {
        if(source == null || !source.available() || !eligibleName(name) || targetUnits < 1
                || access == null || !access.ready()) return Results.FAIL();
        int have = access.count();
        if (have < 0) return Results.FAIL();
        if (have >= targetUnits) { outcome = Outcome.CONFIRMED; return Results.SUCCESS(); }
        int attempts = 0;
        boolean unsupported = source.hasUnsupported(name);
        while (have < targetUnits && attempts++ < 32) {
            if (!access.ready()) return Results.FAIL();
            StorageItemDao.LocatedItem row = source.next(name);
            if (row == null) {
                outcome = unsupported ? Outcome.UNSUPPORTED : Outcome.EXHAUSTED;
                return Results.FAIL();
            }
            Visit result = access.visit(row, targetUnits - have);
            if (result == Visit.ERROR) return Results.FAIL();
            if (result == Visit.UNSUPPORTED) {
                unsupported = true;
                source.recordUnsupported(name);
            }
            source.recordVisit(name, row.container,
                    result == Visit.EXHAUSTED || result == Visit.UNSUPPORTED);
            int after = access.count();
            if (after < have || (after == have && result == Visit.DONE)) return Results.FAIL();
            have = after;
        }
        if (have >= targetUnits) {
            outcome = Outcome.CONFIRMED;
            return Results.SUCCESS();
        }
        return Results.FAIL();
    }

    enum Visit { DONE, EXHAUSTED, UNSUPPORTED, ERROR }

    private Visit visit(NGameUI gui, StorageItemDao.LocatedItem row, int want) throws InterruptedException {
        if (!selectionValid.getAsBoolean() || gui.vhand != null || gui.map.placing != null) return Visit.ERROR;
        Gob gob = StorageNav.reach(gui, row);
        if (gob == null || gob.ngob == null || gob.ngob.name == null) return Visit.ERROR;
        if (gob.ngob.name.startsWith("gfx/terobjs/stockpile")) return Visit.UNSUPPORTED;
        String caption = NContext.contcaps.get(gob.ngob.name);
        if (caption == null || "Barrel".equals(caption) || "Stockpile".equals(caption)) return Visit.ERROR;
        Window existing = gui.getWindow(caption);
        if (existing != null) {
            NInventory bound = gui.getInventory(caption);
            if (bound == null || bound.parentGob == null || bound.parentGob.id != gob.id)
                return Visit.ERROR;
        }
        if (!new PathFinder(gob).run(gui).IsSuccess()) return Visit.ERROR;
        if (!selectionValid.getAsBoolean() || gui.vhand != null || gui.map.placing != null) return Visit.ERROR;
        Window afterWalk = gui.getWindow(caption);
        if (afterWalk != existing) return Visit.ERROR;
        boolean opened = false;
        if (existing == null) {
            gui.ui.core.setLastAction(gob);
            gui.map.wdgmsg("click", Coord.z, gob.rc.floor(posres), 3, 0, 0, (int) gob.id,
                    gob.rc.floor(posres), 0, -1);
            opened = true;
            NTask wait = new NTask() {
                { infinite = false; criticalOnTimeout = false; maxCounter = 200; }
                @Override public boolean check() { return gui.getInventory(caption) != null; }
            };
            NUtils.addTask(wait);
        }
        NInventory from = gui.getInventory(caption);
        if (from == null || from.parentGob == null || from.parentGob.id != gob.id) return Visit.ERROR;
        try {
            int moved = 0;
            while (moved < want) {
                if (!selectionValid.getAsBoolean() || gui.vhand != null || gui.map.placing != null || gui.getInventory(caption) != from
                        || from.parentGob == null || from.parentGob.id != gob.id) return Visit.ERROR;
                List<WItem> choices = exactLeaves(from, name);
                if (choices == null) return Visit.ERROR;
                if (choices.isEmpty()) return Visit.EXHAUSTED;
                WItem chosen = choices.get(0);
                int before = countExactUnits(gui.getInventory(), name);
                if (before < 0) return Visit.ERROR;
                PouchContainerTransfer.Outcome transfer = PouchContainerTransfer.moveOne(
                        PouchContainerTransfer.liveSelected(chosen, gui.getInventory(), name, null), 1);
                if (transfer.failed || transfer.moved != 1 || gui.vhand != null) return Visit.ERROR;
                int after = countExactUnits(gui.getInventory(), name);
                List<WItem> remaining = exactLeaves(from, name);
                if (!confirmedMove(before, after, chosen, remaining)) return Visit.ERROR;
                moved += after - before;
            }
            return Visit.DONE;
        } finally {
            if (opened && gui.getInventory(caption) == from && from.parentGob != null
                    && from.parentGob.id == gob.id) {
                Window own = gui.getWindow(caption);
                if (own != null) own.wdgmsg("close");
            }
        }
    }

    /** Direct snapshot avoids a nested blocking GetItems task inside receipt checks. Negative means unread. */
    public static int countExactUnits(NInventory inventory, String name) {
        if (inventory == null || name == null) return -1;
        if (inventory.ui == null) return count(inventory.child, name, null);
        synchronized (inventory.ui) { return count(inventory.child, name, null); }
    }

    static List<WItem> exactLeaves(NInventory inventory, String name) {
        List<WItem> result = new ArrayList<>();
        int valid;
        if (inventory.ui == null) valid = count(inventory.child, name, result);
        else synchronized (inventory.ui) { valid = count(inventory.child, name, result); }
        return valid < 0 ? null : result;
    }

    static boolean confirmedMove(int before, int after, WItem chosen, List<WItem> remaining) {
        return before >= 0 && after > before && chosen != null && remaining != null
                && !remaining.contains(chosen);
    }

    private static int count(Widget first, String name, List<WItem> matches) {
        int amount = 0;
        for (Widget child = first; child != null; child = child.next) {
            if (!(child instanceof WItem)) continue;
            WItem item = (WItem) child;
            if (item.item.contents instanceof ItemStack) {
                int nested = count(item.item.contents.child, name, matches);
                if (nested < 0) return -1;
                if (amount > Integer.MAX_VALUE - nested) return -1;
                amount += nested;
                continue;
            }
            if (!(item.item instanceof NGItem)) return -1;
            NGItem ng = (NGItem) item.item;
            String actual;
            try { actual = ng.name(); } catch (Loading loading) { return -1; }
            if (actual == null) return -1;
            if (!name.equals(actual)) continue;
            if (!NGItem.validateItem(item)) return -1;
            GItem.Amount stack;
            try { stack = ng.getInfo(GItem.Amount.class); } catch (Loading loading) { return -1; }
            int units = stack == null ? 1 : stack.itemnum();
            if (units <= 0 || amount > Integer.MAX_VALUE - units) return -1;
            amount += units;
            if (matches != null) matches.add(item);
        }
        return amount;
    }
}
