package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTreeIconClaimsTest {
    @Test
    void hiddenIconIsVisibleOnlyWhileClaimed() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        assertTrue(visibility.shown("oak"));

        claims.reconcile(Collections.emptyMap(), visibility);
        assertFalse(visibility.shown("oak"));
    }

    @Test
    void visibleIconRemainsVisibleAfterClaimIsReleased() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertTrue(visibility.shown("oak"));
    }

    @Test
    void sharedIconStaysVisibleUntilLastQuestIsRemoved() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", false);
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
        visibility.show.put("oak:ripe", true);
        visibility.overrides.put("oak:ripe", true);
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
        visibility.show.put("oak:plain", false);
        visibility.show.put("oak:ripe", true);
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

    @Test
    void playerUncheckWhileClaimedIsNotForcedBackOnNextReconcile() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        assertTrue(visibility.shown("oak"));

        visibility.show.put("oak", false);
        int writes = visibility.writes;
        claims.reconcile(requirements(1, "oak"), visibility);

        assertFalse(visibility.shown("oak"));
        assertEquals(writes, visibility.writes);
    }

    @Test
    void releaseRestoresPreClaimShowNotWhateverPlayerLeft() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        visibility.show.put("oak", false);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertTrue(visibility.shown("oak"));
    }

    @Test
    void leftoverOverrideIsClearedOnRelease() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.show.put("oak", false);
        visibility.overrides.put("oak", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertFalse(visibility.shown("oak"));
        assertFalse(visibility.overrides.containsKey("oak"));
    }

    private static Map<Integer, Set<String>> requirements(int questId, String resource) {
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        out.put(questId, Collections.singleton(resource));
        return out;
    }

    private static class VisibilityState<K> implements QuestTreeIconClaims.Visibility<K> {
        final Map<K, Boolean> show = new HashMap<>();
        final Map<K, Boolean> overrides = new HashMap<>();
        int writes;

        @Override
        public boolean shown(K key) {
            return Boolean.TRUE.equals(show.get(key));
        }

        @Override
        public void setShown(K key, boolean visible) {
            writes++;
            show.put(key, visible);
        }

        @Override
        public void clearOverride(K key) {
            overrides.remove(key);
        }
    }
}
