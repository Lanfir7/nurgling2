package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapToolsWindowKindRowTest {

    @Test
    void kindRowCheckboxUsesShowProspectKind() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/MapToolsWindow.java"), StandardCharsets.UTF_8);
        int kindRow = src.indexOf("private class KindRow");
        assertTrue(kindRow >= 0);
        String body = src.substring(kindRow);
        assertTrue(body.contains("box.state(() -> NMiniMap.showProspectKind(kind))"));
        assertTrue(body.contains("box.set(val -> NMiniMap.showProspectKind(kind, val))"));
        assertFalse(body.contains("settings().enabled(kind)"));
        assertFalse(body.contains("settings().setEnabled(kind, val)"));
        assertFalse(src.contains("MinesweeperOverlay"));
    }
}
