package nurgling.overlays.map;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCustomOverlayTickTest {
    private Object miningVisible;
    private Object rocksVisible;

    @BeforeEach
    void rememberSettings() {
        NConfig.getGlobalInstance();
        miningVisible = NConfig.get(NConfig.Key.miningol);
        rocksVisible = NConfig.get(NConfig.Key.highlightRockTiles);
    }

    @AfterEach
    void restoreSettings() {
        NConfig.set(NConfig.Key.miningol, miningVisible);
        NConfig.set(NConfig.Key.highlightRockTiles, rocksVisible);
    }

    @Test
    void mapSnapshotTickRebuildsCoverageWhenAnotherSupportArrives() throws Exception {
        NMiningOverlay mining = bareMiningOverlay();
        // Arrival queued after the already displayed cuts were built.
        mining.forAdd.add(101L);
        NOverlay overlay = mining;

        overlay.tick(Collections.emptyMap());

        assertEquals(1, overlay.cacheRevision());
        assertTrue(mining.forAdd.isEmpty());
        overlay.tick(Collections.emptyMap());
        assertEquals(1, overlay.cacheRevision());
    }

    @Test
    void mapSnapshotTickRemovesCoverageOfAnUnloadedSupport() throws Exception {
        NMiningOverlay mining = bareMiningOverlay();
        mining.curGobs.add(101L); // There is no such gob in this session.
        NOverlay overlay = mining;

        overlay.tick(Collections.emptyMap());

        assertTrue(mining.curGobs.isEmpty());
        assertEquals(1, overlay.cacheRevision());
    }

    @Test
    void mapSnapshotTickRespondsToMiningOverlayToggle() throws Exception {
        NConfig.set(NConfig.Key.miningol, true);
        NOverlay overlay = bareMiningOverlay();

        NConfig.set(NConfig.Key.miningol, false);
        overlay.tick(Collections.emptyMap());

        assertEquals(1, overlay.cacheRevision());
        NConfig.set(NConfig.Key.miningol, true);
        overlay.tick(Collections.emptyMap());
        assertEquals(2, overlay.cacheRevision());
    }

    @Test
    void mapSnapshotTickRespondsToRockHighlightToggle() throws Exception {
        NConfig.set(NConfig.Key.highlightRockTiles, true);
        NRockTileHighlightOverlay rocks = bare(NRockTileHighlightOverlay.class);
        NOverlay overlay = rocks;

        overlay.tick(Collections.emptyMap());

        assertTrue((Boolean)field(rocks, "isEnabled"));
        assertEquals(1, overlay.cacheRevision());
        NConfig.set(NConfig.Key.highlightRockTiles, false);
        overlay.tick(Collections.emptyMap());
        assertFalse((Boolean)field(rocks, "isEnabled"));
        assertEquals(2, overlay.cacheRevision());
    }

    private static NMiningOverlay bareMiningOverlay() throws Exception {
        NMiningOverlay overlay = bare(NMiningOverlay.class);
        set(overlay, "curGobs", new ArrayList<Long>());
        set(overlay, "forAdd", new ArrayList<Long>());
        set(overlay, "forClear", new ArrayList<Long>());
        overlay.isVisible = (Boolean)NConfig.get(NConfig.Key.miningol);
        overlay.shortWallsEnabled = Boolean.TRUE.equals(NConfig.get(NConfig.Key.shortWalls));
        return overlay;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
}
