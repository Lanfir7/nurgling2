package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level checks so this file compiles without the client
 * (Widget, GOut, NGameUI, ...).
 */
class NMakewindowCraftQualityTest {

    @Test
    void layoutConstantsMoveDownByOneQualityLine() throws Exception {
        String src = read("src/nurgling/widgets/NMakewindow.java");
        assertTrue(src.contains("CraftSlotQuality.LINE"), src);
        assertTrue(src.contains("38 + CraftSlotQuality.LINE"), src);
        assertTrue(src.contains("65 + CraftSlotQuality.LINE"), src);
        assertTrue(src.contains("75 + CraftSlotQuality.LINE"), src);
        assertTrue(src.contains("82 + CraftSlotQuality.LINE"), "craft_num y must shift: " + src);
        assertTrue(src.contains("pack()") && src.contains("CraftSlotQuality.LINE"), src);
        assertTrue(src.contains("packedHeight"), "pack() must use idempotent packedHeight: " + src);
        assertFalse(src.contains("sz.add(0, UI.scale(CraftSlotQuality.LINE))"),
                "sz.add stacks a LINE on every pack(): " + src);
        assertFalse(src.contains("qmy = UI.scale(38), outy = UI.scale(65)"),
                "old layout would overlap the extra quality line: " + src);
    }

    @Test
    void ingredientAveragesExpandCategoryViaNamesFor() throws Exception {
        String src = read("src/nurgling/widgets/NMakewindow.java");
        int start = src.indexOf("private List<Double> ingredientAverages()");
        int end = src.indexOf("private List<InvSample> playerInvSamples()");
        assertTrue(start >= 0 && end > start, src);
        String avg = src.substring(start, end);
        assertTrue(avg.contains("CraftIngredientStock.namesFor"),
                "category slots must expand via namesFor: " + avg);
        assertTrue(avg.contains("VSpec.categories.containsKey"),
                "must treat VSpec keys as categories: " + avg);
        assertFalse(avg.contains("slotMatchName"),
                "single slotMatchName cannot expand VSpec members: " + avg);
    }

    @Test
    void averagesComeFromPlayerInventoryMakePrepNotChests() throws Exception {
        String src = read("src/nurgling/widgets/NMakewindow.java");
        assertTrue(src.contains("gui.getInventory()"), "player inventory only: " + src);
        assertTrue(src.contains("isMakePrepClass") && src.contains("info()"),
                "must detect resource-loaded MakePrep via info(), not only NMakewindow.MakePrep: " + src);
        assertFalse(src.contains("getInventory(\""), "must not scan named chests: " + src);
        assertFalse(src.contains("getWindows"), src);
        // Quality scan must not walk every open container inventory.
        assertFalse(src.contains("gui.getInventory(wnd") || src.contains("getInventory(cap"), src);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
