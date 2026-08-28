package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTreeIconClaimsTest {
    @Test
    void hiddenIconIsVisibleOnlyWhileClaimed() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        assertTrue(visibility.shown("oak"));

        claims.reconcile(Collections.emptyMap(), visibility);
        assertFalse(visibility.shown("oak"));
    }

    @Test
    void visibleIconRemainsVisibleAfterClaimIsReleased() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertTrue(visibility.shown("oak"));
    }

    @Test
    void sharedIconStaysVisibleUntilLastQuestIsRemoved() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();
        Map<Integer, Set<String>> both = requirements(1, "oak");
        both.put(2, Collections.singleton("oak"));

        claims.reconcile(both, visibility);
        claims.reconcile(requirements(2, "oak"), visibility);
        assertTrue(visibility.shown("oak"));

        claims.reconcile(Collections.emptyMap(), visibility);
        assertFalse(visibility.shown("oak"));
    }

    @Test
    void lateLoadedVisibleSettingKeepsItsPersistedStateOnRelease() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak:ripe", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(Collections.emptyMap(), visibility);
        claims.reconcile(requirements(1, "oak:ripe"), visibility);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertTrue(visibility.shown("oak:ripe"));
        assertFalse(visibility.overrides.containsKey("oak:ripe"));
    }

    @Test
    void settingsWithSameResourceKeepIndependentPersistedStates() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak:plain", false);
        visibility.base.put("oak:ripe", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();
        Map<Integer, Set<String>> required = new LinkedHashMap<>();
        required.put(1, new HashSet<>(Arrays.asList("oak:plain", "oak:ripe")));

        claims.reconcile(required, visibility);
        assertTrue(visibility.shown("oak:plain"));
        assertTrue(visibility.shown("oak:ripe"));

        claims.reconcile(Collections.emptyMap(), visibility);
        assertFalse(visibility.shown("oak:plain"));
        assertTrue(visibility.shown("oak:ripe"));
    }

    private static Map<Integer, Set<String>> requirements(int questId, String resource) {
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        out.put(questId, Collections.singleton(resource));
        return out;
    }

    private static class VisibilityState<K> implements QuestTreeIconClaims.Visibility<K> {
        final Map<K, Boolean> base = new HashMap<>();
        final Map<K, Boolean> overrides = new HashMap<>();

        boolean shown(K key) {
            return overrides.getOrDefault(key, Boolean.TRUE.equals(base.get(key)));
        }

        @Override
        public void setOverride(K key, Boolean visible) {
            if(visible == null)
                overrides.remove(key);
            else
                overrides.put(key, visible);
        }
    }
}
