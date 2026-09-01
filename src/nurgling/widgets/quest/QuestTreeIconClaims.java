package nurgling.widgets.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps map-icon visibility scoped to the quests that require it.
 * Writes the real Icon Settings {@code show} flag only when an id enters or leaves the claimed set.
 */
public class QuestTreeIconClaims<K> {
    public interface Visibility<K> {
        boolean shown(K key);
        void setShown(K key, boolean shown);
        void clearOverride(K key);
    }

    private Map<Integer, Set<K>> quests = Collections.emptyMap();
    private final Map<K, Boolean> previousShow = new HashMap<>();

    public void reconcile(Map<Integer, ? extends Collection<K>> required, Visibility<K> visibility) {
        Set<K> before = resources(quests);
        Map<Integer, Set<K>> next = copy(required);
        Set<K> after = resources(next);

        for(K key : after) {
            if(!before.contains(key))
                claim(key, visibility);
        }
        for(K key : before) {
            if(!after.contains(key))
                release(key, visibility);
        }
        quests = next;
    }

    private void claim(K key, Visibility<K> visibility) {
        boolean was = visibility.shown(key);
        previousShow.put(key, was);
        if(!was)
            visibility.setShown(key, true);
        visibility.clearOverride(key);
    }

    private void release(K key, Visibility<K> visibility) {
        Boolean was = previousShow.remove(key);
        if(was != null && visibility.shown(key) != was)
            visibility.setShown(key, was);
        visibility.clearOverride(key);
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
