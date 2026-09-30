package nurgling.widgets;

import nurgling.NGameUI;
import nurgling.NConfig;
import nurgling.conf.NQuestTrackerProp;
import nurgling.i18n.L10n;
import nurgling.widgets.quest.QuestKind;
import nurgling.widgets.quest.SharedQuests;
import nurgling.widgets.quest.VillageQuestStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NQuestInfoVillageTaskGroupsTest {
    @BeforeAll
    static void initConfig() {
        NConfig.getGlobalInstance();
    }

    @Test
    void emptyVillageDoesNotCrashWhenGroupedByTasks() throws Exception {
        NQuestInfo info = bare(NQuestInfo.class);

        assertTrue(groups(info, new NQuestTrackerProp("", "")).isEmpty());
    }

    @Test
    void sharedObjectivesKeepTheirCategoriesAndMergeHolders() throws Exception {
        SharedQuests.Quest quest = new SharedQuests.Quest(
                "paginae/quest/act/test", "Village errands", QuestKind.NPC, "Jenny", true,
                Arrays.asList(
                        new SharedQuests.Cond("Bring a branch to Jenny", null, false),
                        new SharedQuests.Cond("Pick a nettle", null, false),
                        new SharedQuests.Cond("Eat an olive (x2)", null, false),
                        new SharedQuests.Cond("Bring a stone to Jenny", null, true)));
        String data = SharedQuests.encode(Collections.singletonList(quest));
        VillageQuestStore.Villager alice = VillageQuestStore.Villager.of("Alice", 1, 0, data);
        VillageQuestStore.Villager bob = VillageQuestStore.Villager.of("Bob", 1, 0, data);
        NGameUI gui = bare(NGameUI.class);
        gui.villageQuests = new VillageQuestStore();
        gui.villageQuests.apply(Arrays.asList(alice, bob), "Self", true);
        NQuestInfo info = bare(NQuestInfo.class);
        info.parent = gui;

        List<?> groups = groups(info, new NQuestTrackerProp("", ""));

        assertEquals(3, groups.size());
        assertGroup(groups.get(0), "Bring", "bring", alice, bob);
        assertGroup(groups.get(1), "Foraging", "foraging", alice, bob);
        assertGroup(groups.get(2), "Other", "other", alice, bob);
    }

    private static void assertGroup(Object group, String name, String key,
                                    VillageQuestStore.Villager... holders) throws Exception {
        assertEquals("vtask:" + name, field(group, "key"));
        assertEquals(L10n.get("char.quest.section." + key), field(group, "title"));
        List<?> rows = (List<?>)field(group, "rows");
        assertEquals(1, rows.size());
        assertEquals(Arrays.asList(holders), field(rows.get(0), "holders"));
    }

    private static List<?> groups(NQuestInfo info, NQuestTrackerProp settings) throws Exception {
        Method method = NQuestInfo.class.getDeclaredMethod("villageTaskGroups", NQuestTrackerProp.class);
        method.setAccessible(true);
        return (List<?>)method.invoke(info, settings);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
}
