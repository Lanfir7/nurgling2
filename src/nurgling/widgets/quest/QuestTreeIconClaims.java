package nurgling.widgets.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Keeps temporary map-icon visibility scoped to the quests that require it. */
public class QuestTreeIconClaims {
    public interface Visibility {
        boolean isVisible(String resource);
        void setVisible(String resource, boolean visible);
    }

    private Map<Integer, Set<String>> quests = Collections.emptyMap();
    private final Map<String, Boolean> original = new HashMap<>();

    public void reconcile(Map<Integer, ? extends Collection<String>> required, Visibility visibility) {
        Set<String> before = resources(quests);
        Map<Integer, Set<String>> next = copy(required);
        Set<String> after = resources(next);

        for(String resource : after) {
            if(!before.contains(resource))
                original.put(resource, visibility.isVisible(resource));
            visibility.setVisible(resource, true);
        }
        for(String resource : before) {
            if(!after.contains(resource)) {
                if(!Boolean.TRUE.equals(original.remove(resource)))
                    visibility.setVisible(resource, false);
            }
        }
        quests = next;
    }

    private static Map<Integer, Set<String>> copy(Map<Integer, ? extends Collection<String>> source) {
        Map<Integer, Set<String>> out = new HashMap<>();
        for(Map.Entry<Integer, ? extends Collection<String>> entry : source.entrySet())
            out.put(entry.getKey(), new HashSet<>(entry.getValue()));
        return out;
    }

    private static Set<String> resources(Map<Integer, Set<String>> source) {
        Set<String> out = new HashSet<>();
        for(Set<String> resources : source.values())
            out.addAll(resources);
        return out;
    }
}
