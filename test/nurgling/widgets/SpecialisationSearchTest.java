package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationSearchTest {
    @Test
    void blankQueryKeepsEverySpecialisationVisible() {
        assertTrue(SpecialisationSearch.matches("   ", "waterForTrees", "Water for Trees"));
    }

    @Test
    void matchesDisplayNameIgnoringCase() {
        assertTrue(SpecialisationSearch.matches("WATER FOR", "waterForTrees", "Water for Trees"));
    }

    @Test
    void matchesInternalIdIgnoringCase() {
        assertTrue(SpecialisationSearch.matches("waterfor", "waterForTrees", "Water for Trees"));
    }

    @Test
    void rejectsUnrelatedSpecialisation() {
        assertFalse(SpecialisationSearch.matches("smelter", "waterForTrees", "Water for Trees"));
    }
}
