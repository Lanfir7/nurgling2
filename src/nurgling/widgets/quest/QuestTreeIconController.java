package nurgling.widgets.quest;

import haven.GobIcon;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Applies quest-owned tree icon visibility without writing it to IconSettings. */
public class QuestTreeIconController {
    private final QuestTreeIconClaims claims = new QuestTreeIconClaims();
    private final QuestObjectiveActionResolver resolver = new QuestObjectiveActionResolver();

    public void reconcile(Collection<QuestModel.TQuest> quests, GobIcon.Settings settings) {
        if(settings == null)
            return;
        Map<Integer, Set<String>> required = new HashMap<>();
        for(QuestModel.TQuest quest : quests) {
            Set<String> resources = new LinkedHashSet<>();
            for(QCond cond : quest.conds)
                resources.addAll(resolver.treeResources(cond));
            if(!resources.isEmpty())
                required.put(quest.id, resources);
        }
        claims.reconcile(required, visibility(settings));
    }

    public void release(GobIcon.Settings settings) {
        if(settings != null)
            claims.reconcile(java.util.Collections.emptyMap(), visibility(settings));
    }

    private static QuestTreeIconClaims.Visibility visibility(GobIcon.Settings settings) {
        return new QuestTreeIconClaims.Visibility() {
            @Override
            public boolean isVisible(String resource) {
                for(GobIcon.Setting setting : settings.settings.values()) {
                    if(setting.id.res.equals(resource) && setting.show)
                        return true;
                }
                return false;
            }

            @Override
            public void setVisible(String resource, boolean visible) {
                for(GobIcon.Setting setting : settings.settings.values()) {
                    if(setting.id.res.equals(resource))
                        setting.show = visible;
                }
            }
        };
    }
}
