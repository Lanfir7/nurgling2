package nurgling.widgets.quest;

import haven.GobIcon;
import haven.Resource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTreeIconControllerTest {
    @Test
    void resourceClaimExpandsToEveryFullSettingId() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting plain = setting("gfx/terobjs/mm/trees/oak", new Object[0]);
        GobIcon.Setting ripe = setting("gfx/terobjs/mm/trees/oak", new Object[] {"ripe"});
        GobIcon.Setting pine = setting("gfx/terobjs/mm/trees/pine", new Object[0]);
        settings.settings.put(plain.id, plain);
        settings.settings.put(ripe.id, ripe);
        settings.settings.put(pine.id, pine);

        Map<Integer, Set<GobIcon.Setting.ID>> result = QuestTreeIconController.settingIds(
                requirements("gfx/terobjs/mm/trees/oak"), settings);

        assertEquals(2, result.get(1).size());
        assertTrue(result.get(1).contains(plain.id));
        assertTrue(result.get(1).contains(ripe.id));
    }

    @Test
    void settingCanBeResolvedAfterItLoads() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        Map<Integer, Set<String>> required = requirements("gfx/terobjs/mm/trees/oak");
        assertTrue(QuestTreeIconController.settingIds(required, settings).isEmpty());

        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0]);
        settings.settings.put(oak.id, oak);

        assertEquals(Collections.singleton(oak.id),
                QuestTreeIconController.settingIds(required, settings).get(1));
    }

    @Test
    void claimReplacesAHiddenTemporaryOverrideWithoutChangingTheCheckbox() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);
        settings.setShowOverride(oak.id, false);

        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);

        assertFalse(oak.show);
        assertTrue(settings.shown(oak));
    }

    @Test
    void questClaimUsesTemporaryVisibilityWithoutPersistingCheckboxState() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);

        new QuestTreeIconController().reconcile(Collections.singletonList(oakQuest(1)), settings);

        assertFalse(oak.show);
        assertTrue(settings.shown(oak));
        assertEquals(0, settings.saves);
    }

    @Test
    void releaseClearsTemporaryVisibility() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);
        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        controller.release(settings);

        assertFalse(oak.show);
        assertFalse(settings.shown(oak));
    }

    @Test
    void releasePreservesAManuallyEnabledCheckbox() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], true);
        settings.settings.put(oak.id, oak);
        QuestTreeIconController controller = new QuestTreeIconController();

        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        controller.release(settings);

        assertTrue(oak.show);
        assertTrue(settings.shown(oak));
    }

    @Test
    void emptyReconcileReleasesACompletedOrRemovedQuestClaim() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);
        QuestTreeIconController controller = new QuestTreeIconController();

        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        controller.reconcile(Collections.emptyList(), settings);

        assertFalse(oak.show);
        assertFalse(settings.shown(oak));
    }

    @Test
    void readyOakBringDoesNotForceOakWhenOtherConditionUnfinished() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);

        QuestModel.TQuest q = new QuestModel.TQuest(1);
        q.kind = QuestKind.NPC;
        q.resnm = "paginae/quest/act/oakboard";
        q.conds = Arrays.asList(
                new QCond(1, true, "Bring oak acorn to Bildal", null),
                new QCond(1, false, "Catch a big fish", null));

        new QuestTreeIconController().reconcile(Collections.singletonList(q), settings);

        assertFalse(oak.show);
        assertFalse(settings.shown(oak));
    }

    @Test
    void unfinishedOakBringStillClaimsOakIcon() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);

        QuestModel.TQuest q = new QuestModel.TQuest(1);
        q.kind = QuestKind.NPC;
        q.resnm = "paginae/quest/act/oakboard";
        q.conds = Collections.singletonList(new QCond(1, false, "Bring oak acorn to Bildal", null));

        new QuestTreeIconController().reconcile(Collections.singletonList(q), settings);

        assertFalse(oak.show);
        assertTrue(settings.shown(oak));
    }

    @Test
    void activeQuestVisibilityRemainsTemporaryUntilReleased() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], true);
        settings.settings.put(oak.id, oak);

        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        oak.show = false;
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);

        assertTrue(settings.shown(oak));

        controller.release(settings);
        assertFalse(settings.shown(oak));
    }

    @Test
    void exactCedarFellObjectiveEnablesEveryCedarSettingVariant() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting plain = setting("gfx/terobjs/mm/trees/cedar", new Object[0], false);
        GobIcon.Setting ripe = setting("gfx/terobjs/mm/trees/cedar", new Object[] {"ripe"}, false);
        settings.settings.put(plain.id, plain);
        settings.settings.put(ripe.id, ripe);

        QuestModel.TQuest quest = new QuestModel.TQuest(1);
        quest.conds = Collections.singletonList(new QCond(1, false,
                "Fell a cedar (x6)", "2/6[5/5]"));

        new QuestTreeIconController().reconcile(Collections.singletonList(quest), settings);

        assertFalse(plain.show);
        assertFalse(ripe.show);
        assertTrue(settings.shown(plain));
        assertTrue(settings.shown(ripe));
    }

    @Test
    void pineConeObjectiveEnablesTheSameSettingShownInIconSettings() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting pine = setting("gfx/terobjs/mm/trees/pine", new Object[0], false);
        settings.settings.put(pine.id, pine);

        QuestModel.TQuest quest = new QuestModel.TQuest(1);
        quest.conds = Collections.singletonList(new QCond(1, false, "Pick a Pine Cone", null));

        new QuestTreeIconController().reconcile(Collections.singletonList(quest), settings);

        assertFalse(pine.show);
        assertTrue(settings.shown(pine));
    }

    @Test
    void activeQuestEnablesCedarAfterItsIconSettingLoadsLater() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        QuestModel.TQuest quest = new QuestModel.TQuest(1);
        quest.conds = Collections.singletonList(new QCond(1, false, "Fell a cedar (x6)", null));
        QuestTreeIconController controller = new QuestTreeIconController();

        controller.reconcile(Collections.singletonList(quest), settings);

        GobIcon.Setting cedar = setting("gfx/terobjs/mm/trees/cedar", new Object[0], false);
        settings.settings.put(cedar.id, cedar);
        controller.reconcile(Collections.singletonList(quest), settings);

        assertFalse(cedar.show);
        assertTrue(settings.shown(cedar));
    }

    private static QuestModel.TQuest oakQuest(int id) {
        QuestModel.TQuest q = new QuestModel.TQuest(id);
        q.kind = QuestKind.NPC;
        q.resnm = "paginae/quest/act/oakboard";
        q.conds = Collections.singletonList(new QCond(id, false, "Bring a Board of Oak to Jenny", null));
        return q;
    }

    private static Map<Integer, Set<String>> requirements(String resource) {
        Map<Integer, Set<String>> out = new HashMap<>();
        out.put(1, Collections.singleton(resource));
        return out;
    }

    private static GobIcon.Setting setting(String name, Object[] sub) {
        return setting(name, sub, false);
    }

    private static GobIcon.Setting setting(String name, Object[] sub, boolean show) {
        Resource.Saved saved = new Resource.Saved(Resource.remote(), name, 1);
        GobIcon.Settings.ResID from = new GobIcon.Settings.ResID(saved, new byte[0]);
        GobIcon.Setting setting = new GobIcon.Setting(saved, sub, null, from);
        setting.show = show;
        return setting;
    }

    private static class TrackingSettings extends GobIcon.Settings {
        int saves;

        TrackingSettings() {
            super(null, "test-icons");
        }

        @Override
        public void dsave() {
            saves++;
        }
    }
}
