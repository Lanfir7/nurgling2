package nurgling.widgets.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Enables an icon once when an unfinished quest first claims it.
 *
 * The icon setting remains the source of truth: a user may turn it off while the
 * quest is active, and a later reconcile will not turn it back on. Releasing a
 * claim also leaves the current checkbox choice alone. A new claim enables the
 * icon again, so newly accepted quests remain discoverable.
 */
public class QuestTreeIconClaims<K> {
    public interface Visibility<K> {
        void enable(K key);
    }

    private Map<Integer, Set<K>> quests = Collections.emptyMap();

    public void reconcile(Map<Integer, ? extends Collection<K>> required, Visibility<K> visibility) {
        Set<K> before = resources(quests);
        Map<Integer, Set<K>> next = copy(required);
        Set<K> after = resources(next);

        for(K key : after) {
            if(!before.contains(key))
                visibility.enable(key);
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
