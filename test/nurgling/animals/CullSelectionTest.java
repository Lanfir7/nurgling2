package nurgling.animals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CullSelectionTest {
    static final class Cow {
        final String name;
        final int rank;
        final boolean adult;
        Cow(String name, int rank, boolean adult) {
            this.name = name;
            this.rank = rank;
            this.adult = adult;
        }
        int rank() { return rank; }
        boolean adult() { return adult; }
    }

    private static final Comparator<Cow> BEST_FIRST = Comparator.comparingInt(Cow::rank).reversed();

    @Test void preservesTheExistingAdultBarSelectionIncludingTiesAndYoung() {
        Cow best = new Cow("best", 90, true);
        Cow youngAbove = new Cow("young above", 85, false);
        Cow bar = new Cow("bar", 80, true);
        Cow youngBelow = new Cow("young below", 70, false);
        Cow tied = new Cow("tied", 80, true);
        List<Cow> input = new ArrayList<>(List.of(youngBelow, bar, tied, best, youngAbove));
        CullSelection<Cow> result = CullSelection.select(input, BEST_FIRST, Cow::adult, 2);
        assertEquals(List.of(best, bar), result.keep);
        assertEquals(bar, result.bar);
        assertEquals(List.of(tied, youngBelow), result.kill);
        assertEquals(List.of(youngBelow, bar, tied, best, youngAbove), input);
    }

    @Test void noAdultKeepsAllAndNullAdultFilterKillsAll() {
        List<Cow> young = List.of(new Cow("a", 5, false), new Cow("b", 2, false));
        assertTrue(CullSelection.select(young, BEST_FIRST, Cow::adult, 1).kill.isEmpty());
        assertEquals(young, CullSelection.select(young, BEST_FIRST, null, 0).kill);
    }

    @Test void zeroOrNegativeKeepNeverSelectsAnAdult() {
        List<Cow> herd = List.of(new Cow("adult", 10, true), new Cow("young", 1, false));
        assertTrue(CullSelection.select(herd, BEST_FIRST, Cow::adult, 0).kill.isEmpty());
        assertTrue(CullSelection.select(herd, BEST_FIRST, Cow::adult, -2).kill.isEmpty());
    }
}
