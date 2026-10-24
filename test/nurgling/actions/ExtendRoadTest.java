package nurgling.actions;

import haven.Button;
import haven.Coord;
import haven.Coord2d;
import haven.GItem;
import haven.Gob;
import haven.Loader;
import haven.Resource;
import haven.WItem;
import haven.Window;
import nurgling.NGItem;
import nurgling.NGob;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NISBox;
import nurgling.NMapView;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import nurgling.actions.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

class ExtendRoadTest {
    private NConfig prior;

    @BeforeEach void config() {
        prior = NConfig.current;
        if (NConfig.current == null) NConfig.current = new NConfig();
    }

    @AfterEach void restore() { NConfig.current = prior; }

    @Test
    void siteAndRockLineStayAlignedAtNegativeCoordinatesInAllDirections() {
        Coord2d spot = Coord2d.of(-104.5, -49.5);
        for (Coord2d along : Arrays.asList(Coord2d.of(1, 0), Coord2d.of(-1, 0),
                Coord2d.of(0, 1), Coord2d.of(0, -1))) {
            Coord2d outward = Coord2d.of(Math.abs(along.y), Math.abs(along.x));
            ExtendRoad.Siting site = ExtendRoad.site(spot, along, outward, Coord2d.of(33, 0));
            assertEquals(11, site.pos.dist(spot), 0.001);
            assertEquals(Math.sqrt(33 * 33 - 11 * 11), spot.dist(site.attach), 0.001);
            assertTrue(ExtendRoad.lineTiles(site.pos, site.attach).contains(site.pos.floor(Coord2d.of(11, 11))));
            assertTrue(ExtendRoad.lineTiles(site.pos, site.attach).stream().anyMatch(tile -> tile.x < 0 && tile.y < 0));
        }
    }

    @Test
    void onlyUniqueConstructionAtExactSiteCanBeResumed() {
        Coord2d site = Coord2d.of(-104.5, 38.5);
        Gob matching = NGob.getDummy(site, 0, (String) null);
        matching.ngob.name = "gfx/terobjs/consobj";
        Gob neighbor = NGob.getDummy(site.add(22, 0), 0, (String) null);
        neighbor.ngob.name = matching.ngob.name;
        Gob unrelated = NGob.getDummy(site, 0, (String) null);
        unrelated.ngob.name = "gfx/terobjs/other";
        assertEquals(List.of(matching), ExtendRoad.unfinishedAt(List.of(matching, neighbor, unrelated), site));
        Gob duplicate = NGob.getDummy(site.add(1, 0), 0, (String) null);
        duplicate.ngob.name = matching.ngob.name;
        assertEquals(2, ExtendRoad.unfinishedAt(List.of(matching, duplicate), site).size());
        Gob shortReach = NGob.getDummy(site.add(5, 0), 0, (String) null);
        shortReach.ngob.name = matching.ngob.name;
        assertEquals(1, ExtendRoad.unfinishedAt(List.of(shortReach), site).size());
    }

    @Test
    void windowMustBeMilestoneWithExpectedActionAndStoneAmountsAreCounted() {
        Window unrelated = new Window(Coord.of(180, 90), "Other");
        unrelated.add(new Button(70, "Extend"));
        assertFalse(ExtendRoad.expectedWindow(unrelated, "Extend"));
        Window lookalike = new Window(Coord.of(180, 90), "Another Milestone");
        lookalike.add(new Button(70, "Extend"));
        assertFalse(ExtendRoad.expectedWindow(lookalike, "Extend"));
        Window milestone = new Window(Coord.of(180, 90), "Milestone");
        milestone.add(new Button(70, "Travel"));
        assertFalse(ExtendRoad.expectedWindow(milestone, "Extend"));
        milestone.add(new Button(70, "Extend"));
        assertTrue(ExtendRoad.expectedWindow(milestone, "Extend"));
        assertFalse(ExtendRoad.expectedWindow(milestone, "Build"));
        assertTrue(ExtendRoad.expectedWindow(milestone, null));

        NGItem singles = new NGItem(() -> null);
        NGItem stack = new NGItem(() -> null);
        stack.info = List.of(new GItem.Amount(stack, 6));
        assertEquals(7, ExtendRoad.countStones(List.of(new WItem(singles), new WItem(stack))));
    }

    @Test
    void routeOffsetsAndRasterRejectUnboundedOrNonfiniteGeometry() {
        assertTrue(ExtendRoad.validRouteOffsets(Coord2d.of(3, 0), Coord2d.of(5, 0)));
        assertFalse(ExtendRoad.validRouteOffsets(Coord2d.of(Double.NaN, 0), Coord2d.of(5, 0)));
        assertFalse(ExtendRoad.validRouteOffsets(Coord2d.of(3, 0), Coord2d.of(Double.POSITIVE_INFINITY, 0)));
        assertFalse(ExtendRoad.validRouteOffsets(Coord2d.of(3, 0), Coord2d.of(1000, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> ExtendRoad.lineTiles(Coord2d.of(0, 0), Coord2d.of(1_000_000, 0)));
    }

    @Test
    void milestoneStoneSlotReceivesMaterialsBeforeBuildAndStopsOnNoProgress() throws Exception {
        Window wnd = new Window(Coord.of(180, 90), "Milestone");
        wnd.add(new Button(70, "Build"));
        RecordingSlot slot = wnd.add(new RecordingSlot("gfx/invobjs/stone", 5, 5));
        AtomicInteger built = new AtomicInteger();
        assertNull(ExtendRoad.fillAndStart(wnd, 5,
                (box, oldFree) -> box.calcFreeSpace() < oldFree,
                () -> { assertEquals(0, slot.calcFreeSpace()); built.incrementAndGet(); }));
        assertEquals(5, slot.putCalls);
        assertEquals(5, slot.requested);
        assertEquals(1, built.get());

        Window stalled = new Window(Coord.of(180, 90), "Milestone");
        stalled.add(new Button(70, "Build"));
        RecordingSlot rejected = stalled.add(new RecordingSlot("gfx/invobjs/stone", 5, 0));
        assertEquals("stone transfer made no progress", ExtendRoad.fillAndStart(stalled, 5,
                (box, oldFree) -> box.calcFreeSpace() < oldFree, built::incrementAndGet));
        assertEquals(1, rejected.putCalls);
        assertEquals(1, built.get());

        Window partial = new Window(Coord.of(180, 90), "Milestone");
        partial.add(new Button(70, "Build"));
        RecordingSlot firstOnly = partial.add(new RecordingSlot("gfx/invobjs/stone", 5, 1));
        assertEquals("stone transfer made no progress", ExtendRoad.fillAndStart(partial, 5,
                (box, oldFree) -> box.calcFreeSpace() < oldFree, built::incrementAndGet));
        assertEquals(2, firstOnly.putCalls);
        assertEquals(2, firstOnly.requested);
        assertEquals(4, firstOnly.calcFreeSpace());
        assertEquals(1, built.get());

        Window wrong = new Window(Coord.of(180, 90), "Milestone");
        wrong.add(new Button(70, "Build"));
        RecordingSlot wood = wrong.add(new RecordingSlot("gfx/invobjs/wood", 5, 5));
        assertEquals("milestone requires an unexpected material", ExtendRoad.fillAndStart(wrong, 5,
                (box, oldFree) -> true, built::incrementAndGet));
        assertEquals(0, wood.putCalls);
    }

    private static final class RecordingSlot extends NISBox {
        int free;
        final int acceptedPuts;
        int putCalls;
        int requested;

        RecordingSlot(String resource, int free, int acceptedPuts) {
            super(() -> new Resource.Virtual(null, resource, 0), 0, 5, -1);
            this.free = free;
            this.acceptedPuts = acceptedPuts;
        }

        @Override public int calcFreeSpace() { return free; }
        @Override public int calcCount() { return 5 - free; }
        @Override public void put(int amount) {
            putCalls++;
            requested += amount;
            if (putCalls <= acceptedPuts) free = Math.max(0, free - amount);
        }
    }

    @Test
    void heldItemAndForeignPlacementStopBeforeAnyRoadInteraction() throws Exception {
        NUI previous = ThreadLocalUI.get();
        try {
            NUI ui = allocate(NUI.class);
            RecordingGameUI gui = allocate(RecordingGameUI.class);
            ui.gui = gui;
            ThreadLocalUI.set(ui);
            gui.map = allocate(NMapView.class);
            WItem held = new WItem(new NGItem(() -> null));
            gui.vhand = held;
            ExtendRoad action = new ExtendRoad(null, Coord2d.of(0, 0), Coord2d.of(1, 0),
                    Coord2d.of(0, 1), 0);
            assertFalse(action.run(gui).IsSuccess());
            assertSame(held, gui.vhand);
            assertEquals(1, gui.errorCount);

            gui.vhand = null;
            Loader.Future<?> foreign = allocate(Loader.Future.class);
            gui.map.placing = (Loader.Future) foreign;
            assertFalse(action.run(gui).IsSuccess());
            assertSame(foreign, gui.map.placing);
            assertEquals(2, gui.errorCount);
        } finally {
            if (previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingGameUI extends NGameUI {
        int errorCount;

        private RecordingGameUI() { super("", 0L, null, null); }
        @Override public void error(String message) { errorCount++; }
    }
}
