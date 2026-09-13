package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMakewindowQModTest {

    private static NMakewindowQMod.NamedComp attr(String nm, int comp) {
        return new NMakewindowQMod.NamedComp(nm, comp);
    }

    @Test
    void nullListsReturnEmptyMatch() {
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(null, null, "str");
        assertTrue(match.comps.isEmpty());
        assertEquals(0, match.count);
        assertEquals(1.0, match.product, 0.0);
    }

    @Test
    void emptyListsReturnEmptyMatch() {
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(
                Collections.<NMakewindowQMod.NamedComp>emptyList(),
                Collections.<NMakewindowQMod.NamedComp>emptyList(),
                "str");
        assertTrue(match.comps.isEmpty());
        assertEquals(0, match.count);
        assertEquals(1.0, match.product, 0.0);
    }

    @Test
    void nullBasenameSkipsLookups() {
        List<NMakewindowQMod.NamedComp> battrs = Collections.singletonList(attr("str", 40));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(battrs, null, null);
        assertTrue(match.comps.isEmpty());
        assertEquals(0, match.count);
        assertEquals(1.0, match.product, 0.0);
    }

    @Test
    void matchingBattrReturnsCompWhenSattrIsNull() {
        List<NMakewindowQMod.NamedComp> battrs = Arrays.asList(attr("agi", 10), attr("str", 40));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(battrs, null, "str");
        assertEquals(Collections.singletonList(Integer.valueOf(40)), match.comps);
        assertEquals(1, match.count);
        assertEquals(40.0, match.product, 0.0);
    }

    @Test
    void matchingSattrReturnsCompWhenBattrIsNull() {
        List<NMakewindowQMod.NamedComp> sattrs = Collections.singletonList(attr("smithing", 80));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(null, sattrs, "smithing");
        assertEquals(Collections.singletonList(Integer.valueOf(80)), match.comps);
        assertEquals(1, match.count);
        assertEquals(80.0, match.product, 0.0);
    }

    @Test
    void unmatchedNameLeavesProductAtOne() {
        List<NMakewindowQMod.NamedComp> battrs = Collections.singletonList(attr("str", 40));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(battrs, null, "agi");
        assertTrue(match.comps.isEmpty());
        assertEquals(0, match.count);
        assertEquals(1.0, match.product, 0.0);
    }

    @Test
    void firstMatchPerListWinsThenBothListsContribute() {
        List<NMakewindowQMod.NamedComp> battrs = Arrays.asList(attr("str", 40), attr("str", 99));
        List<NMakewindowQMod.NamedComp> sattrs = Arrays.asList(attr("str", 80), attr("str", 1));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(battrs, sattrs, "str");
        assertEquals(Arrays.asList(Integer.valueOf(40), Integer.valueOf(80)), match.comps);
        assertEquals(2, match.count);
        assertEquals(3200.0, match.product, 0.0);
    }

    @Test
    void nullEntriesAreSkipped() {
        List<NMakewindowQMod.NamedComp> battrs = Arrays.asList(null, attr(null, 3), attr("str", 40));
        NMakewindowQMod.Match match = NMakewindowQMod.lookup(battrs, null, "str");
        assertEquals(Collections.singletonList(Integer.valueOf(40)), match.comps);
        assertEquals(1, match.count);
        assertEquals(40.0, match.product, 0.0);
    }
}
