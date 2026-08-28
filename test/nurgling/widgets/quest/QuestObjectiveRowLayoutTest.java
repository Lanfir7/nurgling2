package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestObjectiveRowLayoutTest {
    @Test
    void actionButtonReservesRightEdgeWithoutNegativeTextWidth() {
        assertEquals(180, QuestObjectiveRowLayout.textWidth(220, 20, true));
        assertEquals(200, QuestObjectiveRowLayout.textWidth(220, 20, false));
        assertEquals(0, QuestObjectiveRowLayout.textWidth(10, 20, true));
    }
}
