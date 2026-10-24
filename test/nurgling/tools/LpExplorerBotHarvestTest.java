package nurgling.tools;

import haven.UI;
import nurgling.NUI;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class LpExplorerBotHarvestTest {
    private static UI session() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (NUI) ((Unsafe) field.get(null)).allocateInstance(NUI.class);
    }

    @Test
    void oneBotHarvestCreditsOnlyItsProductInItsSessionAndCharacter() throws Exception {
        UI a = session(), b = session();
        LpExplorer.BotHarvest token = LpExplorer.armBotHarvest(a, "alice",
                "gfx/terobjs/trees/figtreelog", "Board of Figwood", 1000);
        assertFalse(LpExplorer.consumeBotHarvest(b, "alice", "gfx/terobjs/trees/figtreelog", "Board of Figwood", 1001));
        assertFalse(LpExplorer.consumeBotHarvest(a, "bob", "gfx/terobjs/trees/figtreelog", "Board of Figwood", 1001));
        assertFalse(LpExplorer.consumeBotHarvest(a, "alice", "gfx/terobjs/trees/planetreelog", "Board of Figwood", 1001));
        assertFalse(LpExplorer.consumeBotHarvest(a, "alice", "gfx/terobjs/trees/figtreelog", "Block of Figwood", 1001));
        assertTrue(LpExplorer.consumeBotHarvest(a, "alice", "gfx/terobjs/trees/figtreelog", "Board of Figwood", 1001));
        assertFalse(LpExplorer.consumeBotHarvest(a, "alice", "gfx/terobjs/trees/figtreelog", "Board of Figwood", 1002));
        LpExplorer.clearBotHarvest(a, token);
    }

    @Test
    void expiryAndOldCleanupDoNotEraseAReplacementHarvest() throws Exception {
        UI a = session();
        LpExplorer.BotHarvest old = LpExplorer.armBotHarvest(a, "alice", "tree", "Fig", 1000);
        assertFalse(LpExplorer.consumeBotHarvest(a, "alice", "tree", "Fig", 71_001));
        LpExplorer.BotHarvest replacement = LpExplorer.armBotHarvest(a, "alice", "tree", "Fig", 80_000);
        LpExplorer.clearBotHarvest(a, old);
        assertTrue(LpExplorer.consumeBotHarvest(a, "alice", "tree", "Fig", 92_000));
        LpExplorer.clearBotHarvest(a, replacement);
        assertNull(LpExplorer.armBotHarvest(null, "alice", "tree", "Fig", 0));
    }
}
