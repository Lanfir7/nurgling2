package nurgling.actions;

import haven.Coord;
import haven.Gob;
import haven.Resource;
import haven.WItem;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import nurgling.tools.Container;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FindQualityThresholdWidgetsTest {
    static {
        NConfig.getGlobalInstance();
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if (stream == null) throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch (java.net.MalformedURLException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    void liveWidgetSnapshotCountsEachStackLeafAndExcludesLowCarriedEgg() throws Exception {
        NInventory cupboard = new NInventory(new Coord(4, 4));
        ItemStack stack = new ItemStack();
        NGItem owner = item("Silkworm Egg", 1f);
        owner.contents = stack;
        hold(cupboard, owner);
        hold(stack, item("Silkworm Egg", 90f));
        hold(stack, item("Silkworm Egg", 80f));
        hold(cupboard, item("Silkworm Egg", 70f));

        List<WItem> source = new ArrayList<>();
        assertTrue(FindQualityThreshold.snapshot(cupboard, "Silkworm Egg", source));
        assertEquals(3, source.size());

        NInventory player = new NInventory(new Coord(4, 4));
        hold(player, item("Silkworm Egg", 10f));
        List<WItem> carried = new ArrayList<>();
        assertTrue(FindQualityThreshold.snapshot(player, "Silkworm Egg", carried));
        List<Float> qualities = new ArrayList<>(Arrays.asList(90f, 80f, 70f));
        qualities.add(((NGItem) carried.get(0).item).quality);
        Float cutoff = FindQualityThreshold.selectThreshold(qualities, 2);
        assertEquals(80f, cutoff);
        assertFalse(FindQualityThreshold.eligible(((NGItem) carried.get(0).item).quality, cutoff));
    }

    @Test
    void unresolvedSourceQualityOrUnknownItemInvalidatesEntireSnapshot() throws Exception {
        NInventory cupboard = new NInventory(new Coord(4, 4));
        hold(cupboard, item("Silkworm Cocoon", 90f));
        hold(cupboard, item("Silkworm Cocoon", null));
        assertFalse(FindQualityThreshold.snapshot(cupboard, "Silkworm Cocoon", new ArrayList<>()));

        NInventory unknown = new NInventory(new Coord(4, 4));
        hold(unknown, item(null, 1f));
        assertFalse(FindQualityThreshold.snapshot(unknown, "Silkworm Cocoon", new ArrayList<>()));
    }

    @Test
    void sameCaptionWindowMustBelongToScannedGob() throws Exception {
        NInventory window = new NInventory(new Coord(4, 4));
        Container expected = (Container) unsafe().allocateInstance(Container.class);
        expected.gobid = 42;
        assertFalse(FindQualityThreshold.owns(window, expected));

        Gob other = (Gob) unsafe().allocateInstance(Gob.class);
        other.id = 43;
        window.parentGob = other;
        assertFalse(FindQualityThreshold.owns(window, expected));

        other.id = 42;
        assertTrue(FindQualityThreshold.owns(window, expected));
    }

    @Test
    void breedingTransferSelectsBestHeldUnitsBeforeLowQualityFullStack() throws Exception {
        ItemStack lowStack = new ItemStack();
        ArrayList<WItem> held = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            NGItem low = item("Silkworm Egg", 10f);
            WItem member = row(low);
            member.parent = lowStack;
            lowStack.wmap.put(low, member);
            held.add(member);
        }
        WItem high = row(item("Silkworm Egg", 100f));
        WItem medium = row(item("Silkworm Egg", 90f));
        held.add(high);
        held.add(medium);

        // Destination needs only two eggs; source stack priority must not hide either best unit.
        ArrayList<WItem> ordered = TransferToContainer.sortItemsByPriority(
                held, "Silkworm Egg", true, true);
        assertEquals(5, ordered.size());
        assertSame(high, ordered.get(0));
        assertSame(medium, ordered.get(1));

        ArrayList<WItem> ordinary = TransferToContainer.sortItemsByPriority(
                held, "Silkworm Egg", true, false);
        assertSame(held.get(0), ordinary.get(0));

        // Tetris containers consume the matching list directly, without stack-category sorting.
        ArrayList<WItem> tetrisMatches = TransferToContainer.orderMatches(
                new ArrayList<>(held), true);
        assertSame(high, tetrisMatches.get(0));
        assertSame(medium, tetrisMatches.get(1));
    }

    @Test
    void carriedStackCanSatisfyDemandWithoutAnySourceCabinet() throws Exception {
        NInventory player = new NInventory(new Coord(4, 4));
        ItemStack stack = new ItemStack();
        NGItem owner = item("Silkworm Egg", 1f);
        owner.contents = stack;
        hold(player, owner);
        hold(stack, item("Silkworm Egg", 90f));
        hold(stack, item("Silkworm Egg", 80f));
        List<WItem> held = new ArrayList<>();
        assertTrue(FindQualityThreshold.snapshot(player, "Silkworm Egg", held));

        FindQualityThreshold selection = new FindQualityThreshold(null, "Silkworm Egg", 2);
        List<Float> qualities = Arrays.asList(((NGItem) held.get(0).item).quality,
                ((NGItem) held.get(1).item).quality);
        assertTrue(selection.finishCarriedOnly(held, qualities).IsSuccess());
        assertEquals(80f, selection.getThreshold());
        NUI previous = ThreadLocalUI.get();
        RecordingGameUI game = (RecordingGameUI) unsafe().allocateInstance(RecordingGameUI.class);
        game.errors = new ArrayList<>();
        NUI ui = (NUI) unsafe().allocateInstance(NUI.class);
        ui.gui = game;
        ThreadLocalUI.set(ui);
        try {
            assertFalse(new FindQualityThreshold(null, "Silkworm Egg", 3)
                    .finishCarriedOnly(held, qualities).IsSuccess());
            assertEquals(List.of("No container source for Silkworm Egg"), game.errors);
        } finally {
            if (previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
        }
    }

    private static void hold(Widget inventory, NGItem item) throws Exception {
        WItem row = row(item);
        place(inventory, item);
        place(inventory, row);
    }

    private static WItem row(NGItem item) throws Exception {
        WItem row = (WItem) unsafe().allocateInstance(WItem.class);
        set(row, "item", item);
        return row;
    }

    private static void place(Widget parent, Widget child) {
        child.parent = parent;
        child.link();
    }

    private static NGItem item(String name, Float quality) throws Exception {
        NGItem item = (NGItem) unsafe().allocateInstance(NGItem.class);
        set(item, "name", name);
        item.quality = quality;
        item.info = Collections.emptyList();
        return item;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class RecordingGameUI extends NGameUI {
        List<String> errors;
        private RecordingGameUI() { super("test", 0, "test", null); }
        @Override public void error(String message) { errors.add(message); }
    }
}
