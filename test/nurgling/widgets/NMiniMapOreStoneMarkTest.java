package nurgling.widgets;

import haven.Coord;
import nurgling.actions.bots.MasterMiner;
import nurgling.conf.ProspectKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiniMapOreStoneMarkTest {

    @Test
    void clayIsNotOreSpotOrStone() {
        LabeledMinimapMark clay = mark("Clay");
        assertEquals(ProspectKind.CLAY, clay.kind);
        assertFalse(MasterMiner.isOre("Clay"));
        assertFalse(MasterMiner.isStone("Clay"));
        assertFalse(MasterMiner.isGemstone("Clay"));
    }

    @Test
    void graniteIsStoneNotOre() {
        LabeledMinimapMark granite = mark("Granite");
        assertEquals(ProspectKind.STONE, granite.kind);
        assertFalse(MasterMiner.isOre("Granite"));
        assertTrue(MasterMiner.isStone("Granite"));
    }

    @Test
    void cassiteriteIsOreNotStone() {
        LabeledMinimapMark cassiterite = mark("Cassiterite");
        assertEquals(ProspectKind.ORE, cassiterite.kind);
        assertTrue(MasterMiner.isOre("Cassiterite"));
        assertFalse(MasterMiner.isStone("Cassiterite"));
    }

    @Test
    void quarryartzIsNeitherOreNorStone() {
        LabeledMinimapMark quarry = mark("Quarryartz");
        assertEquals(ProspectKind.OTHER, quarry.kind);
        assertFalse(MasterMiner.isOre("Quarryartz"));
        assertFalse(MasterMiner.isStone("Quarryartz"));
        assertFalse(MasterMiner.isGemstone("Quarryartz"));
    }

    @Test
    void skipLoopsApplyProspectSettingsToMinerals() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NMiniMap.java"), StandardCharsets.UTF_8);
        String cfg = Files.readString(Path.of("src/nurgling/NConfig.java"), StandardCharsets.UTF_8);
        String ore = method(src, "static boolean isOreSpotMark");
        String stone = method(src, "static boolean isStoneMark");
        String skip = method(src, "private boolean skipHiddenLabeledMark");
        assertTrue(src.contains("skipHiddenLabeledMark"));
        assertTrue(ore.contains("isAnimalMark(mark)") && ore.contains("isForageMark(mark)"));
        assertTrue(ore.contains("\"Quarryartz\""));
        assertTrue(ore.contains("mark.kind == ProspectKind.ORE"));
        assertTrue(ore.contains("MasterMiner.isOre"));
        assertTrue(stone.contains("mark.kind == ProspectKind.STONE"));
        assertTrue(stone.contains("MasterMiner.isStone"));
        assertTrue(skip.contains("showProspectKind(ProspectKind.ORE)"));
        assertTrue(skip.contains("showProspectKind(ProspectKind.GEM)"));
        assertTrue(skip.contains("showProspectKind(ProspectKind.STONE)"));
        assertFalse(src.contains("public boolean showOreSpotIcons"));
        assertFalse(src.contains("public boolean showGemstoneIcons"));
        assertFalse(src.contains("public boolean showStoneIcons"));
        assertTrue(src.contains("isOreSpotMark(labeledMark) || isStoneMark(labeledMark)"));
        assertFalse(src.contains("&& !isOreSpotMark(mark) && !isGemstoneMark(mark.resourceType)"));
        assertTrue(cfg.contains("showStoneIcons,"));
        assertTrue(cfg.contains("conf.put(Key.showStoneIcons, true)"));
        assertTrue(cfg.contains("showOreSpotIcons,"));
        assertTrue(cfg.contains("showGemstoneIcons,"));
    }

    private static String method(String src, String sig) {
        int at = src.indexOf(sig);
        assertTrue(at >= 0, "missing " + sig);
        int brace = src.indexOf('{', at);
        int depth = 0;
        for (int i = brace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0)
                    return src.substring(at, i + 1);
            }
        }
        throw new AssertionError("unclosed " + sig);
    }

    private static LabeledMinimapMark mark(String resourceType) {
        return new LabeledMinimapMark("q40", resourceType, 40.0, 1L, new Coord(1, 1), null);
    }
}
