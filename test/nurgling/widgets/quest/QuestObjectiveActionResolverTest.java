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
    void unfinishedTreeObjectiveReturnsTreeResource() {
        assertTrue(resolver.treeResources(
                new QCond(1, false, "Bring a Board of Oak to Jenny", null))
                .contains("gfx/terobjs/trees/oak"));
    }

    @Test
    void readyTreeObjectiveHasNoTreeResource() {
        assertTrue(resolver.treeResources(
                new QCond(1, true, "Bring a Board of Oak to Jenny", null))
                .isEmpty());
    }

    @Test
    void fellAlmondTreeHighlightsKnownBiomes() {
        QuestObjectiveAction action = resolver.resolve(
                new QCond(1, false, "Fell an almond tree (x2) 2/6[3/5]", null));

        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, action.kind);
        assertTrue(action.targets.contains("Deep Tangle"));
        assertTrue(action.targets.contains("Blue Sod"));
    }

    @Test
    void exactCedarFellObjectiveRemainsAvailableToTheActionButton() {
        QCond cedar = new QCond(1, false, "Fell a cedar (x6)", "2/6[5/5]");

        QuestObjectiveAction action = QuestObjectiveActions.available(null, cedar);

        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, action.kind);
        assertTrue(action.targets.contains("Dry Flat"));
        assertTrue(action.targets.contains("Shady Copse"));
    }

    @Test
    void alderAndOliveObjectivesResolveToLivingTreeTerrainsWithoutChangingWhitebeam() {
        QuestObjectiveAction alder = resolver.resolve(
                new QCond(1, false, "Fell an alder (x6) 4/6[4/5]", null));
        QuestObjectiveAction olive = resolver.resolve(
                new QCond(1, false, "Eat an olive (x2) 4/6[4/5]", null));
        QuestObjectiveAction whitebeam = resolver.resolve(
                new QCond(1, false, "Bring a block of whitebeam to Jenny", null));

        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, alder.kind);
        assertTrue(alder.targets.contains("Beech Grove"));
        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, olive.kind);
        assertTrue(olive.targets.contains("Dry Flat"));
        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, whitebeam.kind);
        assertTrue(whitebeam.targets.contains("Black Wood"));
    }

    @Test
    void pickAlmondsResolvesTreeTerrainsFromProduct() {
        QuestObjectiveAction action = resolver.resolve(
                new QCond(1, false, "Pick Almonds", null));

        assertEquals(QuestObjectiveAction.Kind.TREE_TERRAIN, action.kind);
        assertTrue(action.targets.contains("Deep Tangle"));
        assertEquals("gfx/terobjs/trees/almondtree",
                resolver.treeResources(new QCond(1, false, "Pick Almonds", null))
                        .iterator().next());
    }

    @Test
    void forageAndRockButtonsStillWinOverTreeProducts() {
        assertEquals(QuestObjectiveAction.Kind.FORAGE_TERRAIN, resolver.resolve(
                new QCond(1, false, "Pick a Chiming Bluebell", null)).kind);
        assertEquals(QuestObjectiveAction.Kind.ROCK_TERRAIN, resolver.resolve(
                new QCond(1, false, "Bring a Quartz to Jenny", null)).kind);
    }

    @Test
    void unknownAndReadyTreeObjectivesHaveNoTerrainButton() {
        assertNull(resolver.resolve(new QCond(1, false, "Fell a mallorn tree", null)));
        assertNull(resolver.resolve(new QCond(1, false, "Fell an unknown tree", null)));
        assertNull(resolver.resolve(new QCond(1, true, "Fell an almond tree", null)));
        assertNull(resolver.resolve(new QCond(1, false, "Admire the sunset", null)));
    }

    @Test
    void fellAlmondTreeAlsoExposesLivingTreeResource() {
        assertTrue(resolver.treeResources(
                new QCond(1, false, "Fell an almond tree", null))
                .contains("gfx/terobjs/trees/almondtree"));
    }
}
