package nurgling.widgets.quest;

import haven.GobIcon;
import haven.Resource;
import org.junit.jupiter.api.Test;

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
    void claimWritesPersistedShowAndClearsLeftoverOverride() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);
        settings.setShowOverride(oak.id, true);

        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);

        assertTrue(oak.show);
        assertTrue(settings.shown(oak));
        oak.show = false;
        assertFalse(settings.shown(oak));
    }

    @Test
    void releaseRestoresPreviousShowAndClearsOverride() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        settings.settings.put(oak.id, oak);
        settings.setShowOverride(oak.id, true);

        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        controller.release(settings);

        assertFalse(oak.show);
        assertFalse(settings.shown(oak));
    }

    @Test
    void alreadyShownIconIsNotForcedOnEveryTick() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting oak = setting("gfx/terobjs/mm/trees/oak", new Object[0], true);
        settings.settings.put(oak.id, oak);

        QuestTreeIconController controller = new QuestTreeIconController();
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);
        oak.show = false;
        controller.reconcile(Collections.singletonList(oakQuest(1)), settings);

        assertFalse(oak.show);

        controller.release(settings);
        assertTrue(oak.show);
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
}
