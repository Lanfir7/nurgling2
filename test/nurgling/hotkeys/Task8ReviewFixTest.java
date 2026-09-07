package nurgling.hotkeys;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class Task8ReviewFixTest {
    @Test void woundAndWheelHandlersDispatchResolvedActionIds() throws Exception {
        String wound = source("src/nurgling/NWoundBox.java");
        String fight = source("src/haven/FightWnd.java");
        String stack = source("src/haven/res/ui/stackinv/ItemStack.java");
        String stockpile = source("src/haven/ISBox.java");
        assertTrue(wound.contains("WOUND_FIND_TREATMENT_STORAGE"));
        assertTrue(fight.contains("COMBAT_ACTION_POINTS_INCREASE") && fight.contains("COMBAT_ACTION_POINTS_DECREASE"));
        assertTrue(stack.contains("INVENTORY_STACK_TRANSFER_TO_MAIN") && stack.contains("INVENTORY_STACK_TRANSFER_FROM_MAIN"));
        assertTrue(stockpile.contains("Hotkeys.STOCKPILE_TRANSFER_OUT") && stockpile.contains("Hotkeys.STOCKPILE_TRANSFER_IN"));
        assertTrue(stockpile.contains("wdgmsg(\"xfer2\", -1, 0)"));
        assertTrue(stockpile.contains("wdgmsg(\"xfer2\", 1, 0)"));
    }

    @Test void mapMarkerWaypointAndDeleteUseCatalogActions() throws Exception {
        String mapWnd = source("src/haven/MapWnd.java");
        String minimap = source("src/nurgling/widgets/NMiniMap.java");
        assertTrue(mapWnd.contains("Hotkeys.action(Hotkeys.MAP_MARKER_WAYPOINT)"));
        assertTrue(minimap.contains("Hotkeys.action(Hotkeys.MAP_MARKER_DELETE)"));
        assertFalse(minimap.contains("(ui.modflags() & UI.MOD_SHIFT) != 0"));
    }

    @Test void mapClickBuildsServerArgsAfterCanonicalModifierSelection() throws Exception {
        String source = source("src/haven/MapView.java");
        int args = source.indexOf("Object[] args = {pc, mc.floor(posres), clickb, modflags}");
        int ping = source.indexOf("boolean worldPing");
        int waypoint = source.indexOf("Hotkeys.action(Hotkeys.WORLD_QUEUE_WAYPOINT).canonicalMods()");
        assertTrue(ping >= 0 && waypoint >= 0 && args > ping && args > waypoint);
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
