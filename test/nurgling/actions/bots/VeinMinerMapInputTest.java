package nurgling.actions.bots;

import haven.KeyMatch;
import nurgling.hotkeys.Hotkeys;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerMapInputTest {
    @Test
    void ctrlLeftClickUsesTheVeinMinerGestureOnly() {
        assertTrue(Hotkeys.action(Hotkeys.WORLD_VEIN_MINER).current().matchesMouse(1, KeyMatch.C));
        assertFalse(Hotkeys.action(Hotkeys.WORLD_VEIN_MINER).current().matchesMouse(1, 0));
        assertFalse(Hotkeys.action(Hotkeys.WORLD_VEIN_MINER).current().matchesMouse(1, KeyMatch.S));
        assertFalse(Hotkeys.action(Hotkeys.WORLD_VEIN_MINER).current().matchesMouse(3, KeyMatch.C));
    }

    @Test
    void veinMinerShortcutPrecedesTheMiningSelector() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/NMapView.java"));
        String shortcut = "if (Hotkeys.action(Hotkeys.WORLD_VEIN_MINER).current().matchesMouse(ev.b, ui.modflags())";
        int veinMiner = source.indexOf(shortcut);
        int modalSelector = source.indexOf("if(hasModalMouseGrab()) return super.mousedown(ev);");

        assertTrue(veinMiner >= 0, "the map handler must contain the Vein Miner shortcut condition");
        assertTrue(modalSelector >= 0, "fixture must contain the modal selector guard");
        assertTrue(veinMiner < modalSelector,
                "Ctrl+click must be handled before the mine selector consumes the click");
        String handler = source.substring(veinMiner, modalSelector);
        assertTrue(handler.contains("VeinMiner.isMineCursor()"));
        assertTrue(handler.contains("BotExecutor.runAsync(\"VeinMiner\", new VeinMiner(tile))"));
    }
}
