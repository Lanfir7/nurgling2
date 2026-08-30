package haven;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLoadTrackerTest {
    @Test
    void reportsOneEpisodeWithDurationAndChangingReasons() throws Exception {
        List<String> messages = new ArrayList<>();
        Class<?> type = Class.forName("haven.WorldLoadTracker");
        Constructor<?> constructor = type.getDeclaredConstructor(Consumer.class);
        constructor.setAccessible(true);
        Object tracker = constructor.newInstance((Consumer<String>) messages::add);
        Method blocked = type.getDeclaredMethod("blocked", String.class, String.class, long.class);
        Method ready = type.getDeclaredMethod("ready", long.class);
        blocked.setAccessible(true);
        ready.setAccessible(true);

        blocked.invoke(tracker, "Waiting for map data", "MCache.getgrid", 1_000_000L);
        blocked.invoke(tracker, "Waiting for map data", "MCache.getgrid", 2_000_000L);
        blocked.invoke(tracker, "Building map", "MapMesh.build", 3_000_000L);
        ready.invoke(tracker, 6_000_000L);
        ready.invoke(tracker, 7_000_000L);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("Waiting for map data @ MCache.getgrid"));
        assertTrue(messages.get(1).contains("5 ms"));
        assertTrue(messages.get(1).contains("Waiting for map data @ MCache.getgrid"));
        assertTrue(messages.get(1).contains("Building map @ MapMesh.build"));
    }
}
