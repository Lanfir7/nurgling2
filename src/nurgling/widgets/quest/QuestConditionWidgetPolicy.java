package nurgling.widgets.quest;

import haven.QuestWnd;

/** Keeps server-supplied quest-condition widgets outside client row decoration. */
public final class QuestConditionWidgetPolicy {
    private QuestConditionWidgetPolicy() {
    }

    public static boolean usesServerWidget(QuestWnd.Quest.Condition condition) {
        return condition != null && condition.wdata != null;
    }
}
