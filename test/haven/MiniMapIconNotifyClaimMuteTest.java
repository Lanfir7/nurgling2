package haven;

import nurgling.tools.ClaimLand;
import nurgling.tools.DefaultAnimalAlarms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMapIconNotifyClaimMuteTest {
    private static final String BEAR = "gfx/invobjs/kritter/bear";

    @Test
    void claimMuteGateMatchesClaimLandHelper() {
        assertEquals(ClaimLand.shouldPlayIconNotify(true), MiniMapIconPolicy.shouldPlayIconNotify(true));
        assertEquals(ClaimLand.shouldPlayIconNotify(false), MiniMapIconPolicy.shouldPlayIconNotify(false));
        assertFalse(MiniMapIconPolicy.shouldPlayIconNotify(true));
        assertTrue(MiniMapIconPolicy.shouldPlayIconNotify(false));
    }

    @Test
    void fireOnClaimDropsPendingSoundAndKeepsVisual() {
        AtomicInteger played = new AtomicInteger();
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(played::incrementAndGet);

        MiniMapIconPolicy.fireIconNotify(state, true, "idle", BEAR);

        assertEquals(0, played.get());
        assertFalse(state.isPending());
        assertTrue(state.isVisualActive());
    }

    @Test
    void fireInWildernessPlaysOnce() {
        AtomicInteger played = new AtomicInteger();
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(played::incrementAndGet);

        MiniMapIconPolicy.fireIconNotify(state, false, "idle", BEAR);
        MiniMapIconPolicy.fireIconNotify(state, false, "idle", BEAR);

        assertEquals(1, played.get());
        assertFalse(state.isPending());
        assertTrue(state.isVisualActive());
    }

    @Test
    void walkingOntoClaimBeforePlayDropsPending() {
        AtomicInteger played = new AtomicInteger();
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(played::incrementAndGet);

        MiniMapIconPolicy.fireIconNotify(state, false, null, BEAR);
        assertTrue(state.isPending());
        assertEquals(0, played.get());

        MiniMapIconPolicy.fireIconNotify(state, true, "idle", BEAR);

        assertEquals(0, played.get());
        assertFalse(state.isPending());
        assertTrue(state.isVisualActive());
    }

    @Test
    void displayIconNotifyPathUsesClaimGateAtPlayTime() throws Exception {
        String src = Files.readString(Path.of("src/haven/MiniMap.java"));
        assertTrue(src.contains("MiniMapIconPolicy.fireIconNotify"),
                "DisplayIcon must mute via MiniMapIconPolicy.fireIconNotify");
        assertTrue(src.contains("ClaimLand.isOnClaimOrVillage"),
                "play-time gate must check player claim/village overlay");
        assertFalse(src.contains("SettingsWindow"),
                "must not route preview play through the minimap mute");
    }
}
