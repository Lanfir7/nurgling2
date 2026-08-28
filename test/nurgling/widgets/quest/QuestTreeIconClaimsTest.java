package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTreeIconClaimsTest {
    @Test
    void restoresIconThatWasHiddenBeforeQuest() {
        Map<String, Boolean> visible = visibility("oak", false);
        QuestTreeIconClaims claims = new QuestTreeIconClaims();

        claims.reconcile(requirements(1, "oak"), adapter(visible));
        assertTrue(visible.get("oak"));

        claims.reconcile(Collections.emptyMap(), adapter(visible));
        assertFalse(visible.get("oak"));
    }

    @Test
    void keepsIconThatWasVisibleBeforeQuest() {
        Map<String, Boolean> visible = visibility("oak", true);
        QuestTreeIconClaims claims = new QuestTreeIconClaims();

        claims.reconcile(requirements(1, "oak"), adapter(visible));
        claims.reconcile(Collections.emptyMap(), adapter(visible));

        assertTrue(visible.get("oak"));
    }

    @Test
    void sharedIconStaysVisibleUntilLastQuestIsRemoved() {
        Map<String, Boolean> visible = visibility("oak", false);
        QuestTreeIconClaims claims = new QuestTreeIconClaims();
        Map<Integer, Set<String>> both = requirements(1, "oak");
        both.put(2, Collections.singleton("oak"));

        claims.reconcile(both, adapter(visible));
        claims.reconcile(requirements(2, "oak"), adapter(visible));
        assertTrue(visible.get("oak"));

        claims.reconcile(Collections.emptyMap(), adapter(visible));
        assertFalse(visible.get("oak"));
    }

    @Test
    void retriesClaimWhenIconSettingLoadsLater() {
        Map<String, Boolean> visible = new HashMap<>();
        Map<String, Boolean> loaded = visibility("oak", false);
        QuestTreeIconClaims claims = new QuestTreeIconClaims();
        QuestTreeIconClaims.Visibility adapter = new QuestTreeIconClaims.Visibility() {
            @Override
            public boolean isVisible(String resource) {
                return Boolean.TRUE.equals(visible.get(resource));
            }

            @Override
            public void setVisible(String resource, boolean value) {
                if(Boolean.TRUE.equals(loaded.get(resource)))
                    visible.put(resource, value);
            }
        };

        claims.reconcile(requirements(1, "oak"), adapter);
        assertFalse(visible.containsKey("oak"));

        loaded.put("oak", true);
        claims.reconcile(requirements(1, "oak"), adapter);
        assertTrue(visible.get("oak"));
    }

    private static Map<String, Boolean> visibility(String resource, boolean value) {
        Map<String, Boolean> out = new HashMap<>();
        out.put(resource, value);
        return out;
    }

    private static Map<Integer, Set<String>> requirements(int questId, String resource) {
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        out.put(questId, Collections.singleton(resource));
        return out;
    }

    private static QuestTreeIconClaims.Visibility adapter(Map<String, Boolean> visible) {
        return new QuestTreeIconClaims.Visibility() {
            @Override
            public boolean isVisible(String resource) {
                return Boolean.TRUE.equals(visible.get(resource));
            }

            @Override
            public void setVisible(String resource, boolean value) {
                visible.put(resource, value);
            }
        };
    }
}
