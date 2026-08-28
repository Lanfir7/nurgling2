package nurgling.widgets.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Keeps temporary map-icon visibility scoped to the quests that require it. */
public class QuestTreeIconClaims<K> {
    public interface Visibility<K> {
        void setOverride(K key, Boolean visible);
    }

    private Map<Integer, Set<K>> quests = Collections.emptyMap();

    public void reconcile(Map<Integer, ? extends Collection<K>> required, Visibility<K> visibility) {
        Set<K> before = resources(quests);
        Map<Integer, Set<K>> next = copy(required);
        Set<K> after = resources(next);

        for(K key : after) {
            visibility.setOverride(key, true);
        }
        for(K key : before) {
            if(!after.contains(key))
                visibility.setOverride(key, null);
        }
        quests = next;
    }

    private Map<Integer, Set<K>> copy(Map<Integer, ? extends Collection<K>> source) {
        Map<Integer, Set<K>> out = new HashMap<>();
        for(Map.Entry<Integer, ? extends Collection<K>> entry : source.entrySet())
            out.put(entry.getKey(), new HashSet<>(entry.getValue()));
        return out;
    }

    private Set<K> resources(Map<Integer, Set<K>> source) {
        Set<K> out = new HashSet<>();
        for(Set<K> resources : source.values())
            out.addAll(resources);
        return out;
    }
}
