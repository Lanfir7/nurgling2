package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestCredoCounterTest {
    @Test
    void withLevelShowsQuestProgressAndBracketedCredoLevel() {
        assertEquals("2/6[1/5]", QuestCredoCounter.format(2, 6, 1, 5, true));
    }

    @Test
    void withoutLevelKeepsPlainQuestProgress() {
        assertEquals("2/6", QuestCredoCounter.format(2, 6, 1, 5, false));
    }

    @Test
    void zeroLevelTotalKeepsPlainQuestProgressEvenWhenRequested() {
        assertEquals("2/6", QuestCredoCounter.format(2, 6, 0, 0, true));
        assertEquals("0/6", QuestCredoCounter.format(0, 6, 0, 0, true));
        assertEquals("", QuestCredoCounter.format(0, 0, 0, 0, true));
    }

    @Test
    void pursuedCredoPrefersServerQuestCountsAndAddsLevel() {
        QuestModel.CredoProgress p = new QuestModel.CredoProgress(42, 2, 6, 1, 5);
        assertEquals("2/6[1/5]",
                QuestCredoCounter.forGroup(QuestKind.CREDO, 42, 0, 5, p));
    }

    @Test
    void pursuedCredoFallsBackToLoadedConditionsWhenServerTotalsMissing() {
        QuestModel.CredoProgress p = new QuestModel.CredoProgress(42, 0, 0, 1, 5);
        assertEquals("3/5[1/5]",
                QuestCredoCounter.forGroup(QuestKind.CREDO, 42, 3, 5, p));
    }

    @Test
    void otherCredoAndNpcGroupsStayPlainDoneTotal() {
        QuestModel.CredoProgress p = new QuestModel.CredoProgress(42, 2, 6, 1, 5);
        assertEquals("0/5", QuestCredoCounter.forGroup(QuestKind.CREDO, 7, 0, 5, p));
        assertEquals("1/3", QuestCredoCounter.forGroup(QuestKind.NPC, 42, 1, 3, p));
        assertEquals("4/4", QuestCredoCounter.forGroup(QuestKind.WORLD, 42, 4, 4, p));
        assertEquals("", QuestCredoCounter.forGroup(QuestKind.NPC, 1, 0, 0, p));
    }
}
