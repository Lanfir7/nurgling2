package nurgling.tools;

import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CheckGridsStateTest {
    @Test
    void ownerIdentityIgnoresActiveVisualSessionAndRejectsDisposedOrReboundObjects() {
        Object map = new Object(), ui = new Object(), gui = new Object(), glob = new Object();
        Object otherSession = new Object();
        NUI previous = ThreadLocalUI.get();
        try {
            ThreadLocalUI.clear(); // No active visual session is required for identity checks.
            assertTrue(CheckGridsState.sameOwner(map, ui, gui, ui, ui, gui, map, glob, glob, true, true));
            assertFalse(CheckGridsState.sameOwner(map, ui, gui, ui, ui, otherSession, map, glob, glob, true, true));
            assertFalse(CheckGridsState.sameOwner(map, ui, gui, ui, ui, gui, map, glob, otherSession, true, true));
            assertFalse(CheckGridsState.sameOwner(map, ui, gui, ui, ui, gui, map, glob, glob, false, true));
            assertFalse(CheckGridsState.sameOwner(map, ui, gui, ui, ui, gui, map, glob, glob, true, false));
            assertFalse(CheckGridsState.sameOwner(map, ui, gui, ui, ui, null, map, glob, glob, true, true));
        } finally {
            if(previous == null) ThreadLocalUI.clear();
            else ThreadLocalUI.set(previous);
        }
    }

    @Test
    void cancelledWorkRestoresPreviousThreadSession() throws Exception {
        NUI owner = bareUi(), previous = bareUi();
        NUI original = ThreadLocalUI.get();
        Constructor<CheckGridsState> constructor = CheckGridsState.class.getDeclaredConstructor(
                NMapView.class, NUI.class, NGameUI.class);
        constructor.setAccessible(true);
        try {
            ThreadLocalUI.set(previous);
            constructor.newInstance(null, owner, null).run();
            assertSame(previous, ThreadLocalUI.get());
            ThreadLocalUI.clear();
            constructor.newInstance(null, owner, null).run();
            assertNull(ThreadLocalUI.get());
        } finally {
            if(original == null) ThreadLocalUI.clear();
            else ThreadLocalUI.set(original);
        }
    }

    private static NUI bareUi() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (NUI)((Unsafe)field.get(null)).allocateInstance(NUI.class);
    }

    @Test
    void movementChecksKeepOnlyLatestPendingRun() throws Exception {
        Method factory = CheckGridsState.class.getDeclaredMethod("createExecutor");
        factory.setAccessible(true);
        ExecutorService executor = (ExecutorService)factory.invoke(null);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> completed = new CopyOnWriteArrayList<>();

        try {
            executor.execute(() -> {
                firstStarted.countDown();
                try { releaseFirst.await(); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                completed.add(1);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> completed.add(2));
            executor.execute(() -> completed.add(3));
            releaseFirst.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertEquals(Arrays.asList(1, 3), completed);
    }
}
