package nurgling.widgets.craftatlas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasRecipeListTest {
    @Test
    void formatsGildingChanceAsAnInclusiveRange() {
        assertEquals("20%–50%", CraftAtlasRecipeList.formatGildingChance(0.2, 0.5));
    }

    @Test
    void resetsSeparatorColorBeforeDrawingAttributeIconsAndUsesAnEmDashPlaceholder() throws IOException {
        String source = new String(Files.readAllBytes(Path.of("src/nurgling/widgets/craftatlas/CraftAtlasRecipeList.java")), StandardCharsets.UTF_8);
        int separator = source.indexOf("g.chcolor(new Color(71, 80, 83, 115));");
        int attributeIcons = source.indexOf("if(column.presentation == CraftAtlasListTable.Presentation.ATTRIBUTE_ICONS)", separator);

        assertTrue(separator >= 0 && attributeIcons > separator);
        assertTrue(source.substring(separator, attributeIcons).contains("g.chcolor();"));
        assertTrue(source.contains("g.atext(\"\\u2014\""));
    }
}
