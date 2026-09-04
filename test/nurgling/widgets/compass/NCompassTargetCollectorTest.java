package nurgling.widgets.compass;

import haven.Coord2d;
import haven.Widget;
import haven.res.ui.locptr.Pointer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCompassTargetCollectorTest {
    @Test
    void findsEachPointerOnlyOnceAcrossOverlappingRoots() {
        Widget root = new Widget();
        Widget nested = root.add(new Widget());
        Pointer pointer = nested.add(new Pointer(null));

        assertEquals(Collections.singletonList(pointer),
                NCompassTargetCollector.findPointers(root, nested));
    }

    @Test
    void partyNamePrefersCacheThenBuddyThenFallback() {
        assertEquals("Cached", NCompassTargetCollector.choosePartyName("Cached", "Buddy", "Party member"));
        assertEquals("Buddy", NCompassTargetCollector.choosePartyName(null, "Buddy", "Party member"));
        assertEquals("Party member", NCompassTargetCollector.choosePartyName("", "", "Party member"));
    }

    @Test
    void questPointerUsesCurrentGobPositionInsteadOfStalePointerCoordinates() {
        Coord2d stalePointerPosition = new Coord2d(100, 200);
        Coord2d currentGobPosition = new Coord2d(-300, -400);

        assertEquals(currentGobPosition,
                NCompassTargetCollector.choosePointerPosition(stalePointerPosition, currentGobPosition));
    }

    @Test
    void higherPrioritySourceReplacesDuplicateTarget() {
        NCompassTarget peer = target("peer", "player:alice", NCompassTarget.Kind.DATABASE, 10, -1);
        NCompassTarget nearby = target("nearby", "player:alice", NCompassTarget.Kind.PLAYER, 9, 42);
        NCompassTarget combat = target("combat", "player:alice", NCompassTarget.Kind.COMBAT, 8, 42);

        List<NCompassTarget> merged = NCompassTargetCollector.mergeTargets(
                Arrays.asList(peer, nearby, combat));

        assertEquals(1, merged.size());
        assertEquals(NCompassTarget.Kind.COMBAT, merged.get(0).kind);
        assertEquals(42, merged.get(0).gobId);
    }

    @Test
    void peerSegmentTilesConvertToSessionWorldCoordinates() {
        assertEquals(new Coord2d(115.5, 225.5), NCompassTargetCollector.peerWorldPosition(
                new haven.Coord(30, 40), new haven.Coord(20, 20)));
    }

    @Test
    void sourceIdentityUsesKnownPlayerNameThenGobThenFallback() {
        assertEquals("player:alice",
                NCompassTargetCollector.identityKey(true, "Alice", 42, "quest:1"));
        assertEquals("gob:42",
                NCompassTargetCollector.identityKey(true, null, 42, "quest:1"));
        assertEquals("quest:1",
                NCompassTargetCollector.identityKey(false, null, -1, "quest:1"));
    }

    @Test
    void playerBodyResourceIsRecognizedWithoutKinData() {
        assertTrue(NCompassTargetCollector.isPlayerResource("gfx/borka/body"));
        assertFalse(NCompassTargetCollector.isPlayerResource("gfx/kritter/bear/bear"));
    }

    @Test
    void kritterResourcesCountAsAnimalsAndPlayerBodiesDoNot() {
        assertTrue(NCompassTargetCollector.isAnimalResource("gfx/kritter/bear/bear"));
        assertTrue(NCompassTargetCollector.isAnimalResource("gfx/invobjs/kritter/bear"));
        assertFalse(NCompassTargetCollector.isAnimalResource("gfx/borka/body"));
        assertFalse(NCompassTargetCollector.isAnimalResource("gfx/terobjs/trees/oak"));
        assertFalse(NCompassTargetCollector.isAnimalResource(null));
    }

    @Test
    void shownLiveAnimalsAreIncludedAndCorpsesOrHiddenIconsAreNot() {
        assertTrue(NCompassTargetCollector.includeShownAnimal(
                true, "idle", "gfx/kritter/bear/bear", "gfx/invobjs/kritter/bear"));
        assertTrue(NCompassTargetCollector.includeShownAnimal(
                true, null, "gfx/kritter/fox/fox", null));
        assertFalse(NCompassTargetCollector.includeShownAnimal(
                false, "idle", "gfx/kritter/bear/bear", "gfx/invobjs/kritter/bear"));
        assertFalse(NCompassTargetCollector.includeShownAnimal(
                true, "knock", "gfx/kritter/bear/bear", "gfx/invobjs/kritter/bear"));
        assertFalse(NCompassTargetCollector.includeShownAnimal(
                true, "gfx/kritter/wolf/dead", "gfx/kritter/wolf/wolf", "gfx/invobjs/kritter/wolf"));
        assertFalse(NCompassTargetCollector.includeShownAnimal(
                true, "idle", "gfx/borka/body", "gfx/hud/mmap/plo"));
        assertFalse(NCompassTargetCollector.includeShownAnimal(
                true, "idle", "gfx/terobjs/trees/oak", null));
    }

    @Test
    void combatQuestAndPartyBeatAnimalMarksOnTheSameGob() {
        NCompassTarget animal = target("animal", "gob:7", NCompassTarget.Kind.ANIMAL, 4, 7);
        NCompassTarget combat = target("combat", "gob:7", NCompassTarget.Kind.COMBAT, 4, 7);
        NCompassTarget quest = target("quest", "gob:8", NCompassTarget.Kind.QUEST, 5, 8);
        NCompassTarget animalQuest = target("animal-quest", "gob:8", NCompassTarget.Kind.ANIMAL, 5, 8);
        NCompassTarget party = target("party", "gob:9", NCompassTarget.Kind.PARTY, 6, 9);
        NCompassTarget animalParty = target("animal-party", "gob:9", NCompassTarget.Kind.ANIMAL, 6, 9);
        NCompassTarget player = target("player", "gob:10", NCompassTarget.Kind.PLAYER, 7, 10);
        NCompassTarget animalPlayer = target("animal-player", "gob:10", NCompassTarget.Kind.ANIMAL, 7, 10);

        List<NCompassTarget> merged = NCompassTargetCollector.mergeTargets(
                Arrays.asList(animal, combat, animalQuest, quest, animalParty, party,
                        animalPlayer, player));

        assertEquals(4, merged.size());
        assertEquals(NCompassTarget.Kind.COMBAT, merged.get(0).kind);
        assertEquals(NCompassTarget.Kind.QUEST, merged.get(1).kind);
        assertEquals(NCompassTarget.Kind.PARTY, merged.get(2).kind);
        assertEquals(NCompassTarget.Kind.PLAYER, merged.get(3).kind);
    }

    private static NCompassTarget target(String id, String mergeKey, NCompassTarget.Kind kind,
                                         double distance, long gobId) {
        return new NCompassTarget(id, mergeKey, kind, new Coord2d(distance, 0), id,
                distance, null, Color.WHITE, gobId);
    }
}
