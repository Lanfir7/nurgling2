package nurgling.actions.bots.silk;

import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.actions.Results;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SilkProductionBotTest {
    @Test
    void failedBreedingFillNeverCullsRemainingCocoons() throws Exception {
        AtomicInteger culled = new AtomicInteger();
        NUI previous = ThreadLocalUI.get();
        RecordingGameUI game = (RecordingGameUI) unsafe().allocateInstance(RecordingGameUI.class);
        game.errors = new ArrayList<>();
        NUI ui = (NUI) unsafe().allocateInstance(NUI.class);
        ui.gui = game;
        ThreadLocalUI.set(ui);
        try {
            Results result = SilkProductionBot.depositThenCull(game,
                    gui -> Results.ERROR("Source quality unreadable"),
                    gui -> { culled.incrementAndGet(); return Results.SUCCESS(); });

            assertFalse(result.IsSuccess());
            assertEquals(0, culled.get());
            assertEquals(List.of("Source quality unreadable"), game.errors);
        } finally {
            if (previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
        }
    }

    @Test
    void completedBreedingFillCullsOnce() throws Exception {
        AtomicInteger culled = new AtomicInteger();

        Results result = SilkProductionBot.depositThenCull(null,
                gui -> Results.SUCCESS(),
                gui -> { culled.incrementAndGet(); return Results.SUCCESS(); });

        assertTrue(result.IsSuccess());
        assertEquals(1, culled.get());
    }

    private static final class RecordingGameUI extends NGameUI {
        List<String> errors;
        private RecordingGameUI() { super("test", 0, "test", null); }
        @Override public void error(String message) { errors.add(message); }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
