package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectTrackerMammothSkullTest {
    @Test
    void mammothSkullIsLeftoverNotAnAnimalMarker() {
        assertTrue(ObjectTracker.isLeftoverKritter("gfx/kritter/mammothskull"));
        assertFalse(ObjectTracker.isAnimalMarkerGob("gfx/kritter/mammothskull"));
    }

    @Test
    void livingMammothStaysAnAnimalMarker() {
        assertFalse(ObjectTracker.isLeftoverKritter("gfx/kritter/mammoth/mammoth"));
        assertTrue(ObjectTracker.isAnimalMarkerGob("gfx/kritter/mammoth/mammoth"));
    }

    @Test
    void orcaBeefIsLeftoverLikeSkulls() {
        assertTrue(ObjectTracker.isLeftoverKritter("gfx/kritter/orca/orcabeef"));
        assertFalse(ObjectTracker.isAnimalMarkerGob("gfx/kritter/orca/orcabeef"));
        assertTrue(ObjectTracker.isAnimalMarkerGob("gfx/kritter/orca/orca"));
    }

    @Test
    void mammothPatternDoesNotMatchSkull() {
        assertTrue(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/mammoth/mammoth", "gfx/kritter/mammoth/mammoth"));
        assertTrue(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/mammoth/mammoth", "mammoth"));
        assertFalse(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/mammothskull", "gfx/kritter/mammoth/mammoth"));
        assertFalse(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/mammothskull", "gfx/kritter/mammoth"));
        assertFalse(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/mammothskull", "mammoth"));
    }

    @Test
    void orcaPatternDoesNotMatchOrcaBeef() {
        assertTrue(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/orca/orca", "gfx/kritter/orca/orca"));
        assertFalse(ObjectTracker.matchesTrackedPattern(
                "gfx/kritter/orca/orcabeef", "gfx/kritter/orca/orca"));
    }
}
