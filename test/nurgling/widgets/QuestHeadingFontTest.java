package nurgling.widgets;

import haven.Text;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestHeadingFontTest {
    @Test
    void headingUsesConfiguredQuestFontAndSupportsCyrillic() {
        Text.Foundry quest = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 13));

        Text.Foundry heading = QuestHeadingFont.from(quest);

        assertEquals(quest.font.getFamily(), heading.font.getFamily());
        assertEquals(quest.font.getSize2D(), heading.font.getSize2D());
        assertTrue(heading.font.isBold());
        assertEquals(-1, heading.font.canDisplayUpTo("Русский персонаж"));
    }
}
