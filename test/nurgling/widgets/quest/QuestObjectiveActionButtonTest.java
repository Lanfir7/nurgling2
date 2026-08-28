package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestObjectiveActionButtonTest {
    @Test
    void usesDistinctMapAndCraftGlyphs() {
        QuestObjectiveAction map = new QuestObjectiveAction(
                QuestObjectiveAction.Kind.FORAGE_TERRAIN, Collections.singleton("Bog"));
        QuestObjectiveAction craft = new QuestObjectiveAction(
                QuestObjectiveAction.Kind.CRAFT, Collections.singleton("stone axe"));

        assertEquals("M", QuestObjectiveActionButton.glyphFor(map));
        assertEquals("C", QuestObjectiveActionButton.glyphFor(craft));
    }
}
