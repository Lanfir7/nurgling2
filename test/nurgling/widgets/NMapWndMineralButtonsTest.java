package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMapWndMineralButtonsTest {

    @Test
    void usesNewMineralButtonsWithoutLegacyDuplicates() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NMapWnd.java"), StandardCharsets.UTF_8);
        int ctor = src.indexOf("public NMapWnd(");
        int end = src.indexOf("placeDbButtons();", ctor);
        assertTrue(ctor >= 0 && end > ctor);
        String body = src.substring(ctor, end);

        assertTrue(index(body, "mapToolsBtn") < index(body, "fishBtn"));
        assertTrue(index(body, "fishBtn") < index(body, "treeBtn"));
        assertTrue(index(body, "treeBtn") < index(body, "oreBtn"));
        assertTrue(index(body, "oreBtn") < index(body, "gemBtn"));
        assertTrue(index(body, "gemBtn") < index(body, "stoneBtn"));
        assertTrue(index(body, "stoneBtn") < index(body, "animalsBtn"));
        assertTrue(index(body, "animalsBtn") < index(body, "foragingBtn"));
        assertTrue(index(body, "foragingBtn") < index(body, "vectorClearBtn"));

        assertTrue(body.contains("oreBtn = add(new MapToggleButton(\"ores\""));
        assertTrue(body.contains("gemBtn = add(new MapToggleButton(\"gems\""));
        assertTrue(body.contains("stoneBtn = add(new MapToggleButton(\"stone\""));
        assertTrue(body.contains("openMineralSearch(ProspectKind.ORE)"));
        assertTrue(body.contains("openMineralSearch(ProspectKind.GEM)"));
        assertTrue(body.contains("openMineralSearch(ProspectKind.STONE)"));
        assertTrue(body.contains("oreBtn.state(() -> NMiniMap.showProspectKind(ProspectKind.ORE))"));
        assertTrue(body.contains("oreBtn.set(val -> NMiniMap.showProspectKind(ProspectKind.ORE, val))"));
        assertTrue(body.contains("gemBtn.state(() -> NMiniMap.showProspectKind(ProspectKind.GEM))"));
        assertTrue(body.contains("gemBtn.set(val -> NMiniMap.showProspectKind(ProspectKind.GEM, val))"));
        assertTrue(body.contains("stoneBtn.state(() -> NMiniMap.showProspectKind(ProspectKind.STONE))"));
        assertTrue(body.contains("stoneBtn.set(val -> NMiniMap.showProspectKind(ProspectKind.STONE, val))"));
        assertFalse(body.contains("oresBtn"));
        assertFalse(body.contains("prospectBtn"));
        assertFalse(body.contains("quarryartzBtn"));
        assertFalse(body.contains("oreSpotsBtn"));
        assertFalse(body.contains("gemstoneBtn"));
        assertFalse(body.contains("openOresSearch"));
        assertFalse(body.contains("openProspectingSearch"));
        assertFalse(body.contains("openQuarryartzSearch"));
        assertFalse(body.contains("oreBtn.a ="));
        assertFalse(body.contains("gemBtn.a ="));
        assertFalse(body.contains("stoneBtn.a ="));
        assertFalse(body.contains("MinesweeperOverlay"));
        assertFalse(src.contains("MinesweeperOverlay"));
    }

    private static int index(String src, String name) {
        int at = src.indexOf(name + " =");
        assertTrue(at >= 0, "missing assignment for " + name);
        return at;
    }
}
