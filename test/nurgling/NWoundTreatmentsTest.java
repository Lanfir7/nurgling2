package nurgling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NWoundTreatmentsTest {
    @Test
    void coldCutTreatsTheWoundsOnItsTooltip() {
        assertTreats("paginae/wound/deepcut", "gfx/invobjs/coldcut");
        assertTreats("paginae/wound/blackeye", "gfx/invobjs/coldcut");
        assertTreats("paginae/wound/swollenbump", "gfx/invobjs/coldcut");
        assertTreats("paginae/wound/beesting", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/crabcaressed", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/infectedsore", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/sealfinger", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/soresnout", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/soresnout", "gfx/invobjs/mudointment");
        assertTreats("paginae/wound/scorpstung", "gfx/invobjs/antpaste");
        assertTreats("paginae/wound/scorpstung", "gfx/invobjs/jar-snakejuice");
    }

    private static void assertTreats(String wound, String item) {
        List<NWoundTreatments.Treatment> treats = NWoundTreatments.forWound(wound);
        assertTrue(treats.stream().anyMatch(t -> item.equals(t.res)), wound);
    }
}
