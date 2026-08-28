package nurgling;

import haven.QuestWnd;
import nurgling.widgets.quest.QuestConditionWidgetPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NQuestBoxConditionPolicyTest {
    @Test
    void serverProvidedConditionWidgetIsNeverReplacedByQuestActionRow() {
        QuestWnd.Quest.Condition custom = new QuestWnd.Quest.Condition("Pick a Quartz", 0, null);
        custom.wdata = new Object[] {42};
        QuestWnd.Quest.Condition ordinary = new QuestWnd.Quest.Condition("Pick a Quartz", 0, null);

        assertTrue(QuestConditionWidgetPolicy.usesServerWidget(custom));
        assertFalse(QuestConditionWidgetPolicy.usesServerWidget(ordinary));
    }
}
