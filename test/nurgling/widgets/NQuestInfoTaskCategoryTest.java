package nurgling.widgets;

import nurgling.widgets.quest.QCond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NQuestInfoTaskCategoryTest {
    @Test
    void eatObjectiveIsEligibleForTheOtherTaskGroup() {
        QCond eatOlive = new QCond(1, false, "Eat an olive (x2)", null);

        assertEquals("Other", NQuestInfo.taskCategory(eatOlive.verb));
    }

    @Test
    void existingTaskGroupMappingsStayUnchanged() {
        assertEquals("Bring", NQuestInfo.taskCategory(QCond.Verb.BRING));
        assertEquals("Foraging", NQuestInfo.taskCategory(QCond.Verb.PICK));
    }
}
