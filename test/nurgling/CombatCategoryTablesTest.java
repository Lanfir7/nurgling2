package nurgling;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatCategoryTablesTest {
    private static Set<String> resources(String names) {
        Set<String> result = new HashSet<>();
        for (String name : names.split(" ")) result.add("paginae/atk/" + name);
        return result;
    }

    @Test void currentCategoriesKeepEveryPreviouslyClassifiedMove() {
        assertEquals(resources("pow lefthook lowblow oppknock ripapart fullcircle cleave barrage sideswipe sting sos knockteeth kick haymaker chop gojug uppercut punchboth stealthunder ravenbite takedown flex"),
                CombatSchoolUi.Category.ATTACKS.names);
        assertEquals(resources("regain dash zigzag yieldground watchmoves sidestep qdodge jump fdodge artevade flex"),
                CombatSchoolUi.Category.DEFENCES.names);
        assertEquals(resources("toarms shield parry oakstance dorg chinup bloodlust combmed"),
                CombatSchoolUi.Category.MANEUVERS.names);
        assertEquals(resources("think takeaim dash oppknock"), CombatSchoolUi.Category.MOVES.names);
    }

    @Test void newDefinitionsAreClassifiedWithoutAnotherHardcodedMoveList() {
        Map<CombatSchoolUi.Category, Set<String>> result = CombatSchoolUi.classify(
                List.of("newattack"), List.of("paginae/atk/newstance"),
                List.of("paginae/atk/newattack", "paginae/atk/newdefence"),
                List.of("paginae/atk/fixeddefence", "paginae/atk/newstance"),
                List.of("paginae/atk/newmove"));
        assertTrue(result.get(CombatSchoolUi.Category.ATTACKS).contains("paginae/atk/newattack"));
        assertTrue(result.get(CombatSchoolUi.Category.MANEUVERS).contains("paginae/atk/newstance"));
        assertTrue(result.get(CombatSchoolUi.Category.MOVES).contains("paginae/atk/newmove"));
        assertTrue(result.get(CombatSchoolUi.Category.DEFENCES).containsAll(
                List.of("paginae/atk/newdefence", "paginae/atk/fixeddefence")));
        assertFalse(result.get(CombatSchoolUi.Category.DEFENCES).contains("paginae/atk/newattack"));
        assertFalse(result.get(CombatSchoolUi.Category.DEFENCES).contains("paginae/atk/newstance"));
        assertFalse(result.get(CombatSchoolUi.Category.DEFENCES).contains("paginae/atk/newmove"));
        assertTrue(result.get(CombatSchoolUi.Category.ALL).isEmpty());
        assertTrue(result.get(CombatSchoolUi.Category.OTHER).isEmpty());
    }

    @Test void specialOverlapsAndUnknownResourcesStayAvailable() {
        assertTrue(CombatSchoolUi.Category.ATTACKS.matches("paginae/atk/oppknock"));
        assertTrue(CombatSchoolUi.Category.MOVES.matches("paginae/atk/oppknock"));
        assertTrue(CombatSchoolUi.Category.ATTACKS.matches("paginae/atk/flex"));
        assertTrue(CombatSchoolUi.Category.DEFENCES.matches("paginae/atk/flex"));
        assertTrue(CombatSchoolUi.Category.DEFENCES.matches("paginae/atk/dash"));
        assertTrue(CombatSchoolUi.Category.MOVES.matches("paginae/atk/dash"));
        assertTrue(CombatSchoolUi.Category.OTHER.matches("paginae/atk/unclassified"));
        assertTrue(CombatSchoolUi.Category.ALL.matches("paginae/atk/unclassified"));
        for (CombatSchoolUi.Category category : CombatSchoolUi.Category.values())
            assertEquals(category == CombatSchoolUi.Category.ALL, category.matches(null));
    }
}
