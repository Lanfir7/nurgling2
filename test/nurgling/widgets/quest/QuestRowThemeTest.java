package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRowThemeTest {
    @Test
    void credoRowsReceiveBadgeAccentAndBrightObjectiveColors() {
        QuestRowTheme theme = QuestRowTheme.forKind(QuestKind.CREDO);

        assertTrue(theme.emphasized);
        assertEquals("char.quest.credo_badge", theme.badgeKey);
        assertEquals(new Color(126, 198, 194), theme.accent);
        assertTrue(theme.background.getAlpha() >= 80);
        assertEquals(new Color(255, 218, 64), theme.conditionColor(false, false));
        assertEquals(new Color(122, 175, 122), theme.conditionColor(true, false));
    }

    @Test
    void ordinaryQuestRowsDoNotReceiveCredoDecoration() {
        QuestRowTheme theme = QuestRowTheme.forKind(QuestKind.NPC);

        assertFalse(theme.emphasized);
        assertNull(theme.badgeKey);
        assertEquals(0, theme.background.getAlpha());
        assertEquals(new Color(222, 205, 171), theme.conditionColor(false, false));
    }

    @Test
    void credoObjectiveStaysHighlightedInsideOrdinaryTaskGroup() {
        QuestRowTheme giverMode = QuestRowTheme.forObjective(QuestKind.CREDO, QuestKind.CREDO);
        QuestRowTheme taskMode = QuestRowTheme.forObjective(QuestKind.NPC, QuestKind.CREDO);
        QuestRowTheme ordinaryTask = QuestRowTheme.forObjective(QuestKind.NPC, QuestKind.NPC);

        assertTrue(giverMode.emphasized);
        assertTrue(taskMode.emphasized);
        assertFalse(ordinaryTask.emphasized);
    }
}
