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

}
