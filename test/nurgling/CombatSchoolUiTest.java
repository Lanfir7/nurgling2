package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatSchoolUiTest {
    @Test
    void hurricaneCategoriesMatchKnownCombatActions() {
        assertTrue(CombatSchoolUi.Category.ATTACKS.matches("paginae/atk/cleave"));
        assertTrue(CombatSchoolUi.Category.DEFENCES.matches("paginae/atk/artevade"));
        assertTrue(CombatSchoolUi.Category.MANEUVERS.matches("paginae/atk/oakstance"));
        assertTrue(CombatSchoolUi.Category.MOVES.matches("paginae/atk/takeaim"));
        assertTrue(CombatSchoolUi.Category.OTHER.matches("paginae/atk/futuremove"));
    }

    @Test
    void overlappingHurricaneCategoriesRemainAvailableInBothTabs() {
        assertTrue(CombatSchoolUi.Category.ATTACKS.matches("paginae/atk/flex"));
        assertTrue(CombatSchoolUi.Category.DEFENCES.matches("paginae/atk/flex"));
        assertTrue(CombatSchoolUi.Category.DEFENCES.matches("paginae/atk/dash"));
        assertTrue(CombatSchoolUi.Category.MOVES.matches("paginae/atk/dash"));
    }

    @Test
    void allAndOtherHaveComplementaryFallbackBehavior() {
        assertTrue(CombatSchoolUi.Category.ALL.matches("paginae/atk/cleave"));
        assertTrue(CombatSchoolUi.Category.ALL.matches("paginae/atk/futuremove"));
        assertFalse(CombatSchoolUi.Category.OTHER.matches("paginae/atk/cleave"));
    }

    @Test
    void plusGlowsOnlyWhenAnotherLevelIsAvailable() {
        assertTrue(CombatSchoolUi.canIncrease(1, 3));
        assertFalse(CombatSchoolUi.canIncrease(3, 3));
        assertFalse(CombatSchoolUi.canIncrease(0, 0));
    }

    @Test
    void usedPointTextAlwaysContainsBothNumbers() {
        assertEquals("Использовано: 7/30",
                CombatSchoolUi.usedText("Использовано: %d/%d", 7, 30));
    }
}
