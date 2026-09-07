package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestObjectiveActionButtonTest {
    @Test
    void usesDistinctMapAndCraftGlyphs() {
        QuestObjectiveAction map = new QuestObjectiveAction(
                QuestObjectiveAction.Kind.FORAGE_TERRAIN, Collections.singleton("Bog"));
        QuestObjectiveAction trees = new QuestObjectiveAction(
                QuestObjectiveAction.Kind.TREE_TERRAIN, Collections.singleton("Deep Tangle"));
        QuestObjectiveAction craft = new QuestObjectiveAction(
                QuestObjectiveAction.Kind.CRAFT, Collections.singleton("stone axe"));

        assertEquals("M", QuestObjectiveActionButton.glyphFor(map));
        assertEquals("M", QuestObjectiveActionButton.glyphFor(trees));
        assertEquals("C", QuestObjectiveActionButton.glyphFor(craft));
    }

    @Test
    void leftClickIsConsumedEvenWhenActionBecomesUnavailable() {
        assertTrue(QuestObjectiveActionButton.consumesClick(1));
    }
}
