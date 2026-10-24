package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.actions.Results;
import nurgling.sessions.ThreadLocalUI;
import nurgling.widgets.TunnelingDialog.Direction;
import nurgling.widgets.TunnelingDialog.SupportType;
import nurgling.widgets.TunnelingDialog.TunnelSide;
import nurgling.widgets.TunnelingDialog.WingOption;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OreWatchTest {
    private static final String CAVE = "gfx/tiles/rocks/cave";
    private static final String IRON = "gfx/tiles/rocks/hematite";
    private static final Coord TILE = new Coord(-12, 8);

    @Test
    void stopsOnlyForSelectedOreRevealedAfterKnownNonOre() {
        Map<Coord, String> map = new HashMap<>();
        map.put(TILE, CAVE);
        OreWatch watch = new OreWatch(Set.of("hematite"));
        assertNull(watch.observe(TILE, map::get));
        map.put(TILE, IRON);
        assertNotNull(watch.observe(TILE, map::get));
        assertEquals(watch.found(), watch.observe(TILE.add(1, 0), map::get));
    }

    @Test
    void alreadyVisibleAndUnselectedOreDoNotStop() {
        Map<Coord, String> map = new HashMap<>();
        map.put(TILE, IRON);
        OreWatch alreadyVisible = new OreWatch(Set.of("hematite"));
        assertNull(alreadyVisible.observe(TILE, map::get));
        OreWatch unselected = new OreWatch(Set.of("galena"));
        map.put(TILE, CAVE);
        assertNull(unselected.observe(TILE, map::get));
        map.put(TILE, IRON);
        assertNull(unselected.observe(TILE, map::get));
    }

    @Test
    void loadingIsNotEvidenceAndLateRevealIsCaughtByOverlappingScan() {
        Map<Coord, String> map = new HashMap<>();
        OreWatch watch = new OreWatch(Set.of("hematite"));
        assertNull(watch.observe(TILE, map::get));
        map.put(TILE, IRON);
        assertNull(watch.observe(TILE, map::get));

        map.put(TILE, CAVE);
        assertNull(watch.observe(TILE, map::get));
        map.remove(TILE); // Loading does not erase the known non-ore observation.
        assertNull(watch.observe(TILE.add(1, 0), map::get));
        map.put(TILE, IRON);
        assertNotNull(watch.observe(TILE.add(2, 0), map::get));
    }

    @Test
    void selectedKeysAreSnapshottedAndEmptyOrInvalidMeansNoStop() {
        Map<Coord, String> map = new HashMap<>();
        map.put(TILE, CAVE);
        Set<String> selected = new HashSet<>(Set.of("hematite", "invalid"));
        OreWatch watch = new OreWatch(selected);
        selected.clear();
        assertNull(watch.observe(TILE, map::get));
        map.put(TILE, IRON);
        assertNotNull(watch.observe(TILE, map::get));

        for (Set<String> keys : new Set[]{Set.of(), Set.of("invalid")}) {
            OreWatch disabled = new OreWatch(keys);
            map.put(TILE, CAVE);
            disabled.observe(TILE, map::get);
            map.put(TILE, IRON);
            assertNull(disabled.observe(TILE, map::get));
        }
    }

    @Test
    void latchedDiscoveryStopsAllThreeMinersBeforeWorldActionAndBeforeOptionalWing() throws Exception {
        NUI previousUI = ThreadLocalUI.get();
        NConfig previousConfig = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NUI ui = allocate(NUI.class);
            RecordingGameUI gui = allocate(RecordingGameUI.class);
            ui.gui = gui;
            ThreadLocalUI.set(ui);

            OreWatch watch = new OreWatch(Set.of("hematite"));
            Map<Coord, String> map = new HashMap<>();
            map.put(TILE, CAVE);
            watch.observe(TILE, map::get);
            map.put(TILE, IRON);
            assertNotNull(watch.observe(TILE, map::get));

            TunnelingBot ordinary = new TunnelingBot();
            Field watcher = TunnelingBot.class.getDeclaredField("oreWatch");
            watcher.setAccessible(true);
            watcher.set(ordinary, watch);
            assertFalse(ordinary.mineTileIfNeeded(null, TILE).IsSuccess());
            Method ordinaryRoadMine = TunnelingBot.class.getDeclaredMethod("mineRoadTile", NGameUI.class, Coord.class);
            ordinaryRoadMine.setAccessible(true);
            assertFalse(((Results) ordinaryRoadMine.invoke(ordinary, null, TILE)).IsSuccess());

            Method wing = TunnelingBot.class.getDeclaredMethod("mineWings", NGameUI.class,
                    Coord2d.class, Direction.class, WingOption.class, TunnelSide.class, int.class);
            wing.setAccessible(true);
            assertFalse(((Results) wing.invoke(ordinary, null, null, Direction.NORTH,
                    WingOption.EAST, TunnelSide.WEST, 5)).IsSuccess());

            FrameTunneler frame = new FrameTunneler(Direction.NORTH, SupportType.TIMBER_TUNNEL,
                    TunnelSide.EAST, false, watch);
            Method frameMine = FrameTunneler.class.getDeclaredMethod("mineTileIfNeeded", NGameUI.class, Coord.class);
            frameMine.setAccessible(true);
            assertFalse(((Results) frameMine.invoke(frame, null, TILE)).IsSuccess());
            Method frameRoadMine = FrameTunneler.class.getDeclaredMethod("mineRoadTile", NGameUI.class, Coord.class);
            frameRoadMine.setAccessible(true);
            assertFalse(((Results) frameRoadMine.invoke(frame, null, TILE)).IsSuccess());

            MinesweeperMiner sweep = new MinesweeperMiner(Direction.NORTH, 5, watch);
            Method sweepMine = MinesweeperMiner.class.getDeclaredMethod("mineTile", NGameUI.class,
                    MinesweeperSolver.class, Coord.class);
            sweepMine.setAccessible(true);
            assertFalse(((Results) sweepMine.invoke(sweep, null, null, TILE)).IsSuccess());
            assertEquals(6, gui.errorCount);
        } finally {
            if (previousUI == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previousUI);
            NConfig.current = previousConfig;
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
