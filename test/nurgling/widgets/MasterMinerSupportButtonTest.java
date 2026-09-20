package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerSupportButtonTest {
    @Test
    void supportControlUsesTheStoneColumnPaginaIconAndLocalizedTooltip() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/widgets/bots/MasterMinerWnd.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("new Button(UI.scale(32), emptyIcon())"));
        assertTrue(source.contains("paginae/bld/column"));
        assertTrue(source.contains("RichText.render(tipText, UI.scale(260)).tex()"));
        assertTrue(source.contains("private Tex supportTip;"));
        assertTrue(source.contains("supportTip.dispose()"));
        assertTrue(source.contains("public void dispose()"));
        assertTrue(source.contains("PUtils.convolvedown(icon, size, CharWnd.iconfilter)"));
        assertTrue(source.contains("icon.getWidth() <= max && icon.getHeight() <= max"));
        assertTrue(source.contains("Math.min((double) max / icon.getWidth(), (double) max / icon.getHeight())"));
    }

    @Test
    void supportMacroClearsMiningSelectionAndStopsAtTheConstructionWindow() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/actions/bots/MasterMiner.java"), StandardCharsets.UTF_8);
        String collector = source.substring(source.indexOf("class CollectSupportStones"));
        assertTrue(collector.contains("private static final String STONE_COLUMN_NAME = \"Stone Column\";"));
        assertTrue(collector.contains("private static final String STONE_COLUMN_PAGINA = \"paginae/bld/column\";"));
        assertTrue(collector.contains("wdgmsg(\"click\", Coord.z, player.rc.floor(OCache.posres), 3, 0)"));
        assertTrue(collector.contains("WaitPlob.withSoftTimeout"));
        assertTrue(collector.contains("WaitConstructionObject.withSoftTimeout"));
        assertTrue(collector.contains("STONE_COLUMN_PAGINA.equals(pagina.res().name)"));
        assertTrue(collector.contains("WaitWindow.withSoftTimeout(STONE_COLUMN_NAME, 200)"));
        assertTrue(collector.contains("gui.getWindow(STONE_COLUMN_NAME) == null"));
        assertTrue(!collector.contains("startBuild("));
    }
}
