package nurgling.widgets;

import haven.Widget;
import haven.Text;
import nurgling.NConfig;
import nurgling.widgets.quest.QCond;
import nurgling.widgets.quest.QuestKind;
import nurgling.widgets.quest.QuestModel;
import nurgling.widgets.quest.QuestObjectiveActionButton;
import nurgling.widgets.quest.QuestObjectiveActionResolver;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NQuestInfoObjectiveActionRowTest {
    @Test
    void cedarConditionFromProtocolWithNonBreakingSpacesHasVisibleMapAction() throws Exception {
        NConfig.getGlobalInstance();
        NQuestInfo info = bareQuestInfo();
        QCond cedar = protocolCondition(0, "Fell\u00a0a\u00a0cedar\u00a0(x6)", "2/6[5/5]");

        Widget row = conditionRow(info, cedar);

        assertEquals("cedar", cedar.itemTarget);
        assertEquals(1, row.children(QuestObjectiveActionButton.class).size());
        QuestObjectiveActionButton button = row.children(QuestObjectiveActionButton.class).iterator().next();
        button.tick(0);
        assertTrue(button.visible());
    }

    @Test
    void whitebeamAndCompletedConditionsKeepTheirExistingRowActionBehavior() throws Exception {
        NConfig.getGlobalInstance();
        NQuestInfo info = bareQuestInfo();

        Widget whitebeam = conditionRow(info,
                protocolCondition(0, "Bring a block of whitebeam to Jenny", null));
        Widget completedCedar = conditionRow(info,
                protocolCondition(1, "Fell a cedar (x6)", "6/6[5/5]"));

        assertEquals(1, whitebeam.children(QuestObjectiveActionButton.class).size());
        assertEquals(0, completedCedar.children(QuestObjectiveActionButton.class).size());
    }

    private static Widget conditionRow(NQuestInfo info, QCond cond) throws Exception {
        Class<?> row = Class.forName("nurgling.widgets.NQuestInfo$Row");
        Constructor<?> rowConstructor = row.getDeclaredConstructor(
                String.class, boolean.class, int.class, boolean.class, QuestKind.class, QCond.class);
        rowConstructor.setAccessible(true);
        Object rowModel = rowConstructor.newInstance(cond.text, false, 1, false, QuestKind.NPC, cond);
        Class<?> condRow = Class.forName("nurgling.widgets.NQuestInfo$CondRow");
        Constructor<?> constructor = condRow.getDeclaredConstructor(
                NQuestInfo.class, rowModel.getClass(), int.class, QuestKind.class);
        constructor.setAccessible(true);
        return (Widget)constructor.newInstance(info, rowModel, 300, QuestKind.NPC);
    }

    private static QCond protocolCondition(int state, String desc, String status) {
        QuestModel model = new QuestModel();
        model.setConds(1, new Object[] {desc, state, status == null ? "" : status});
        return model.quests().iterator().next().conds.get(0);
    }

    private static NQuestInfo bareQuestInfo() throws Exception {
        NQuestInfo info = (NQuestInfo)unsafe().allocateInstance(NQuestInfo.class);
        set(info, "actionResolver", new QuestObjectiveActionResolver());
        set(info, "condFnd", new Text.Foundry(Text.sans, 12).aa(true));
        set(info, "rowH", 18);
        return info;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = NQuestInfo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
