package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level checks so MacroKey butcher can dump via FreeInventory2
 * without constructing Gob / NGameUI.
 */
class ButcherDumpInventoryTest {

    @Test
    void singleUsesDumpFlagFromPlayerOutAreas() throws Exception {
        String src = butcherSrc();
        assertTrue(src.contains("ButcherTarget.dumpInventory(mode, ButcherTarget.hasOutAreas("), src);
        assertTrue(src.contains("gui.map.nols"), src);
        assertTrue(src.contains("cand.jout"), src);
        assertTrue(src.contains("listOf(gob)"), src);
        assertFalse(src.contains("return butcherGobs(gui, listOf(gob), null, false);"), src);
        assertTrue(src.contains("return butcherGobs(gui, getGobs(insa.getRCArea()), null, false);"), src);
        assertTrue(src.contains("return butcherGobs(gui, getGobs(zone), zone, true);"), src);
    }

    @Test
    void singleDoesNotSweepDeadkritterZone() throws Exception {
        String src = butcherSrc();
        String single = slice(src, "if (mode == ButcherTarget.Mode.SINGLE)", "if (mode == ButcherTarget.Mode.ZONE)");
        assertTrue(single.contains("listOf(gob)"), single);
        assertFalse(single.contains("getGobs(zone)"), single);
        assertFalse(single.contains("navigateToArea"), single);
        assertFalse(single.contains("Validator"), single);
        assertTrue(src.contains("new Butcher(gob)") || actionSrc().contains("return new Butcher(gob);"), src);
    }

    @Test
    void dumpReusesFreeInventory2AndStillHandDropsBones() throws Exception {
        String src = butcherSrc();
        assertTrue(src.contains("new FreeInventory2(context).run(gui)"), src);
        assertTrue(src.contains("NUtils.drop(gui.vhand)"), src);
        assertTrue(src.contains("if (dumpInventory) {\n                                    new FreeInventory2(context).run(gui);"), src);
        assertTrue(src.indexOf("NUtils.drop(gui.vhand)") < src.lastIndexOf("new FreeInventory2(context)"), src);
        assertFalse(src.contains("Cupboard"), src);
        assertFalse(src.contains("chest"), src);
        assertFalse(src.contains("crate"), src);
    }

    @Test
    void petalOrderAndMacroKeyBindingStayTheSame() throws Exception {
        String src = butcherSrc();
        int skin = src.indexOf("order.add(\"Skin\");");
        int scale = src.indexOf("order.add(\"Scale\");");
        int crack = src.indexOf("order.add(\"Crack\");");
        int clean = src.indexOf("order.add(\"Clean\");");
        int butcher = src.indexOf("order.add(\"Butcher\");");
        int bones = src.indexOf("order.add(\"Collect bones\");");
        assertTrue(skin >= 0 && skin < scale && scale < crack && crack < clean && clean < butcher && butcher < bones, src);
        String action = actionSrc();
        assertTrue(action.contains("return new Butcher(gob);"), action);
        assertFalse(action.contains("dumpInventory"), action);
    }

    private static String butcherSrc() throws Exception {
        return read("src/nurgling/actions/bots/Butcher.java");
    }

    private static String actionSrc() throws Exception {
        return read("src/nurgling/contextmenu/ButcherAction.java");
    }

    private static String slice(String src, String from, String to) {
        int start = src.indexOf(from);
        int end = src.indexOf(to, start + 1);
        assertTrue(start >= 0 && end > start, src);
        return src.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
