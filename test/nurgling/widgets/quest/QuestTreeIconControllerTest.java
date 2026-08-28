package nurgling.widgets.quest;

import haven.GobIcon;
import haven.Resource;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Map<Integer, Set<String>> requirements(String resource) {
        Map<Integer, Set<String>> out = new HashMap<>();
        out.put(1, Collections.singleton(resource));
        return out;
    }

    private static GobIcon.Setting setting(String name, Object[] sub) {
        return new GobIcon.Setting(new Resource.Saved(Resource.remote(), name, 1), sub);
    }
}
