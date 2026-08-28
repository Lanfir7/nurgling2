package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestObjectiveActionResolverTest {
    private final QuestObjectiveActionResolver resolver = new QuestObjectiveActionResolver();

    @Test
    void forageObjectiveReturnsCanonicalBiomes() {
        QuestObjectiveAction action = resolver.resolve(
                new QCond(1, false, "Pick a Chiming Bluebell", null));

        assertEquals(QuestObjectiveAction.Kind.FORAGE_TERRAIN, action.kind);
        assertFalse(action.targets.isEmpty());
    }

    @Test
    void rockObjectiveReturnsExactTile() {
        QuestObjectiveAction action = resolver.resolve(
                new QCond(1, false, "Bring a Quartz to Jenny", null));

        assertEquals(QuestObjectiveAction.Kind.ROCK_TERRAIN, action.kind);
        assertEquals(Collections.singletonList("gfx/tiles/rocks/quartz"), action.targets);
    }

    @Test
    void createObjectiveRequestsCraftLookup() {
        QuestObjectiveAction action = resolver.resolve(
                new QCond(1, false, "Create a Stone Axe", null));

        assertEquals(QuestObjectiveAction.Kind.CRAFT, action.kind);
        assertEquals(Collections.singletonList("stone axe"), action.targets);
    }

    @Test
    void readyAndUnknownObjectivesHaveNoButtonAction() {
        assertNull(resolver.resolve(new QCond(1, true, "Pick a Chiming Bluebell", null)));
        assertNull(resolver.resolve(new QCond(1, false, "Admire the sunset", null)));
    }

    @Test
    void readyTreeObjectiveStillRetainsTreeResource() {
        assertTrue(resolver.treeResources(
                new QCond(1, true, "Bring a Board of Oak to Jenny", null))
                .contains("gfx/terobjs/trees/oak"));
    }
}
