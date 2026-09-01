package nurgling.widgets.quest;

import haven.GobIcon;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies quest-owned tree icon visibility by writing Icon Settings {@code show}
 * and persisting it the same way the Icon Settings checkbox does.
 */
public class QuestTreeIconController {
    private final QuestTreeIconClaims<GobIcon.Setting.ID> claims = new QuestTreeIconClaims<>();
    private final QuestObjectiveActionResolver resolver = new QuestObjectiveActionResolver();

    public void reconcile(Collection<QuestModel.TQuest> quests, GobIcon.Settings settings) {
        if(settings == null)
            return;
        Map<Integer, Set<String>> resourcesByQuest = new HashMap<>();
        for(QuestModel.TQuest quest : quests) {
            Set<String> resources = new LinkedHashSet<>();
            for(QCond cond : quest.conds) {
                for(String tree : resolver.treeResources(cond))
                    resources.add(iconResourceForTree(tree));
            }
            if(!resources.isEmpty())
                resourcesByQuest.put(quest.id, resources);
        }
        claims.reconcile(settingIds(resourcesByQuest, settings), visibility(settings));
    }

    public void release(GobIcon.Settings settings) {
        if(settings != null)
            claims.reconcile(java.util.Collections.emptyMap(), visibility(settings));
    }

    static String iconResourceForTree(String treeResource) {
        return treeResource.replace("gfx/terobjs/trees/", "gfx/terobjs/mm/trees/")
                .replace("gfx/terobjs/bushes/", "gfx/terobjs/mm/bushes/");
    }

    static Map<Integer, Set<GobIcon.Setting.ID>> settingIds(
            Map<Integer, Set<String>> resourcesByQuest, GobIcon.Settings settings) {
        Map<Integer, Set<GobIcon.Setting.ID>> required = new HashMap<>();
        Map<GobIcon.Setting.ID, GobIcon.Setting> loaded = settings.settings;
        synchronized(loaded) {
            for(Map.Entry<Integer, Set<String>> quest : resourcesByQuest.entrySet()) {
                Set<GobIcon.Setting.ID> ids = new LinkedHashSet<>();
                for(GobIcon.Setting setting : loaded.values()) {
                    if(quest.getValue().contains(setting.id.res))
                        ids.add(setting.id);
                }
                if(!ids.isEmpty())
                    required.put(quest.getKey(), ids);
            }
        }
        return required;
    }

    private static QuestTreeIconClaims.Visibility<GobIcon.Setting.ID> visibility(GobIcon.Settings settings) {
        return new QuestTreeIconClaims.Visibility<GobIcon.Setting.ID>() {
            @Override
            public boolean shown(GobIcon.Setting.ID id) {
                GobIcon.Setting setting = setting(id);
                return setting != null && setting.show;
            }

            @Override
            public void setShown(GobIcon.Setting.ID id, boolean shown) {
                GobIcon.Setting setting = setting(id);
                if(setting == null || setting.show == shown)
                    return;
                setting.show = shown;
                settings.dsave();
            }

            @Override
            public void clearOverride(GobIcon.Setting.ID id) {
                settings.setShowOverride(id, null);
            }

            private GobIcon.Setting setting(GobIcon.Setting.ID id) {
                Map<GobIcon.Setting.ID, GobIcon.Setting> loaded = settings.settings;
                synchronized(loaded) {
                    return loaded.get(id);
                }
            }
        };
    }
}
