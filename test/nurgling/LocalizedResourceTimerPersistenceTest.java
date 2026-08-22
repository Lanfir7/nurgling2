package nurgling;

import haven.Coord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedResourceTimerPersistenceTest {
    private static final long READY_MS = LocalizedResourceTimer.BOUGH_PYRE_READY_MS;
    private static final long AUTO_REMOVE_MS = LocalizedResourceTimer.BOUGH_PYRE_AUTO_REMOVE_MS;

    @Test
    void serializeKeepsCountdownAndReadyPyres() {
        LocalizedResourceTimer countdown = pyre("cd", System.currentTimeMillis() - 5 * 60 * 1000L);
        LocalizedResourceTimer ready = pyre("rd", System.currentTimeMillis() - READY_MS - 1000L);
        LocalizedResourceTimer gone = pyre("gn", System.currentTimeMillis() - AUTO_REMOVE_MS - 1000L);

        JSONObject doc = LocalizedResourceTimerService.serializeTimers(Arrays.asList(countdown, ready, gone));
        List<LocalizedResourceTimer> loaded = LocalizedResourceTimerService.deserializeTimers(doc);

        assertEquals(2, loaded.size());
        assertTrue(ids(loaded).contains(countdown.getResourceId()));
        assertTrue(ids(loaded).contains(ready.getResourceId()));
        assertFalse(ids(loaded).contains(gone.getResourceId()));
    }

    @Test
    void mergeDiskCountdownIntoEmptyMemory() {
        LocalizedResourceTimer countdown = pyre("cd", System.currentTimeMillis() - 60 * 1000L);
        Map<String, LocalizedResourceTimer> memory = new HashMap<>();

        LocalizedResourceTimerService.mergeMissing(memory, Arrays.asList(countdown));

        assertEquals(1, memory.size());
        assertTrue(memory.get(countdown.getResourceId()).shouldPersist());
        assertFalse(memory.get(countdown.getResourceId()).isExpired());
    }

    @Test
    void disposeWithoutLocalEditsMustNotWipeDiskTimers() {
        assertFalse(LocalizedResourceTimerService.shouldSaveOnDispose(false));
        assertTrue(LocalizedResourceTimerService.shouldSaveOnDispose(true));
    }

    private static LocalizedResourceTimer pyre(String suffix, long start) {
        return new LocalizedResourceTimer(
                "res_1_10_20_nurgling_boughpyre_" + suffix, 1L, new Coord(10, 20), "Bough Pyre",
                LocalizedResourceTimer.BOUGH_PYRE_TYPE, start, READY_MS, "Bough Pyre",
                AUTO_REMOVE_MS, LocalizedResourceTimer.BOUGH_PYRE_ICON);
    }

    private static Collection<String> ids(List<LocalizedResourceTimer> timers) {
        List<String> ids = new ArrayList<>();
        for (LocalizedResourceTimer timer : timers) {
            ids.add(timer.getResourceId());
        }
        return ids;
    }
}
