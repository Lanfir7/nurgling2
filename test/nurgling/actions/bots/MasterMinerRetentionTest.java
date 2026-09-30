package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerRetentionTest {
    @Test
    void observedStackHolderDoesNotStayAliveOnlyBecauseItWasObserved() throws Exception {
        Set<Object> observed = observedHolders(new MasterMiner());
        WeakReference<Object> holder = observeAndRelease(observed);

        for (int attempt = 0; holder.get() != null && attempt < 30; attempt++) {
            System.gc();
            byte[][] pressure = new byte[4][];
            for (int i = 0; i < pressure.length; i++) pressure[i] = new byte[256 * 1024];
            Thread.sleep(10);
        }

        assertTrue(holder.get() == null, "observed holders should be weakly retained");
        assertTrue(observed.isEmpty(), "collected holders should leave the observed set");
    }

    @Test
    void liveHolderRemainsObservedAcrossWidgetRecreation() throws Exception {
        Set<Object> observed = observedHolders(new MasterMiner());
        Object holder = new Object();

        assertTrue(MasterMiner.isFirstPopulatedContentsSnapshot(observed.contains(holder), 1));
        observed.add(holder);
        assertFalse(MasterMiner.isFirstPopulatedContentsSnapshot(observed.contains(holder), 1));
        assertTrue(observed.contains(holder));
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> observedHolders(MasterMiner miner) throws Exception {
        Field field = MasterMiner.class.getDeclaredField("stackContentsObserved");
        field.setAccessible(true);
        return (Set<Object>) field.get(miner);
    }

    private static WeakReference<Object> observeAndRelease(Set<Object> observed) {
        Object holder = new Object();
        observed.add(holder);
        return new WeakReference<>(holder);
    }
}
