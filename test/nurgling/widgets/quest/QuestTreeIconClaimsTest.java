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
    void hiddenIconIsEnabledWhenFirstClaimed() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        assertTrue(visibility.shown("oak"));
    }

    @Test
    void releasePreservesCurrentCheckboxChoice() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        visibility.base.put("oak", false);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertFalse(visibility.shown("oak"));
    }

    @Test
    void releasePreservesAManuallyEnabledIcon() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        claims.reconcile(Collections.emptyMap(), visibility);

        assertTrue(visibility.shown("oak"));
    }

    @Test
    void sharedIconIsEnabledOnlyOnFirstClaim() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();
        Map<Integer, Set<String>> both = requirements(1, "oak");
        both.put(2, Collections.singleton("oak"));

        claims.reconcile(both, visibility);
        int writes = visibility.writes;
        claims.reconcile(requirements(2, "oak"), visibility);
        assertTrue(visibility.shown("oak"));
        assertEquals(writes, visibility.writes);

        claims.reconcile(Collections.emptyMap(), visibility);
        assertFalse(visibility.shown("oak"));
    }

    @Test
    void newClaimEnablesAResourceAfterItBecomesAvailable() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak:ripe", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(Collections.emptyMap(), visibility);
        claims.reconcile(requirements(1, "oak:ripe"), visibility);

        assertTrue(visibility.shown("oak:ripe"));
    }

    @Test
    void settingsWithSameResourceAreEnabledIndependently() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak:plain", false);
        visibility.base.put("oak:ripe", true);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();
        Map<Integer, Set<String>> required = new LinkedHashMap<>();
        required.put(1, new HashSet<>(Arrays.asList("oak:plain", "oak:ripe")));

        claims.reconcile(required, visibility);
        assertTrue(visibility.shown("oak:plain"));
        assertTrue(visibility.shown("oak:ripe"));
    }

    @Test
    void activeClaimRemainsVisibleAcrossAnUnchangedReconcile() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        assertTrue(visibility.shown("oak"));

        visibility.base.put("oak", false);
        int writes = visibility.writes;
        claims.reconcile(requirements(1, "oak"), visibility);

        assertTrue(visibility.shown("oak"));
        assertEquals(writes, visibility.writes);
    }

    @Test
    void newClaimEnablesAnIconAgainAfterThePreviousQuestEnds() {
        VisibilityState<String> visibility = new VisibilityState<>();
        visibility.base.put("oak", false);
        QuestTreeIconClaims<String> claims = new QuestTreeIconClaims<>();

        claims.reconcile(requirements(1, "oak"), visibility);
        visibility.base.put("oak", false);
        claims.reconcile(Collections.emptyMap(), visibility);
        claims.reconcile(requirements(2, "oak"), visibility);

        assertTrue(visibility.shown("oak"));
    }

    private static Map<Integer, Set<String>> requirements(int questId, String resource) {
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        out.put(questId, Collections.singleton(resource));
        return out;
    }

    private static class VisibilityState<K> implements QuestTreeIconClaims.Visibility<K> {
        final Map<K, Boolean> base = new HashMap<>();
        final Map<K, Boolean> temporary = new HashMap<>();
        int writes;

        public boolean shown(K key) {
            Boolean override = temporary.get(key);
            return override != null ? override : Boolean.TRUE.equals(base.get(key));
        }

        @Override
        public void enable(K key) {
            writes++;
            temporary.put(key, true);
        }

        @Override
        public void disable(K key) {
            writes++;
            temporary.remove(key);
        }
    }
}
