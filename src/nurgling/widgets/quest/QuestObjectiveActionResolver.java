package nurgling.widgets.quest;

import nurgling.tools.Forageables;
import nurgling.tools.RockResourceMapper;
import nurgling.tools.VSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestObjectiveActionResolver {
    private final Map<String, Forageables.Entry> forageByName;

    public QuestObjectiveActionResolver() {
        Map<String, Forageables.Entry> entries = new LinkedHashMap<>();
        for(Forageables.Entry entry : Forageables.all()) {
            String key = normalize(entry.name);
            if(!key.isEmpty() && !entries.containsKey(key))
                entries.put(key, entry);
        }
        forageByName = Collections.unmodifiableMap(entries);
    }

    public QuestObjectiveAction resolve(QCond cond) {
        if(cond == null || cond.ready || cond.itemTarget == null)
            return null;
        if(cond.verb == QCond.Verb.CREATE)
            return new QuestObjectiveAction(QuestObjectiveAction.Kind.CRAFT,
                    Collections.singletonList(normalize(cond.itemTarget)));
        if(cond.verb != QCond.Verb.PICK && cond.verb != QCond.Verb.BRING)
            return null;

        Forageables.Entry forage = forageByName.get(normalize(cond.itemTarget));
        if(forage != null && !forage.terrains.isEmpty())
            return new QuestObjectiveAction(QuestObjectiveAction.Kind.FORAGE_TERRAIN, forage.terrains);

        Set<String> rocks = RockResourceMapper.getTileResourcesForItem(cond.itemTarget);
        if(!rocks.isEmpty())
            return new QuestObjectiveAction(QuestObjectiveAction.Kind.ROCK_TERRAIN, rocks);
        return null;
    }

    public Set<String> treeResources(QCond cond) {
        if(cond == null || cond.itemTarget == null)
            return Collections.emptySet();
        return VSpec.treeResourcesForProduct(cond.itemTarget);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
