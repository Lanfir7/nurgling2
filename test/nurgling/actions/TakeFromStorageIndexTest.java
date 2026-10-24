package nurgling.actions;

import haven.Coord;
import haven.GItem;
import haven.Indir;
import haven.Resource;
import haven.WItem;
import haven.Loader;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.NMapView;
import nurgling.db.dao.StorageItemDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

class TakeFromStorageIndexTest {
    private NConfig prior;

    @BeforeEach void config() {
        prior = NConfig.current;
        if (prior == null) NConfig.current = new NConfig();
    }

    @AfterEach void restore() { NConfig.current = prior; }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static WItem item(String name, int units) {
        NGItem ng = new NGItem((Indir<Resource>) () -> null) {
            @Override public String name() { return name; }
            @Override public <C extends haven.ItemInfo> C getInfo(Class<C> type) {
                return type == GItem.Amount.class && units != 1
                        ? type.cast(new GItem.Amount(this, units)) : null;
            }
        };
        ng.quality = 30f;
        return new WItem(ng);
    }

    @Test void exactUnitsAndUnreadItemsControlReceipt() {
        NInventory inv = new NInventory(new Coord(4, 4));
        WItem seeds = item("Barley Seed", 7);
        WItem other = item("Barley Seedling", 3);
        seeds.next = other;
        inv.child = seeds;
        assertEquals(7, TakeFromStorageIndex.countExactUnits(inv, "Barley Seed"));
        assertEquals(List.of(seeds), TakeFromStorageIndex.exactLeaves(inv, "Barley Seed"));
        assertTrue(TakeFromStorageIndex.confirmedMove(0, 7, seeds, List.of()));
        assertFalse(TakeFromStorageIndex.confirmedMove(0, 7, seeds, List.of(seeds)));
        assertFalse(TakeFromStorageIndex.confirmedMove(0, 0, seeds, List.of()));
        other.next = item(null, 1);
        assertEquals(-1, TakeFromStorageIndex.countExactUnits(inv, "Barley Seed"));
    }

    @Test void sameContainerCanSupplyLaterCraftBatchesUntilVerifiedEmpty() {
        StorageItemDao.LocatedItem here = new StorageItemDao.LocatedItem("Barley Seed", "chest-a", 3, "100;100");
        StorageItemDao.LocatedItem otherVillage = new StorageItemDao.LocatedItem("Barley Seed", "chest-b", 4, "100;100");
        TakeFromStorageIndex.Source source = TakeFromStorageIndex.Source.fromRows(
                Set.of(3L), List.of("Barley Seed"), List.of(here, otherVillage));
        assertSame(here, source.next("Barley Seed"));
        source.recordVisit("Barley Seed", here.container, false);
        assertSame(here, source.next("Barley Seed"));
        source.recordVisit("Barley Seed", here.container, true);
        assertNull(source.next("Barley Seed"));
        assertFalse(source.has("Barley Seed"));
    }

    @Test void kindsAndLiquidsStayOnExistingTakeRoutes() {
        assertFalse(TakeFromStorageIndex.eligibleName("Butter"));
        assertFalse(TakeFromStorageIndex.eligibleName("Butter (Cowsmilk)"));
        assertFalse(TakeFromStorageIndex.eligibleName("Milk"));
        assertFalse(TakeFromStorageIndex.eligibleName("Cowsmilk"));
        assertTrue(TakeFromStorageIndex.eligibleName("Barley Seed"));
    }

    @Test void staleOrMalformedContainerCoordinatesCannotLaunchLongNavigation() {
        assertEquals(new Coord(-12, 45), StorageNav.parseCoord("(-12, 45)"));
        assertNull(StorageNav.parseCoord("(Infinity, 4)"));
        assertNull(StorageNav.parseCoord("(1000000000, 4)"));
        assertNull(StorageNav.parseCoord("unloaded"));
    }

    @Test void actualFetchStopsBeforeNavigationForHeldItemPlacementOrChangedSelection() throws Exception {
        TakeFromStorageIndex.Source source = TakeFromStorageIndex.Source.fromRows(Set.of(3L),
                List.of("Barley Seed"), List.of(new StorageItemDao.LocatedItem(
                        "Barley Seed", "chest-a", 3, "(0, 0)")));
        NGameUI gui = (NGameUI) unsafe().allocateInstance(NGameUI.class);
        WItem held = item("Food", 1);
        gui.vhand = held;
        assertFalse(new TakeFromStorageIndex(source, "Barley Seed", 1).run(gui).IsSuccess());
        assertSame(held, gui.vhand);
        assertTrue(source.has("Barley Seed"));

        gui.vhand = null;
        gui.map = (NMapView) unsafe().allocateInstance(NMapView.class);
        gui.map.placing = (Loader.Future) unsafe().allocateInstance(Loader.Future.class);
        assertFalse(new TakeFromStorageIndex(source, "Barley Seed", 1).run(gui).IsSuccess());
        assertNotNull(gui.map.placing);

        gui.map.placing = null;
        assertFalse(new TakeFromStorageIndex(source, "Barley Seed", 1, () -> false).run(gui).IsSuccess());
        assertTrue(source.has("Barley Seed"));
    }

    private static TakeFromStorageIndex.Source twoChests() {
        return TakeFromStorageIndex.Source.fromRows(Set.of(3L), List.of("Barley Seed"), List.of(
                new StorageItemDao.LocatedItem("Barley Seed", "a", 3, "(0, 0)"),
                new StorageItemDao.LocatedItem("Barley Seed", "b", 3, "(1, 0)")));
    }

    @Test void confirmedPartialFetchNeverClaimsTargetAndExhaustionIsDistinctFromError() throws Exception {
        TakeFromStorageIndex.Source source = twoChests();
        AtomicInteger units = new AtomicInteger();
        List<String> visits = new ArrayList<>();
        TakeFromStorageIndex fetch = new TakeFromStorageIndex(source, "Barley Seed", 5);
        TakeFromStorageIndex.Access access = new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return units.get(); }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                visits.add(row.container + ":" + want);
                if ("a".equals(row.container)) units.addAndGet(2);
                return TakeFromStorageIndex.Visit.EXHAUSTED;
            }
        };
        assertFalse(fetch.runWith(access).IsSuccess());
        assertEquals(TakeFromStorageIndex.Outcome.EXHAUSTED, fetch.outcome());
        assertEquals(List.of("a:5", "b:3"), visits);
        assertEquals(2, units.get());
    }

    @Test void sameChestSuppliesTwoCraftBatchesButUnconfirmedMoveStopsImmediately() throws Exception {
        TakeFromStorageIndex.Source source = twoChests();
        AtomicInteger units = new AtomicInteger();
        List<String> visits = new ArrayList<>();
        TakeFromStorageIndex.Access access = new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return units.get(); }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                visits.add(row.container);
                units.addAndGet(want);
                return TakeFromStorageIndex.Visit.DONE;
            }
        };
        assertTrue(new TakeFromStorageIndex(source, "Barley Seed", 2).runWith(access).IsSuccess());
        assertTrue(new TakeFromStorageIndex(source, "Barley Seed", 4).runWith(access).IsSuccess());
        assertEquals(List.of("a", "a"), visits);
        assertEquals(4, units.get());

        TakeFromStorageIndex fail = new TakeFromStorageIndex(twoChests(), "Barley Seed", 2);
        AtomicInteger calls = new AtomicInteger();
        assertFalse(fail.runWith(new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return 0; }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                calls.incrementAndGet();
                return TakeFromStorageIndex.Visit.DONE;
            }
        }).IsSuccess());
        assertEquals(1, calls.get());
        assertEquals(TakeFromStorageIndex.Outcome.ERROR, fail.outcome());
    }

    @Test void selectionChangeAfterFirstReceiptPreventsAnotherContainerVisit() throws Exception {
        TakeFromStorageIndex.Source source = twoChests();
        AtomicInteger units = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        TakeFromStorageIndex fetch = new TakeFromStorageIndex(source, "Barley Seed", 5);
        assertFalse(fetch.runWith(new TakeFromStorageIndex.Access() {
            public boolean ready() { return calls.get() == 0; }
            public int count() { return units.get(); }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                calls.incrementAndGet();
                units.incrementAndGet();
                return TakeFromStorageIndex.Visit.EXHAUSTED;
            }
        }).IsSuccess());
        assertEquals(1, calls.get());
        assertEquals(TakeFromStorageIndex.Outcome.ERROR, fetch.outcome());
    }

    @Test void unsupportedPileIsSkippedWithoutSamplingAndCannotBeCraftAllExhaustion() throws Exception {
        TakeFromStorageIndex.Source source = twoChests();
        AtomicInteger units = new AtomicInteger();
        List<String> visits = new ArrayList<>();
        TakeFromStorageIndex.Access access = new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return units.get(); }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                visits.add(row.container);
                if ("a".equals(row.container)) return TakeFromStorageIndex.Visit.UNSUPPORTED;
                units.addAndGet(want);
                return TakeFromStorageIndex.Visit.DONE;
            }
        };
        TakeFromStorageIndex fetch = new TakeFromStorageIndex(source, "Barley Seed", 2);
        assertTrue(fetch.runWith(access).IsSuccess());
        assertEquals(List.of("a", "b"), visits);
        assertEquals(TakeFromStorageIndex.Outcome.CONFIRMED, fetch.outcome());

        TakeFromStorageIndex nextBatch = new TakeFromStorageIndex(source, "Barley Seed", 3);
        assertFalse(nextBatch.runWith(new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return units.get(); }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                assertEquals("b", row.container);
                return TakeFromStorageIndex.Visit.EXHAUSTED;
            }
        }).IsSuccess());
        assertEquals(TakeFromStorageIndex.Outcome.UNSUPPORTED, nextBatch.outcome());

        TakeFromStorageIndex.Source onlyPile = TakeFromStorageIndex.Source.fromRows(Set.of(3L),
                List.of("Barley Seed"), List.of(new StorageItemDao.LocatedItem(
                        "Barley Seed", "pile", 3, "(0, 0)")));
        TakeFromStorageIndex pileFetch = new TakeFromStorageIndex(onlyPile, "Barley Seed", 1);
        assertFalse(pileFetch.runWith(new TakeFromStorageIndex.Access() {
            public boolean ready() { return true; }
            public int count() { return 0; }
            public TakeFromStorageIndex.Visit visit(StorageItemDao.LocatedItem row, int want) {
                return TakeFromStorageIndex.Visit.UNSUPPORTED;
            }
        }).IsSuccess());
        assertEquals(TakeFromStorageIndex.Outcome.UNSUPPORTED, pileFetch.outcome());
    }
}
