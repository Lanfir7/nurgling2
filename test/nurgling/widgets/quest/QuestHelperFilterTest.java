package nurgling.widgets.quest;

import nurgling.conf.NQuestTrackerProp;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestHelperFilterTest {
    @Test
    void unknownKindIsInvisible() {
        QuestModel.TQuest q = quest(1, QuestKind.UNKNOWN, "paginae/quest/act/oak");
        assertFalse(QuestHelperFilter.visible(q, prop()));
    }

    @Test
    void hiddenQuestIsInvisible() {
        QuestModel.TQuest q = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        NQuestTrackerProp p = prop();
        p.hiddenQuests.add(q.key());
        assertFalse(QuestHelperFilter.visible(q, p));
    }

    @Test
    void mutedTitleIsInvisible() {
        QuestModel.TQuest q = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        assertFalse(QuestHelperFilter.visible(q, prop(), true));
    }

    @Test
    void uncheckedKindIsInvisibleUnlessPinned() {
        QuestModel.TQuest q = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        NQuestTrackerProp p = prop();
        p.kinds.remove(QuestKind.NPC);
        assertFalse(QuestHelperFilter.visible(q, p));

        p.pinned.add(q.key());
        assertTrue(QuestHelperFilter.visible(q, p));
    }

    @Test
    void visibleQuestsDropInvisibleEntries() {
        QuestModel.TQuest shown = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        QuestModel.TQuest hidden = quest(2, QuestKind.NPC, "paginae/quest/act/pine");
        NQuestTrackerProp p = prop();
        p.hiddenQuests.add(hidden.key());

        List<QuestModel.TQuest> out = QuestHelperFilter.visibleQuests(
                Arrays.asList(shown, hidden), p);
        assertEquals(Collections.singletonList(shown), out);
    }

    @Test
    void overlayTargetsIgnoreInvisibleQuests() {
        QuestModel.TQuest shown = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        shown.conds = Collections.singletonList(new QCond(1, false, "Kill a Badger", null));
        QuestModel.TQuest hidden = quest(2, QuestKind.NPC, "paginae/quest/act/pine");
        hidden.conds = Collections.singletonList(new QCond(2, false, "Pick a Chiming Bluebell", null));
        NQuestTrackerProp p = prop();
        p.hiddenQuests.add(hidden.key());

        QCond kill = shown.conds.get(0);
        QuestHelperFilter.OverlaySets sets = QuestHelperFilter.overlayTargets(
                QuestHelperFilter.visibleQuests(Arrays.asList(shown, hidden), p));

        assertTrue(sets.hunt.contains(kill.gobTarget));
        assertTrue(sets.forage.isEmpty());
        assertTrue(sets.bring.isEmpty());
    }

    @Test
    void overlayTargetsSkipReadyConditions() {
        QCond readyKill = new QCond(1, true, "Kill a Badger", null);
        QCond bring = new QCond(1, false, "Bring a Board of Oak to Jenny", null);
        QuestModel.TQuest q = quest(1, QuestKind.NPC, "paginae/quest/act/oak");
        q.conds = Arrays.asList(readyKill, bring);

        QuestHelperFilter.OverlaySets sets = QuestHelperFilter.overlayTargets(Collections.singletonList(q));
        assertTrue(sets.hunt.isEmpty());
        assertTrue(sets.bring.contains(bring.bringItem));
    }

    private static QuestModel.TQuest quest(int id, QuestKind kind, String resnm) {
        QuestModel.TQuest q = new QuestModel.TQuest(id);
        q.kind = kind;
        q.resnm = resnm;
        q.stitle = "Quest " + id;
        return q;
    }

    private static NQuestTrackerProp prop() {
        return new NQuestTrackerProp("user", "char");
    }
}
